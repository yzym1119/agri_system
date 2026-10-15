package com.agri_system.application.customer.service;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * 退款回退积分与客户统计 —— 与 onOrderFinished 对称的回退逻辑。
 */
@DisplayName("退款回退积分与客户统计")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerSettlementRefundTest {

    @Mock CustomerDomainService customerDomainService;
    @Mock CustomerRepository customerRepository;
    @Mock CustomerWalletRepository walletRepository;
    @Mock WalletTransactionRepository transactionRepository;
    @InjectMocks CustomerSettlementService service;

    private Customer newCustomer(long id, BigDecimal totalAmount, int count) {
        Customer c = new Customer();
        c.setId(id);
        c.setTotalOrderAmount(totalAmount);
        c.setTotalOrderCount(count);
        return c;
    }

    private Order newOrder(long id, Long customerId, BigDecimal pay, int status, int paymentMethod) {
        Order o = new Order();
        o.setId(id);
        o.setCustomerId(customerId);
        o.setPayAmount(pay);
        o.setOrderStatus(status);
        o.setPaymentMethod(paymentMethod);
        return o;
    }

    private CustomerWallet newPointsWallet(BigDecimal balance) {
        CustomerWallet w = new CustomerWallet();
        w.setCustomerId(1L);
        w.setWalletType(3);
        w.setBalance(balance);
        w.setFrozenBalance(BigDecimal.ZERO);
        w.setVersion(0);
        return w;
    }

    private WalletTransaction newTxn(int type, BigDecimal amount) {
        WalletTransaction t = new WalletTransaction();
        t.setTransactionType(type);
        t.setAmount(amount);
        return t;
    }

    @Test
    @DisplayName("全额退款：扣全额积分 + 累计消费清零 + 订单数-1")
    void fullRefund() {
        Customer c = newCustomer(1L, new BigDecimal("100"), 5);
        Order o = newOrder(10L, 1L, new BigDecimal("100"), 8, 2);
        CustomerWallet w = newPointsWallet(new BigDecimal("100"));

        when(customerRepository.findByIdWithLock(1L)).thenReturn(Optional.of(c));
        when(transactionRepository.findByReference(1L, 3, "ORDER", 10L))
                .thenReturn(List.of(newTxn(4, new BigDecimal("100"))));
        when(transactionRepository.findByReference(1L, 3, "ORDER_REFUND", 10L))
                .thenReturn(List.of());
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 3))
                .thenReturn(Optional.of(w));

        service.onOrderRefunded(o, new BigDecimal("100"));

        assertEquals(0, c.getTotalOrderAmount().compareTo(BigDecimal.ZERO));
        assertEquals(4, c.getTotalOrderCount());
        assertEquals(0, w.getBalance().compareTo(BigDecimal.ZERO));

        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(cap.capture());
        assertEquals(0, cap.getValue().getAmount().compareTo(new BigDecimal("-100")));
        assertEquals("ORDER_REFUND", cap.getValue().getReferenceType());
        assertEquals(10L, cap.getValue().getReferenceId());
        verify(customerRepository).save(c);
        verify(walletRepository).save(w);
    }

    @Test
    @DisplayName("部分退款：按比例扣积分（30%），订单数不变")
    void partialRefund() {
        Customer c = newCustomer(1L, new BigDecimal("100"), 5);
        Order o = newOrder(10L, 1L, new BigDecimal("100"), 9, 2); // 9=部分退款
        CustomerWallet w = newPointsWallet(new BigDecimal("100"));

        when(customerRepository.findByIdWithLock(1L)).thenReturn(Optional.of(c));
        when(transactionRepository.findByReference(1L, 3, "ORDER", 10L))
                .thenReturn(List.of(newTxn(4, new BigDecimal("100"))));
        when(transactionRepository.findByReference(1L, 3, "ORDER_REFUND", 10L))
                .thenReturn(List.of());
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 3))
                .thenReturn(Optional.of(w));

        service.onOrderRefunded(o, new BigDecimal("30"));

        assertEquals(0, c.getTotalOrderAmount().compareTo(new BigDecimal("70")));
        assertEquals(5, c.getTotalOrderCount()); // 部分退款不减订单数
        assertEquals(0, w.getBalance().compareTo(new BigDecimal("70")));

        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(cap.capture());
        assertEquals(0, cap.getValue().getAmount().compareTo(new BigDecimal("-30")));
    }

    @Test
    @DisplayName("积分已被消耗：仅回退当前余额，钱包不为负")
    void pointsAlreadyConsumed() {
        Customer c = newCustomer(1L, new BigDecimal("100"), 5);
        Order o = newOrder(10L, 1L, new BigDecimal("100"), 8, 2);
        CustomerWallet w = newPointsWallet(new BigDecimal("20")); // 余额仅剩20（其余已兑换消耗）

        when(customerRepository.findByIdWithLock(1L)).thenReturn(Optional.of(c));
        when(transactionRepository.findByReference(1L, 3, "ORDER", 10L))
                .thenReturn(List.of(newTxn(4, new BigDecimal("100"))));
        when(transactionRepository.findByReference(1L, 3, "ORDER_REFUND", 10L))
                .thenReturn(List.of());
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 3))
                .thenReturn(Optional.of(w));

        service.onOrderRefunded(o, new BigDecimal("100"));

        assertEquals(0, w.getBalance().compareTo(BigDecimal.ZERO)); // 扣20，不为负
        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(cap.capture());
        assertEquals(0, cap.getValue().getAmount().compareTo(new BigDecimal("-20")));
    }

    @Test
    @DisplayName("赊销订单：回退统计但不回退积分")
    void creditOrderStatsOnly() {
        Customer c = newCustomer(1L, new BigDecimal("100"), 5);
        Order o = newOrder(10L, 1L, new BigDecimal("100"), 8, OrderMainPaymentMethod.CREDIT.getCode());

        when(customerRepository.findByIdWithLock(1L)).thenReturn(Optional.of(c));

        service.onOrderRefunded(o, new BigDecimal("100"));

        assertEquals(0, c.getTotalOrderAmount().compareTo(BigDecimal.ZERO));
        assertEquals(4, c.getTotalOrderCount());
        verify(customerRepository).save(c);
        // 赊销订单当初未发积分，退款时不触碰积分钱包/流水
        verify(transactionRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("多次部分退款累计封顶：已回退70后，本次按比例应50但封顶至剩余30")
    void cumulativeCap() {
        Customer c = newCustomer(1L, new BigDecimal("100"), 5);
        Order o = newOrder(10L, 1L, new BigDecimal("100"), 9, 2);
        CustomerWallet w = newPointsWallet(new BigDecimal("30"));

        when(customerRepository.findByIdWithLock(1L)).thenReturn(Optional.of(c));
        when(transactionRepository.findByReference(1L, 3, "ORDER", 10L))
                .thenReturn(List.of(newTxn(4, new BigDecimal("100"))));
        // 之前已回退70（流水金额 -70）
        when(transactionRepository.findByReference(1L, 3, "ORDER_REFUND", 10L))
                .thenReturn(List.of(newTxn(5, new BigDecimal("-70"))));
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 3))
                .thenReturn(Optional.of(w));

        // 本次退款 50（比例 50% → 名义 50），但剩余可退 = 100-70 = 30，封顶 30
        service.onOrderRefunded(o, new BigDecimal("50"));

        ArgumentCaptor<WalletTransaction> cap = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(cap.capture());
        assertEquals(0, cap.getValue().getAmount().compareTo(new BigDecimal("-30")));
        assertEquals(0, w.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("散客订单（无 customerId）：不处理")
    void walkInNoOp() {
        Order o = newOrder(10L, null, new BigDecimal("100"), 8, 2);
        service.onOrderRefunded(o, new BigDecimal("100"));
        verifyNoInteractions(customerRepository, walletRepository, transactionRepository);
    }
}
