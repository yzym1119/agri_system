package com.agri_system.application.wallet.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletAuthCodePrivacyTest {
    @Test void 生成码正确保存绑定但日志不含原码() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:wallet_code_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE wallet_auth_code(customer_id BIGINT,order_id BIGINT,auth_code VARCHAR(6),amount DECIMAL(18,2),status INT,expire_at TIMESTAMP,created_at TIMESTAMP)");
        var logger = (Logger) LoggerFactory.getLogger(WalletApplicationService.class);
        var events = new ListAppender<ILoggingEvent>(); events.start(); logger.addAppender(events);
        try {
            var service = new WalletApplicationService(null,null,new com.agri_system.infrastructure.wallet.repository.WalletAuthCodeRepositoryImpl(null,jdbc));
            String code = service.generateAndStoreWalletAuthCode(11L,22L,new BigDecimal("25.50"),5);
            assertTrue(code.matches("[0-9]{6}"));
            var row = jdbc.queryForMap("SELECT * FROM wallet_auth_code");
            assertTrue(code.equals(row.get("AUTH_CODE")));
            assertEquals(11L,row.get("CUSTOMER_ID")); assertEquals(22L,row.get("ORDER_ID"));
            assertEquals(new BigDecimal("25.50"),row.get("AMOUNT")); assertEquals(1,row.get("STATUS"));
            var created = ((java.sql.Timestamp)row.get("CREATED_AT")).toLocalDateTime();
            var expiry = ((java.sql.Timestamp)row.get("EXPIRE_AT")).toLocalDateTime();
            assertEquals(created.plusMinutes(5),expiry);
            assertFalse(events.list.stream().anyMatch(e->e.getFormattedMessage().contains(code)),"生成日志不得输出原授权码");
        } finally { logger.detachAppender(events); events.stop(); jdbc.execute("SHUTDOWN"); }
    }

    @Test void 关联成功与失败日志均不输出原码() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1,0);
        var logger = (Logger) LoggerFactory.getLogger(WalletApplicationService.class);
        var events = new ListAppender<ILoggingEvent>(); events.start(); logger.addAppender(events);
        try {
            var service = new WalletApplicationService(null,null,new com.agri_system.infrastructure.wallet.repository.WalletAuthCodeRepositoryImpl(null,jdbc));
            service.associateAuthCodeWithOrder("735129",22L);
            service.associateAuthCodeWithOrder("735129",22L);
            assertFalse(events.list.stream().anyMatch(e->e.getFormattedMessage().contains("735129")),"关联日志不得输出原授权码");
        } finally { logger.detachAppender(events); events.stop(); }
    }
    @Test void 授权码发放与原Mapper核销及伴随写入同Flex回滚() {
        var source=new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:wallet_tx_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var flex=new com.mybatisflex.core.datasource.FlexDataSource("wallet-boundary",source,false);
        var jdbc=new JdbcTemplate(flex);
        jdbc.execute("CREATE TABLE wallet_auth_code(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,order_id BIGINT,auth_code VARCHAR(6),amount DECIMAL(18,2),status INT,expire_at TIMESTAMP,used_at TIMESTAMP,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE companion(id BIGINT PRIMARY KEY,value_amount DECIMAL(18,2))");
        jdbc.update("INSERT INTO companion VALUES(1,0)");
        var config=new com.mybatisflex.core.mybatis.FlexConfiguration(new org.apache.ibatis.mapping.Environment(
                "wallet-boundary",new org.mybatis.spring.transaction.SpringManagedTransactionFactory(),flex));
        config.addMapper(com.agri_system.infrastructure.wallet.mapper.WalletAuthCodeMapper.class);
        var mapper=new org.mybatis.spring.SqlSessionTemplate(new com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder().build(config))
                .getMapper(com.agri_system.infrastructure.wallet.mapper.WalletAuthCodeMapper.class);
        var repository=new com.agri_system.infrastructure.wallet.repository.WalletAuthCodeRepositoryImpl(mapper,jdbc);
        var service=new WalletApplicationService(null,null,repository);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new com.mybatisflex.spring.FlexTransactionManager());
        try {
            assertThrows(IllegalStateException.class,()->tx.execute(status->{
                String code=service.generateAndStoreWalletAuthCode(11L,null,new BigDecimal("25.50"),5);
                assertEquals(1,repository.consumeAuthCode(11L,code,new BigDecimal("25.50"),22L));
                assertEquals(0,repository.consumeAuthCode(11L,code,new BigDecimal("25.50"),22L));
                jdbc.update("UPDATE companion SET value_amount=25.50 WHERE id=1");
                assertEquals(2,jdbc.queryForObject("SELECT status FROM wallet_auth_code",Integer.class));
                throw new IllegalStateException("模拟原收款后续失败");
            }));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM wallet_auth_code",Integer.class));
            assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT value_amount FROM companion WHERE id=1",BigDecimal.class)));
        } finally {jdbc.execute("SHUTDOWN");}
    }
}
