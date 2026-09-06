package com.agri_system.application.customer.job;

import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 积分年度清零任务测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class PointsClearJobTest {

    @Mock
    private CustomerWalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @InjectMocks
    private PointsClearJob pointsClearJob;

    private CustomerWallet walletWithPoints;
    private CustomerWallet walletZeroBalance;
    private CustomerWallet walletNullBalance;

    @BeforeEach
    void setUp() {
        // 初始化有积分的钱包
        walletWithPoints = new CustomerWallet();
        walletWithPoints.setId(1L);
        walletWithPoints.setCustomerId(1L);
        walletWithPoints.setWalletType(3); // 积分
        walletWithPoints.setBalance(new BigDecimal("1000.00"));
        walletWithPoints.setFrozenBalance(BigDecimal.ZERO);
        walletWithPoints.setVersion(0);

        // 初始化余额为0的钱包
        walletZeroBalance = new CustomerWallet();
        walletZeroBalance.setId(2L);
        walletZeroBalance.setCustomerId(2L);
        walletZeroBalance.setWalletType(3); // 积分
        walletZeroBalance.setBalance(BigDecimal.ZERO);
        walletZeroBalance.setFrozenBalance(BigDecimal.ZERO);
        walletZeroBalance.setVersion(0);

        // 初始化余额为null的钱包
        walletNullBalance = new CustomerWallet();
        walletNullBalance.setId(3L);
        walletNullBalance.setCustomerId(3L);
        walletNullBalance.setWalletType(3); // 积分
        walletNullBalance.setBalance(null);
        walletNullBalance.setFrozenBalance(BigDecimal.ZERO);
        walletNullBalance.setVersion(0);
    }

    /**
     * 测试用例1：正常清零
     */
    @Test
    void testClearPointsYearly_Success() {
        // Given
        List<CustomerWallet> wallets = List.of(walletWithPoints);

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        // 验证：余额被重置为0
        ArgumentCaptor<CustomerWallet> walletCaptor = ArgumentCaptor.forClass(CustomerWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        CustomerWallet savedWallet = walletCaptor.getValue();
        assertEquals(BigDecimal.ZERO, savedWallet.getBalance());

        // 验证：记录了流水
        ArgumentCaptor<WalletTransaction> transactionCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        WalletTransaction transaction = transactionCaptor.getValue();
        assertEquals(1L, transaction.getCustomerId());
        assertEquals(3, transaction.getWalletType());
        assertEquals(5, transaction.getTransactionType()); // 扣除
        assertEquals(new BigDecimal("-1000.00"), transaction.getAmount());
        assertEquals(new BigDecimal("1000.00"), transaction.getBalanceBefore());
        assertEquals(BigDecimal.ZERO, transaction.getBalanceAfter());
        assertEquals("YEARLY_CLEAR", transaction.getReferenceType());
        assertEquals("年度积分清零", transaction.getRemark());
    }

    /**
     * 测试用例2：余额为0跳过
     */
    @Test
    void testClearPointsYearly_ZeroBalance() {
        // Given
        List<CustomerWallet> wallets = List.of(walletZeroBalance);

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        // 验证：没有更新钱包
        verify(walletRepository, never()).save(any());
        // 验证：没有记录流水
        verify(transactionRepository, never()).save(any());
    }

    /**
     * 测试用例3：余额为null跳过
     */
    @Test
    void testClearPointsYearly_NullBalance() {
        // Given
        List<CustomerWallet> wallets = List.of(walletNullBalance);

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        // 验证：没有更新钱包
        verify(walletRepository, never()).save(any());
        // 验证：没有记录流水
        verify(transactionRepository, never()).save(any());
    }

    /**
     * 测试用例4：批量处理多个钱包
     */
    @Test
    void testClearPointsYearly_MultipleWallets() {
        // Given
        CustomerWallet wallet2 = new CustomerWallet();
        wallet2.setId(4L);
        wallet2.setCustomerId(4L);
        wallet2.setWalletType(3);
        wallet2.setBalance(new BigDecimal("500.00"));

        CustomerWallet wallet3 = new CustomerWallet();
        wallet3.setId(5L);
        wallet3.setCustomerId(5L);
        wallet3.setWalletType(3);
        wallet3.setBalance(new BigDecimal("200.00"));

        List<CustomerWallet> wallets = List.of(walletWithPoints, walletZeroBalance, wallet2, wallet3);

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        // 验证：更新了3个钱包（跳过余额为0的）
        verify(walletRepository, times(3)).save(any(CustomerWallet.class));
        // 验证：记录了3条流水
        verify(transactionRepository, times(3)).save(any(WalletTransaction.class));
    }

    /**
     * 测试用例5：单个客户失败不影响其他客户
     */
    @Test
    void testClearPointsYearly_SingleFailure() {
        // Given
        CustomerWallet wallet2 = new CustomerWallet();
        wallet2.setId(4L);
        wallet2.setCustomerId(4L);
        wallet2.setWalletType(3);
        wallet2.setBalance(new BigDecimal("500.00"));

        List<CustomerWallet> wallets = List.of(walletWithPoints, wallet2);

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);
        when(walletRepository.save(walletWithPoints)).thenThrow(new RuntimeException("数据库错误"));

        // When
        assertDoesNotThrow(() -> pointsClearJob.clearPointsYearly());

        // Then
        // 验证：第二个钱包仍然被处理
        verify(walletRepository, atLeastOnce()).save(any(CustomerWallet.class));
        // 验证：至少记录了一条流水（第二个钱包的）
        verify(transactionRepository, atLeastOnce()).save(any(WalletTransaction.class));
    }

    /**
     * 测试用例6：空列表处理
     */
    @Test
    void testClearPointsYearly_EmptyList() {
        // Given
        List<CustomerWallet> wallets = new ArrayList<>();

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        // 验证：没有更新钱包
        verify(walletRepository, never()).save(any());
        // 验证：没有记录流水
        verify(transactionRepository, never()).save(any());
    }

    /**
     * 测试用例7：负数余额处理（异常情况）
     */
    @Test
    void testClearPointsYearly_NegativeBalance() {
        // Given
        CustomerWallet wallet = new CustomerWallet();
        wallet.setId(6L);
        wallet.setCustomerId(6L);
        wallet.setWalletType(3);
        wallet.setBalance(new BigDecimal("-100.00")); // 负数余额（异常情况）

        List<CustomerWallet> wallets = List.of(wallet);

        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        // 验证：负数余额也会被清零（因为 <= 0 的判断）
        // 当前实现会跳过，因为 compareTo(BigDecimal.ZERO) <= 0
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    /**
     * 测试用例8：本年度已清零的客户被跳过（幂等守卫）
     * 年内重跑时,即便客户又攒了积分(balance>0),也不会被二次清零。
     */
    @Test
    void testClearPointsYearly_SkipAlreadyClearedThisYear() {
        // Given
        List<CustomerWallet> wallets = List.of(walletWithPoints);
        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);
        long year = java.time.LocalDate.now().getYear();
        // 客户1本年度已有 YEARLY_CLEAR 记录
        when(transactionRepository.findCustomerIdsByReference(3, "YEARLY_CLEAR", year))
                .thenReturn(List.of(1L));

        // When
        pointsClearJob.clearPointsYearly();

        // Then：不清零、不写流水
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    /**
     * 测试用例9：清零流水写入 referenceId=清零年度（幂等去重键）
     */
    @Test
    void testClearPointsYearly_SetsReferenceIdToYear() {
        // Given
        List<CustomerWallet> wallets = List.of(walletWithPoints);
        when(walletRepository.findAllByWalletType(3)).thenReturn(wallets);

        // When
        pointsClearJob.clearPointsYearly();

        // Then
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(captor.capture());
        long year = java.time.LocalDate.now().getYear();
        assertEquals(year, captor.getValue().getReferenceId().longValue());
    }
}









