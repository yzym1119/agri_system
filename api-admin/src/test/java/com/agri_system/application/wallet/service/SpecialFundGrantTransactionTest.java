package com.agri_system.application.wallet.service;

import com.agri_system.application.customer.service.SpecialFundGrantService;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 真实 Flex 事务连接：台账写入失败时，钱包余额与流水必须一并回滚，
 * 不能出现「钱已到账、台账缺失」。仓储为基于同一 JdbcTemplate 的测试替身，
 * 事务管理器与连接方式沿用 WalletAuthCodePrivacyTest 的既有模式。
 */
class SpecialFundGrantTransactionTest {

    @Test
    void benefitLogFailureRollsBackWalletBalanceAndTransactionTogether() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:grant_tx_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var flex = new com.mybatisflex.core.datasource.FlexDataSource("grant-boundary", source, false);
        var jdbc = new JdbcTemplate(flex);
        jdbc.execute("CREATE TABLE customer_wallet(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,"
                + "wallet_type INT,balance DECIMAL(10,2),frozen_balance DECIMAL(10,2),version INT)");
        jdbc.execute("CREATE TABLE wallet_transaction(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,amount DECIMAL(10,2))");
        jdbc.update("INSERT INTO customer_wallet(customer_id,wallet_type,balance,frozen_balance,version) VALUES(9,2,10.00,0.00,0)");

        CustomerWalletRepository walletRepository = mock(CustomerWalletRepository.class);
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(9L, 2))
                .thenAnswer(invocation -> Optional.of(readWallet(jdbc)));
        when(walletRepository.save(any(CustomerWallet.class))).thenAnswer(invocation -> {
            CustomerWallet w = invocation.getArgument(0);
            jdbc.update("UPDATE customer_wallet SET balance=?,version=? WHERE id=?",
                    w.getBalance(), w.getVersion(), w.getId());
            return w;
        });
        WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
        when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction t = invocation.getArgument(0);
            jdbc.update("INSERT INTO wallet_transaction(customer_id,amount) VALUES(?,?)",
                    t.getCustomerId(), t.getAmount());
            return t;
        });
        CustomerBenefitLogRepository benefitLogRepository = mock(CustomerBenefitLogRepository.class);
        when(benefitLogRepository.save(any(CustomerBenefitLog.class)))
                .thenThrow(new IllegalStateException("台账写入失败"));

        var walletService = new WalletApplicationService(walletRepository, transactionRepository, null);
        var grantService = new SpecialFundGrantService(walletService, benefitLogRepository);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new com.mybatisflex.spring.FlexTransactionManager());

        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            grantService.grant(9L, new BigDecimal("50.00"), 7L, "活动补发");
            return null;
        }));
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject(
                "SELECT balance FROM customer_wallet WHERE customer_id=9 AND wallet_type=2", BigDecimal.class),
                "台账失败后余额必须回到发放前");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transaction", Integer.class),
                "台账失败后不得留下赠送流水");
    }

    private static CustomerWallet readWallet(JdbcTemplate jdbc) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM customer_wallet WHERE customer_id=9 AND wallet_type=2");
        var wallet = new CustomerWallet();
        wallet.setId(((Number) row.get("ID")).longValue());
        wallet.setCustomerId(9L);
        wallet.setWalletType(2);
        wallet.setBalance((BigDecimal) row.get("BALANCE"));
        wallet.setFrozenBalance((BigDecimal) row.get("FROZEN_BALANCE"));
        wallet.setVersion(((Number) row.get("VERSION")).intValue());
        return wallet;
    }
}
