package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow;
import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.model.AgronomyCaseEntry;
import com.agri_system.domain.customer.model.CustomerAuthState;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.infrastructure.agronomy.repository.AgronomyCaseRepositoryImpl;
import com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl;
import com.agri_system.infrastructure.store.repository.StorePublicationRepositoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgronomyAssistantCoordinatorTest {
    JdbcTemplate jdbc; TransactionTemplate tx; DataSourceTransactionManager manager;
    AgronomyCaseRepositoryImpl cases; AgronomyAssistantRepositoryImpl requests;
    StorePublicationRepositoryImpl stores; CustomerRepository customers;
    final Clock clock=Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"),ZoneOffset.UTC);
    AssistantBudgetPolicy policy=policy(true,2,2,100000);
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:assistant_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE store(id BIGINT PRIMARY KEY,name VARCHAR(100),phone VARCHAR(30),address VARCHAR(200),status INT,deleted INT,client_visible INT,client_publication_version BIGINT)");
        jdbc.update("INSERT INTO store VALUES(1,'虚构软件门店',NULL,NULL,1,0,1,1)");
        jdbc.execute("CREATE TABLE customer(id BIGINT PRIMARY KEY,status INT,deleted INT,auth_version BIGINT)");
        jdbc.update("INSERT INTO customer VALUES(101,1,0,1),(202,1,0,1)");
        jdbc.execute("CREATE TABLE agronomy_case(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,store_id BIGINT,request_key VARCHAR(36),request_hash VARCHAR(64),question VARCHAR(2000),region VARCHAR(100),crop VARCHAR(50),growth_stage VARCHAR(100),status VARCHAR(24),version BIGINT,facts_version BIGINT,assigned_staff_id BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(customer_id,request_key))");
        jdbc.execute("CREATE TABLE agronomy_case_entry(id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT,actor_type VARCHAR(16),actor_id BIGINT,request_key VARCHAR(36),request_hash VARCHAR(64),type VARCHAR(24),body TEXT,source_text TEXT,region VARCHAR(100),crop VARCHAR(50),growth_stage VARCHAR(100),assigned_staff_id BIGINT,expected_version BIGINT,result_version BIGINT,result_facts_version BIGINT,result_status VARCHAR(24),created_at TIMESTAMP,assistant_state VARCHAR(16),assistant_snapshot TEXT,assistant_outcome TEXT,assistant_budget_day DATE,assistant_model_reserved_microusd BIGINT,assistant_search_reserved_microusd BIGINT,assistant_claim_key VARCHAR(36),assistant_updated_at TIMESTAMP,UNIQUE(actor_type,actor_id,request_key))");
        cases=new AgronomyCaseRepositoryImpl(jdbc);requests=new AgronomyAssistantRepositoryImpl(jdbc);stores=new StorePublicationRepositoryImpl(jdbc);
        customers=mock(CustomerRepository.class);
        when(customers.findAuthStateById(anyLong())).thenAnswer(a->jdbc.query("SELECT * FROM customer WHERE id=?",(r,n)->new CustomerAuthState(r.getLong("id"),r.getInt("status"),r.getInt("deleted"),r.getLong("auth_version")),a.<Long>getArgument(0)).stream().findFirst());
    }
    static AssistantBudgetPolicy policy(boolean enabled,long calls,long searches,long money) {
        return new AssistantBudgetPolicy(enabled,calls,searches,money,"deepseek-flash",true,1024,300000,1200000,750,"TEST_ONLY_PRICE_V1");
    }
    long create(long customer) {
        var at=LocalDateTime.now(clock);var f=new AgronomyCase.Facts("虚构地区","虚构作物","软件阶段");String key=UUID.randomUUID().toString();
        cases.insertCase(new AgronomyCase(null,customer,1L,key,"0".repeat(64),"仅软件流程验收，不是农技问题",f,"WAITING_REVIEW",1,1,null,at,at));
        var c=cases.findCreation(customer,key).orElseThrow();cases.insertEntry(new AgronomyCaseEntry(null,c.id(),"CUSTOMER",customer,key,"0".repeat(64),"QUESTION",c.question(),"",f,null,0,1,1,c.status(),at));return c.id();
    }
    AgronomyAssistantCoordinator coordinator(AgronomyAssistantWorkflow work) {
        return new AgronomyAssistantCoordinator(cases,requests,stores,customers,manager,new ObjectMapper(),clock,policy,work);
    }
    AgronomyAssistantCoordinator.Command command(String key) {return new AgronomyAssistantCoordinator.Command(key,"1","1",true);}
    @Test void originalRequestCommitsReservationBeforeOneOutsideTransactionWorkflowAndKeepsSnapshot() {
        long id=create(101);var calls=new AtomicInteger();var c=coordinator(snapshot->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("RUNNING",jdbc.queryForObject("SELECT assistant_state FROM agronomy_case_entry WHERE type='ASSISTANT_REQUEST'",String.class));
            calls.incrementAndGet();return "{\"kind\":\"STAGED_TEST_ONLY\"}";
        });String key=UUID.randomUUID().toString();
        var first=c.submit(101L,1L,id,command(key));assertEquals("COMPLETED",first.state());assertEquals(first,c.submit(101L,1L,id,command(key)));assertEquals(1,calls.get());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
        assertEquals(1,cases.entries(id,true,2,null).size());assertEquals(1,cases.entries(id,false,2,null).size());
        assertEquals("2",Long.toString(cases.find(id,false).orElseThrow().version()));
    }

    AgronomyAssistantCoordinator queued(long id,String key) {
        if(!mockingDetails(requests).isSpy())requests=spy(requests);doThrow(new IllegalStateException("模拟派出前进程中断")).when(requests).claim(anyLong(),anyString(),any());
        var c=coordinator(snapshot->{throw new AssertionError("不得执行");});assertThrows(IllegalStateException.class,()->c.submit(101L,1L,id,command(key)));
        doCallRealMethod().when(requests).claim(anyLong(),anyString(),any());return c;
    }
    @Test void defaultDisabledAndInsufficientMoneyLeaveNoRequestOrVersionChange() {
        long id=create(101);policy=AssistantBudgetPolicy.disabled();var c=coordinator(s->{throw new AssertionError();});
        assertThrows(IllegalArgumentException.class,()->c.submit(101L,1L,id,command(UUID.randomUUID().toString())));
        policy=policy(true,2,2,11809);assertEquals(11060,policy.modelReservation());
        assertThrows(IllegalArgumentException.class,()->coordinator(s->"{}").submit(101L,1L,id,command(UUID.randomUUID().toString())));
        assertEquals(1,cases.find(id,false).orElseThrow().version());assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).modelCalls());
    }
    @Test void sameStoreDifferentProblemsShareDailyCountAndReservedMoney() {
        long a=create(101),b=create(202);policy=policy(true,1,1,11810);var c=coordinator(s->"{}");
        c.submit(101L,1L,a,command(UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class,()->c.submit(202L,1L,b,command(UUID.randomUUID().toString())));
        assertEquals(1,cases.find(b,false).orElseThrow().version());assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void sameKeyCannotChangeProblemVersionConsentOrBorrowHumanRequest() {
        long a=create(101),b=create(101);String key=UUID.randomUUID().toString();var c=coordinator(s->"{}");c.submit(101L,1L,a,command(key));
        assertThrows(RuntimeException.class,()->c.submit(101L,1L,b,command(key)));
        assertThrows(RuntimeException.class,()->c.submit(101L,1L,a,new AgronomyAssistantCoordinator.Command(key,"2","1",true)));
        assertThrows(RuntimeException.class,()->c.submit(101L,1L,a,new AgronomyAssistantCoordinator.Command(key,"1","1",false)));
        String original=jdbc.queryForObject("SELECT request_key FROM agronomy_case_entry WHERE case_id=? AND type='QUESTION'",String.class,a);
        assertThrows(RuntimeException.class,()->c.submit(101L,1L,a,command(original)));
        assertThrows(RuntimeException.class,()->c.read(202L,1L,a,key));
    }
    @Test void unknownKeepsMaximumReservationAndNeverRepeatsOrAcceptsAnotherRequest() {
        long id=create(101);String key=UUID.randomUUID().toString();var calls=new AtomicInteger();var c=coordinator(s->{calls.incrementAndGet();throw new IllegalStateException("PRIVATE_QUESTION SECRET_KEY");});
        var result=c.submit(101L,1L,id,command(key));assertEquals("UNKNOWN",result.state());assertFalse(result.toString().contains("SECRET_KEY"));
        assertEquals(result,c.submit(101L,1L,id,command(key)));assertEquals(1,calls.get());
        assertThrows(RuntimeException.class,()->c.submit(101L,1L,id,new AgronomyAssistantCoordinator.Command(UUID.randomUUID().toString(),"2","1",true)));
        assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void restartDoesNotRedispatchRunningRequestAndTimeoutOnlyMarksUnknown() {
        long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator(s->{throw new AssertionError("模拟进程退出");});
        assertThrows(AssertionError.class,()->c.submit(101L,1L,id,command(key)));
        var restart=new AgronomyAssistantCoordinator(cases,requests,stores,customers,manager,new ObjectMapper(),Clock.offset(clock,java.time.Duration.ofSeconds(100)),policy,s->{throw new AssertionError("不得重发");});
        assertEquals("RUNNING",restart.submit(101L,1L,id,command(key)).state());assertEquals("UNKNOWN",restart.markUnknown(101L,1L,id,key).state());
        assertEquals("UNKNOWN",restart.submit(101L,1L,id,command(key)).state());assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void lateResultCannotPublishAfterFactsOrCustomerAuthorizationChanges() {
        for(boolean revoke:new boolean[]{false,true}) {
            long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator(s->{
                if(revoke)jdbc.update("UPDATE customer SET auth_version=auth_version+1 WHERE id=101");
                else tx.execute(status->{var row=cases.find(id,true).orElseThrow();cases.update(id,row.version(),new AgronomyCase.Facts("新地区","新作物","新阶段"),row.status(),2,null,LocalDateTime.now(clock));return null;});
                return "{\"privateDraft\":\"NOT_PUBLISHED\"}";
            });
            var result=c.submit(101L,1L,id,command(key));assertEquals("STALE",result.outcomeKind());
            assertFalse(jdbc.queryForObject("SELECT assistant_outcome FROM agronomy_case_entry WHERE request_key=?",String.class,key).contains("NOT_PUBLISHED"));
        }
    }
    @Test void stoppedStoreBeforeDispatchReleasesOnlyProvablyUnsentReservation() {
        long id=create(101);String key=UUID.randomUUID().toString();queued(id,key);jdbc.update("UPDATE store SET status=0 WHERE id=1");
        var result=coordinator(s->{throw new AssertionError("不得出网");}).submit(101L,1L,id,command(key));
        assertEquals("FAILED",result.state());assertEquals("NOT_DISPATCHED",result.outcomeKind());assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void queuedSnapshotIsFrozenAndNextDayOrChangedExecutionCannotUseOldBudget() {
        long id=create(101);String key=UUID.randomUUID().toString();queued(id,key);
        var next=new AgronomyAssistantCoordinator(cases,requests,stores,customers,manager,new ObjectMapper(),Clock.offset(clock,java.time.Duration.ofDays(1)),policy,s->{throw new AssertionError();});
        assertEquals("FAILED",next.submit(101L,1L,id,command(key)).state());assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
        long other=create(101);String second=UUID.randomUUID().toString();queued(other,second);policy=AssistantBudgetPolicy.disabled();
        assertEquals("FAILED",coordinator(s->{throw new AssertionError();}).submit(101L,1L,other,command(second)).state());
    }
    @Test void completedOriginalSurvivesConfigurationChangeAndSnapshotTamperingFailsClosed() {
        long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator(s->"{}");var first=c.submit(101L,1L,id,command(key));
        policy=AssistantBudgetPolicy.disabled();assertEquals(first,coordinator(s->{throw new AssertionError();}).submit(101L,1L,id,command(key)));
        jdbc.update("UPDATE agronomy_case_entry SET assistant_snapshot=REPLACE(assistant_snapshot,'仅软件流程验收','被改写的原问题') WHERE request_key=?",key);
        assertThrows(RuntimeException.class,()->c.read(101L,1L,id,key));
    }
    @Test void wrongClaimCannotCompleteAnotherExecutionAndMetadataFailureRollsBackEverything() {
        long id=create(101);String key=UUID.randomUUID().toString();coordinator(s->{throw new AssertionError();});
        assertThrows(AssertionError.class,()->coordinator(s->{throw new AssertionError();}).submit(101L,1L,id,command(key)));
        long entry=cases.findRequest("CUSTOMER",101L,key).orElseThrow().id();assertFalse(requests.finish(entry,UUID.randomUUID().toString(),"COMPLETED","{}",LocalDateTime.now(clock)));
        assertEquals("RUNNING",requests.find(entry).orElseThrow().state());
        long other=create(202);requests=spy(requests);doThrow(new IllegalStateException("测试元数据失败")).when(requests).initialize(any());
        String failed=UUID.randomUUID().toString();assertThrows(RuntimeException.class,()->coordinator(s->"{}").submit(202L,1L,other,command(failed)));
        assertTrue(cases.findRequest("CUSTOMER",202L,failed).isEmpty());assertEquals(1,cases.find(other,false).orElseThrow().version());
    }
    @Test void noOuterTransactionAndStaleAuthorizationCanReachTheWorkflow() {
        long id=create(101);var c=coordinator(s->{throw new AssertionError();});
        assertThrows(RuntimeException.class,()->tx.execute(status->c.submit(101L,1L,id,command(UUID.randomUUID().toString()))));
        assertThrows(RuntimeException.class,()->c.submit(101L,2L,id,command(UUID.randomUUID().toString())));
        assertEquals(1,cases.find(id,false).orElseThrow().version());
    }
    @Test void concurrentSameOriginalOnlyGetsOneDispatch() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var calls=new AtomicInteger();var start=new java.util.concurrent.CountDownLatch(1);
        var c=coordinator(s->{calls.incrementAndGet();return "{}";});var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {var all=new java.util.ArrayList<java.util.concurrent.Future<?>>();for(int i=0;i<8;i++)all.add(pool.submit(()->{start.await();return c.submit(101L,1L,id,command(key));}));start.countDown();for(var f:all)f.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(1,calls.get());assertEquals("COMPLETED",c.read(101L,1L,id,key).state());}
        finally{pool.shutdownNow();}
    }

    @Test void midnightBetweenBudgetReadAndInsertCannotMoveReservationIntoUncheckedDay() {
        policy=policy(true,1,1,100000);long a=create(101),b=create(202);
        Clock tomorrow=Clock.fixed(Instant.parse("2026-11-11T16:00:00Z"),ZoneOffset.UTC);
        var first=new AgronomyAssistantCoordinator(cases,requests,stores,customers,manager,new ObjectMapper(),tomorrow,policy,s->"{}");
        first.submit(101L,1L,a,command(UUID.randomUUID().toString()));
        Clock edge=new Clock(){final AtomicInteger reads=new AtomicInteger();public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId zone){return this;}public Instant instant(){return reads.getAndIncrement()==0?Instant.parse("2026-11-11T15:59:59.999Z"):Instant.parse("2026-11-11T16:00:00Z");}};
        var calls=new AtomicInteger();var second=new AgronomyAssistantCoordinator(cases,requests,stores,customers,manager,new ObjectMapper(),edge,policy,s->{calls.incrementAndGet();return "{}";});
        var result=second.submit(202L,1L,b,command(UUID.randomUUID().toString()));
        assertEquals("FAILED",result.state());assertEquals(0,calls.get());assertEquals(1,requests.usage(1L,java.time.LocalDate.of(2026,11,12)).modelCalls());
    }
}
