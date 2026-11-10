package com.agri_system.infrastructure.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.spring.FlexTransactionManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** 仅验证原UUID优先的真实SQL行为；H2不能替代待执行的最终MySQL/权限回归。 */
class CustomerIdentityEnrollmentSqlTest {
    JdbcTemplate jdbc;TransactionTemplate tx;CustomerIdentityEnrollmentRepositoryImpl repository;
    @BeforeEach void setup(){
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:enrollment_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var flex=new FlexDataSource("enrollment_sql",source,false);jdbc=new JdbcTemplate(flex);tx=new TransactionTemplate(new FlexTransactionManager());
        jdbc.execute("CREATE TABLE customer_identity_enrollment(id BIGINT AUTO_INCREMENT PRIMARY KEY,request_key VARCHAR(36) UNIQUE,fingerprint VARCHAR(64),active_subject VARCHAR(64) UNIQUE,app_id VARCHAR(32),open_id VARCHAR(200),phone VARCHAR(20),display_name VARCHAR(100),verified_at TIMESTAMP,status VARCHAR(24),review_reason VARCHAR(32),customer_id BIGINT,identity_id BIGINT,identity_auth_version BIGINT,decision_request_key VARCHAR(36) UNIQUE,decision_fingerprint VARCHAR(64),reviewed_by BIGINT,verification_method VARCHAR(32),decision_reason VARCHAR(500),reviewed_at TIMESTAMP,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        repository=new CustomerIdentityEnrollmentRepositoryImpl(jdbc);
    }
    @AfterEach void close(){jdbc.execute("SHUTDOWN");}
    CustomerIdentityEnrollment input(String key){
        var row=new CustomerIdentityEnrollment();row.setRequestKey(key);row.setFingerprint("f".repeat(64));row.setActiveSubject("a".repeat(64));row.setAppId("wx1111111111111111");row.setOpenId("IT_SQL_OPEN");row.setPhone("13800000000");row.setDisplayName("SQL验收");row.setVerifiedAt(java.time.LocalDateTime.now());return row;
    }
    void registerRejectAndReapply(String original){
        tx.execute(status->{var row=repository.acquire(input(original));row.setStatus("REVIEW_REQUIRED");repository.finishRegistration(row);return null;});
        tx.execute(status->{var row=repository.findByRequestKey(original,true).orElseThrow();row.setDecisionRequestKey(UUID.randomUUID().toString());row.setDecisionFingerprint("d".repeat(64));row.setReviewedBy(1L);row.setVerificationMethod("UNCONFIRMED");row.setDecisionReason("不能确认");repository.claimDecision(row);row.setStatus("REJECTED");row.setActiveSubject(null);repository.finishDecision(row);return null;});
        tx.execute(status->{var row=repository.acquire(input(UUID.randomUUID().toString()));row.setStatus("REVIEW_REQUIRED");repository.finishRegistration(row);return null;});
    }
    @Test void originalRejectedRequestWinsOverLaterActiveSubject(){
        String key=UUID.randomUUID().toString();registerRejectAndReapply(key);
        var result=tx.execute(status->repository.acquire(input(key)));
        assertEquals(key,result.getRequestKey());assertEquals("REJECTED",result.getStatus());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM customer_identity_enrollment",Integer.class));
    }
    @Test void originalRequestThatAppearsAfterInitialReadStillWins()throws Exception {
        String key=UUID.randomUUID().toString();var initial=new AtomicBoolean(true);var executor=Executors.newSingleThreadExecutor();
        var raced=new CustomerIdentityEnrollmentRepositoryImpl(jdbc){
            @Override public Optional<CustomerIdentityEnrollment> findByRequestKey(String requested,boolean lock){
                var result=super.findByRequestKey(requested,lock);
                if(requested.equals(key)&&lock&&initial.getAndSet(false)){
                    assertTrue(result.isEmpty());
                    try{executor.submit(()->registerRejectAndReapply(key)).get(10,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException(failure);}
                }
                return result;
            }
        };
        try{var result=tx.execute(status->raced.acquire(input(key)));assertEquals(key,result.getRequestKey());assertEquals("REJECTED",result.getStatus());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM customer_identity_enrollment",Integer.class));}
        finally{executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }
}
