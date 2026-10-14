package com.agri_system.infrastructure.migration;

import db.migration.V132__credit_settlement_facts;
import org.flywaydb.core.api.migration.Context;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Connection;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离库证明确定回填与历史未知界限，不触及开发库。 */
class CreditSettlementMigrationTest {
    JdbcDataSource source; JdbcTemplate jdbc;
    @BeforeEach void schema() {
        source=new JdbcDataSource();source.setURL("jdbc:h2:mem:credit_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE store(id BIGINT PRIMARY KEY,status INT,deleted INT);
            CREATE TABLE `order`(id BIGINT PRIMARY KEY,customer_id BIGINT,store_id BIGINT,payment_method INT,pay_amount DECIMAL(10,2),total_amount DECIMAL(10,2),discount_amount DECIMAL(10,2),coupon_amount DECIMAL(10,2));
            CREATE TABLE credit_account(id BIGINT PRIMARY KEY,customer_id BIGINT,order_id BIGINT,credit_amount DECIMAL(10,2),paid_amount DECIMAL(10,2),remaining_amount DECIMAL(10,2));
            CREATE TABLE credit_repayment_log(id BIGINT PRIMARY KEY,repayment_no VARCHAR(50),customer_id BIGINT,store_id BIGINT,amount DECIMAL(10,2),created_at TIMESTAMP);
            CREATE TABLE order_payment(id BIGINT PRIMARY KEY,order_id BIGINT,payment_type INT,amount DECIMAL(10,2));
            CREATE TABLE after_sales(id BIGINT PRIMARY KEY,order_id BIGINT,status INT,refund_amount DECIMAL(10,2));
            INSERT INTO store VALUES(1,1,0);
            INSERT INTO `order` VALUES(10,100,1,6,0,100,0,0),(20,100,1,1,80,100,10,10);
            INSERT INTO credit_account VALUES(1,100,10,100,40,60);
            INSERT INTO credit_repayment_log VALUES(1,'legacy',100,1,40,CURRENT_TIMESTAMP);
            INSERT INTO order_payment VALUES(1,10,6,100),(2,20,1,80),(3,20,5,10);
            """);
    }
    void migrate() throws Exception {
        try(Connection connection=source.getConnection()) {new V132__credit_settlement_facts().migrate(new Context(){
            public Connection getConnection(){return connection;}
            public org.flywaydb.core.api.configuration.Configuration getConfiguration(){return null;}
        });}
    }
    @Test void backfillsOnlyKnownStoreAndPureCreditNetAmountWithoutInventingRepaymentAllocations() throws Exception {
        migrate();assertEquals(1L,jdbc.queryForObject("SELECT store_id FROM credit_account",Long.class));
        assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT pay_amount FROM `order` WHERE id=10",BigDecimal.class));
        assertEquals(new BigDecimal("80.00"),jdbc.queryForObject("SELECT pay_amount FROM `order` WHERE id=20",BigDecimal.class));
        assertNull(jdbc.queryForObject("SELECT credit_account_id FROM credit_repayment_log",Long.class));
        assertNull(jdbc.queryForObject("SELECT request_key FROM credit_repayment_log",String.class));
        assertNull(jdbc.queryForObject("SELECT refunded_amount FROM credit_repayment_log",BigDecimal.class));
        assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT refunded_amount FROM order_payment WHERE id=2",BigDecimal.class));
    }
    @Test void finishedUnknownReturnKeepsOriginalPaymentAndFinancialSplitUnknown() throws Exception {
        jdbc.update("INSERT INTO after_sales VALUES(1,20,50,20)");migrate();
        assertNull(jdbc.queryForObject("SELECT refunded_amount FROM order_payment WHERE id=2",BigDecimal.class));
        assertNull(jdbc.queryForObject("SELECT credit_reduction_amount FROM after_sales",BigDecimal.class));
        assertNull(jdbc.queryForObject("SELECT cash_refund_amount FROM after_sales",BigDecimal.class));
    }
    @Test void zeroMoneyHistoricGiftReturnCanBeMarkedKnownWithoutReopeningMoney() throws Exception {
        jdbc.update("INSERT INTO after_sales VALUES(1,20,50,0)");migrate();
        assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT refunded_amount FROM order_payment WHERE id=2",BigDecimal.class));
        assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT cash_refund_amount FROM after_sales",BigDecimal.class));
    }
    @Test void duplicateOriginalAccountFailsBeforeAnySchemaOrAmountChange() {
        jdbc.update("INSERT INTO credit_account VALUES(2,100,10,100,0,100)");assertThrows(Exception.class,this::migrate);
        assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT pay_amount FROM `order` WHERE id=10",BigDecimal.class));
        assertThrows(Exception.class,()->jdbc.queryForObject("SELECT store_id FROM credit_account",Long.class));
    }
    @Test void missingOrderOrWrongCustomerStopsRatherThanRepairingByGuess() {
        jdbc.update("UPDATE credit_account SET order_id=99");assertThrows(Exception.class,this::migrate);
        jdbc.update("UPDATE credit_account SET order_id=10,customer_id=99");assertThrows(Exception.class,this::migrate);
    }
    @Test void inconsistentDebtOrCreditPaymentStopsBeforeAmountChanges() {
        jdbc.update("UPDATE credit_account SET remaining_amount=50");assertThrows(Exception.class,this::migrate);
        jdbc.update("UPDATE credit_account SET remaining_amount=60");jdbc.update("UPDATE order_payment SET amount=99 WHERE id=1");assertThrows(Exception.class,this::migrate);
    }
    @Test void newAllocationAndSourceCounterConstraintsProtectMoney() throws Exception {
        migrate();jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount,credit_account_id,request_key,refunded_amount) VALUES(2,'new',100,1,10,1,'req',0)");
        assertThrows(Exception.class,()->jdbc.update("UPDATE credit_repayment_log SET refunded_amount=11 WHERE id=2"));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount,credit_account_id,request_key,refunded_amount) VALUES(3,'new',100,1,10,1,'req',0)"));
        assertThrows(Exception.class,()->jdbc.update("UPDATE order_payment SET refunded_amount=81 WHERE id=2"));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO after_sales(id,order_id,status,refund_amount,credit_reduction_amount,cash_refund_amount) VALUES(2,10,50,10,6,5)"));
    }
    @Test void oneStoreRequestHasExactlyOneHeadAcrossCustomersAndAccountsWhileNullAllocationsRemainAllowed() throws Exception {
        migrate();
        jdbc.update("INSERT INTO credit_account(id,customer_id,order_id,credit_amount,paid_amount,remaining_amount,store_id) VALUES(2,101,20,80,0,80,1)");
        jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount,credit_account_id,request_key,refunded_amount) VALUES(2,'batchA',100,1,10,1,'same-request',0)");
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount,credit_account_id,request_key,refunded_amount) VALUES(3,'batchB',101,1,10,2,'same-request',0)"));
        jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount,credit_account_id,request_key,refunded_amount) VALUES(4,'batchA',100,1,10,1,NULL,0),(5,'batchA',100,1,10,1,NULL,0)");
        jdbc.update("INSERT INTO store VALUES(2,1,0)");
        jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount,credit_account_id,request_key,refunded_amount) VALUES(6,'otherStore',100,2,10,1,'same-request',0)");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM credit_repayment_log WHERE store_id=1 AND request_key='same-request'",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM credit_repayment_log WHERE repayment_no='batchA'",Integer.class));
    }
    @Test void batchCurrentReadHasAnExactStoreAndNumberIndexAndReturnsOnlyThatBatch() throws Exception {
        migrate();
        jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount) VALUES(10,'selected',100,1,10),(11,'selected',100,1,20),(12,'selected',101,2,30)");
        for(int id=20;id<80;id++) {
            jdbc.update("INSERT INTO credit_repayment_log(id,repayment_no,customer_id,store_id,amount) VALUES(?,?,?,1,10)",id,"other-"+id,100+id);
        }
        String query="SELECT * FROM credit_repayment_log WHERE store_id=1 AND repayment_no='selected' ORDER BY id FOR UPDATE";
        assertEquals(java.util.List.of("STORE_ID","REPAYMENT_NO"),jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS WHERE TABLE_NAME='CREDIT_REPAYMENT_LOG' AND INDEX_NAME='IDX_CREDIT_REPAYMENT_BATCH' ORDER BY ORDINAL_POSITION",String.class));
        assertEquals(java.util.List.of(10L,11L),jdbc.query(query,(row,index)->row.getLong("id")));
    }
    @Test void illegalPaymentOrRepaymentAmountFailsBeforeAnyAlter() {
        jdbc.update("UPDATE order_payment SET amount=-1 WHERE id=2");
        Exception paymentFailure=assertThrows(Exception.class,this::migrate);assertTrue(paymentFailure.getMessage().contains("收款记录 2"));
        assertThrows(Exception.class,()->jdbc.queryForObject("SELECT store_id FROM credit_account",Long.class));
        jdbc.update("UPDATE order_payment SET amount=80 WHERE id=2");jdbc.update("UPDATE credit_repayment_log SET amount=-1 WHERE id=1");
        Exception repaymentFailure=assertThrows(Exception.class,this::migrate);assertTrue(repaymentFailure.getMessage().contains("还款记录 1"));
        assertThrows(Exception.class,()->jdbc.queryForObject("SELECT store_id FROM credit_account",Long.class));
    }
    @Test void mixedOriginalReceiptsMustAddUpToNetSettlementBeforeAnyAlter() {
        jdbc.update("UPDATE `order` SET payment_method=10,pay_amount=100 WHERE id=10");
        jdbc.update("UPDATE credit_account SET credit_amount=60,paid_amount=0,remaining_amount=60 WHERE id=1");
        jdbc.update("UPDATE order_payment SET amount=60 WHERE id=1");jdbc.update("INSERT INTO order_payment VALUES(4,10,1,50)");
        Exception failure=assertThrows(Exception.class,this::migrate);assertTrue(failure.getMessage().contains("原收款与赊销合计"));
        assertThrows(Exception.class,()->jdbc.queryForObject("SELECT store_id FROM credit_account",Long.class));
        assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT pay_amount FROM `order` WHERE id=10",BigDecimal.class));
    }

    @Test void historicalRefundOperationIdentityRemainsNullAndMultipleLegacyRowsAreAllowed() throws Exception {
        jdbc.update("INSERT INTO after_sales VALUES(1,20,50,20),(2,20,50,15)");migrate();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM after_sales WHERE refund_quote_id IS NULL AND refund_request_hash IS NULL",Integer.class));
        assertNull(jdbc.queryForObject("SELECT refunded_amount FROM order_payment WHERE id=2",BigDecimal.class));
    }
    @Test void successfulRefundQuoteCannotBeStoredAsTwoDifferentOperations() throws Exception {
        migrate();String quote="a".repeat(64),digest="b".repeat(64);
        jdbc.update("INSERT INTO after_sales(id,order_id,status,refund_amount,credit_reduction_amount,cash_refund_amount,refund_quote_id,refund_request_hash) VALUES(1,20,50,10,0,10,?,?)",quote,digest);
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO after_sales(id,order_id,status,refund_amount,credit_reduction_amount,cash_refund_amount,refund_quote_id,refund_request_hash) VALUES(2,20,50,10,0,10,?,?)",quote,"c".repeat(64)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM after_sales WHERE refund_quote_id=?",Integer.class,quote));
        // 相同前63位但最后一位不同的完整身份必须能独立保存，不能使用截断前缀唯一。
        jdbc.update("INSERT INTO after_sales(id,order_id,status,refund_amount,credit_reduction_amount,cash_refund_amount,refund_quote_id,refund_request_hash) VALUES(3,20,50,10,0,10,?,?)","a".repeat(63)+"c",digest);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM after_sales",Integer.class));
    }
    @Test void refundQuoteAndOriginalRequestDigestMustAppearTogether() throws Exception {
        jdbc.update("INSERT INTO after_sales VALUES(1,20,50,20)");migrate();
        assertThrows(Exception.class,()->jdbc.update("UPDATE after_sales SET refund_quote_id=? WHERE id=1","a".repeat(64)));
        assertThrows(Exception.class,()->jdbc.update("UPDATE after_sales SET refund_request_hash=? WHERE id=1","b".repeat(64)));
        assertThrows(Exception.class,()->jdbc.update("UPDATE after_sales SET refund_quote_id='',refund_request_hash='' WHERE id=1"));
        jdbc.update("UPDATE after_sales SET refund_quote_id=?,refund_request_hash=? WHERE id=1","a".repeat(64),"b".repeat(64));
    }

}
