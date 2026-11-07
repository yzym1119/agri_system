package com.agri_system.application.customer.service;

import com.agri_system.application.customer.service.SpecialFundGrantService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.SpecialFundGrantRequestRepository;
import com.agri_system.infrastructure.customer.repository.SpecialFundGrantRequestRepositoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 专用金发放幂等的真实 MySQL/InnoDB 集成测试（评审 P1：重放参数核对 + 并发）。
 *
 * <p>H2(MODE=MySQL) 无法复刻 InnoDB 的 ON DUPLICATE KEY 插入即占位语义、
 * SELECT ... FOR UPDATE 行锁串行化与独立事务隔离。本测试在真实 MySQL 上验证：
 * <ol>
 *   <li>reserve 对已存在 request_key 不覆盖原始客户/金额（插入即占位）；</li>
 *   <li>request_key 已 COMPLETED 但客户/金额与原请求不一致时，重放被拒绝；</li>
 *   <li>两个线程并发携带同一 request_key 调用 grant 时，钱包只加一次钱，
 *       后到者串行等待行锁后重放已记录结果。</li>
 * </ol>
 *
 * <p>默认关闭，显式 {@code -Dmysql.it=true} 开启；独立库 BeforeAll 建、AfterAll 删，
 * 与 MysqlRetailFactChainIntegrationTest 同一连接参数约定。</p>
 */
@EnabledIfSystemProperty(named = "mysql.it", matches = "true")
class SpecialFundGrantMysqlConcurrencyTest {

    private static final String HOST = System.getProperty("mysql.it.host", "127.0.0.1");
    private static final String PORT = System.getProperty("mysql.it.port", "3306");
    private static final String USER = System.getProperty("mysql.it.user", "root");
    private static final String PASSWORD = System.getProperty("mysql.it.password", "");
    private static final String DB = System.getProperty("mysql.it.database",
            "agri_retail_it_" + UUID.randomUUID().toString().replace("-", ""));
    private static final String URL_PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    private static String dbUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB + URL_PARAMS;
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(dbUrl(), USER, PASSWORD);
    }

    @BeforeAll
    static void provisionFreshSchema() throws SQLException {
        try (Connection server = DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":" + PORT + "/" + URL_PARAMS, USER, PASSWORD);
             Statement statement = server.createStatement()) {
            statement.execute("CREATE DATABASE `" + DB + "` CHARACTER SET utf8mb4");
        }
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            // 与 V139 迁移一致的最小投影 + 钱包/台账最小表
            statement.execute("CREATE TABLE special_fund_grant_request ("
                    + "request_key VARCHAR(64) PRIMARY KEY, customer_id BIGINT NOT NULL,"
                    + "amount DECIMAL(10,2) NOT NULL, balance_after DECIMAL(10,2) NULL,"
                    + "status VARCHAR(16) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB");
            statement.execute("CREATE TABLE customer_wallet ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL,"
                    + "wallet_type INT NOT NULL, balance DECIMAL(10,2) NOT NULL,"
                    + "frozen_balance DECIMAL(10,2) NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0) ENGINE=InnoDB");
            statement.execute("CREATE TABLE customer_benefit_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT NOT NULL) ENGINE=InnoDB");
            statement.execute("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(9,2,10.00)");
            // 并发测试使用独立客户行，避免与其他测试共享余额状态
            statement.execute("INSERT INTO customer_wallet(customer_id,wallet_type,balance) VALUES(21,2,10.00)");
        }
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        try (Connection server = DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":" + PORT + "/" + URL_PARAMS, USER, PASSWORD);
             Statement statement = server.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + DB + "`");
        }
    }

    private static SpecialFundGrantRequestRepository repository(JdbcTemplateHolder holder) {
        return new SpecialFundGrantRequestRepositoryImpl(holder.jdbc);
    }

    /** 钱包真实写在 MySQL 上：金额无上限约束，专测幂等并发而非金额校验。 */
    private static WalletApplicationService walletService(JdbcTemplateHolder holder) {
        WalletApplicationService walletService = mock(WalletApplicationService.class);
        when(walletService.addSpecialFund(any(), any(), eq("MANUAL_GRANT"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Long customerId = invocation.getArgument(0);
                    BigDecimal amount = invocation.getArgument(1);
                    holder.jdbc.update("UPDATE customer_wallet SET balance=balance+? WHERE customer_id=? AND wallet_type=2",
                            amount, customerId);
                    return holder.jdbc.queryForObject(
                            "SELECT balance FROM customer_wallet WHERE customer_id=? AND wallet_type=2",
                            BigDecimal.class, customerId);
                });
        return walletService;
    }

    private static CustomerBenefitLogRepository benefitLogRepository(JdbcTemplateHolder holder) {
        CustomerBenefitLogRepository repository = mock(CustomerBenefitLogRepository.class);
        when(repository.save(any(CustomerBenefitLog.class))).thenAnswer(invocation -> {
            CustomerBenefitLog log = invocation.getArgument(0);
            holder.jdbc.update("INSERT INTO customer_benefit_log(customer_id) VALUES(?)", log.getCustomerId());
            return log;
        });
        return repository;
    }

    @Test
    void reserveDuplicateKeyDoesNotOverwriteOriginalRequest() throws SQLException {
        try (JdbcTemplateHolder holder = new JdbcTemplateHolder()) {
            var repo = repository(holder);
            repo.reserve("rk-same", 9L, new BigDecimal("100.00"));
            // 相同 request_key 二次占位，但客户与金额都不同 → 不允许覆盖
            repo.reserve("rk-same", 88L, new BigDecimal("999.00"));
            var row = repo.find("rk-same", false).orElseThrow();
            assertEquals(9L, row.customerId());
            assertEquals(0, row.amount().compareTo(new BigDecimal("100.00")));
        }
    }

    @Test
    void conflictingReplayIsRejectedOnRealDatabase() throws SQLException {
        try (JdbcTemplateHolder holder = new JdbcTemplateHolder()) {
            var repo = repository(holder);
            var service = new SpecialFundGrantService(walletService(holder),
                    benefitLogRepository(holder), repo);
            service.grant(9L, new BigDecimal("30.00"), 7L, "首发放", "rk-conflict");
            // 同 request_key 换客户重放 → 必须拒绝，不能把客户 9 的结果报给客户 88
            IllegalArgumentException wrongCustomer = assertThrows(IllegalArgumentException.class,
                    () -> service.grant(88L, new BigDecimal("30.00"), 7L, null, "rk-conflict"));
            assertTrue(wrongCustomer.getMessage().contains("已被占用"));
            // 同 request_key 换金额重放 → 同样拒绝
            IllegalArgumentException wrongAmount = assertThrows(IllegalArgumentException.class,
                    () -> service.grant(9L, new BigDecimal("300.00"), 7L, null, "rk-conflict"));
            assertTrue(wrongAmount.getMessage().contains("已被占用"));
            // 参数一致的合法重放仍成功且不二次加钱
            BigDecimal replayed = service.grant(9L, new BigDecimal("30.00"), 7L, null, "rk-conflict");
            assertEquals(0, replayed.compareTo(new BigDecimal("40.00")));
            assertEquals(new BigDecimal("40.00"), queryBalance(holder, 9L),
                    "合法重放不得二次加钱");
            assertEquals(1, queryBenefitLogs(holder, 9L), "台账只允许一条");
        }
    }

    @Test
    void concurrentGrantWithSameRequestKeyGrantsOnlyOnce() throws Exception {
        try (JdbcTemplateHolder holder = new JdbcTemplateHolder()) {
            var repo = repository(holder);
            var service = new SpecialFundGrantService(walletService(holder),
                    benefitLogRepository(holder), repo);
            var txTemplate = new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(holder.source));
            Callable<BigDecimal> grantTask = () -> txTemplate.execute(status ->
                    service.grant(21L, new BigDecimal("50.00"), 7L, "并发", "rk-race"));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<BigDecimal> first = pool.submit(grantTask);
                Future<BigDecimal> second = pool.submit(grantTask);
                BigDecimal b1 = first.get(30, TimeUnit.SECONDS);
                BigDecimal b2 = second.get(30, TimeUnit.SECONDS);
                assertEquals(b1, b2, "并发双方应得到同一发放后余额");
                assertEquals(0, b1.compareTo(new BigDecimal("60.00")), "50 元只发放一次(10+50)");
            } finally {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
            assertEquals(new BigDecimal("60.00"), queryBalance(holder, 21L), "并发后余额必须只加一次(10+50)");
            assertEquals(1, queryBenefitLogs(holder, 21L), "并发后台账只允许一条");
            try (PreparedStatement ps = open().prepareStatement(
                    "SELECT status, COUNT(*) FROM special_fund_grant_request WHERE request_key='rk-race'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("COMPLETED", rs.getString(1));
                    assertEquals(1, rs.getInt(2), "请求行只能有一条");
                }
            }
        }
    }

    private static BigDecimal queryBalance(JdbcTemplateHolder holder, long customerId) {
        return holder.jdbc.queryForObject(
                "SELECT balance FROM customer_wallet WHERE customer_id=? AND wallet_type=2",
                BigDecimal.class, customerId);
    }

    private static int queryBenefitLogs(JdbcTemplateHolder holder, long customerId) {
        Integer count = holder.jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_benefit_log WHERE customer_id=?", Integer.class, customerId);
        return count == null ? 0 : count;
    }

    /** 每个测试独立建表清库，连接串指向同一独立 MySQL 库。 */
    private static final class JdbcTemplateHolder implements AutoCloseable {
        final DriverManagerDataSource source = new DriverManagerDataSource(dbUrl(), USER, PASSWORD);
        final org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(source);

        @Override
        public void close() {
            // DriverManagerDataSource 每次调用新建连接，无池化资源需显式关闭
        }
    }
}
