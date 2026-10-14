package com.agri_system.application.order.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.model.CreditRepaymentLog;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 钱包、现金、还款和欠款观察真实服务运算；仅存储及钱包边界使用状态替身。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundFundingConservationTest {
    @Mock OrderPaymentRepository paymentRepository;
    @Mock CustomerCouponRepository couponRepository;
    @Mock WalletApplicationService walletService;
    @Mock OrderDomainService orderDomainService;
    @Mock OrderItemRepository itemRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CreditAccountRepository creditAccountRepository;
    @Mock CreditRepaymentLogRepository repaymentRepository;
    @Mock AfterSalesRepository afterSalesRepository;
    @Mock com.agri_system.domain.after_sales.repository.AfterSalesItemRepository afterSalesItemRepository;
    @InjectMocks OrderPaymentService service;
    private final List<OrderPayment> payments = new ArrayList<>();
    private final List<CreditRepaymentLog> repayments = new ArrayList<>();
    private final List<AfterSales> history = new ArrayList<>();

    private Order fixture(String net, String credit, String paid) {
        Order o = new Order(); o.setId(1L); o.setCustomerId(10L); o.setStoreId(1L); o.setOrderStatus(6);
        o.setPaymentMethod(new BigDecimal(credit).signum() > 0 ? 6 : 4); o.setPayAmount(m(net));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(o));
        Customer c = new Customer(); c.setId(10L); c.setStatus(1);
        when(customerRepository.findByIdWithLock(10L)).thenReturn(Optional.of(c));
        when(paymentRepository.findByOrderId(1L)).thenAnswer(i -> payments);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenAnswer(i -> payments);
        when(afterSalesRepository.findByOrderId(1L)).thenAnswer(i -> history);
        BigDecimal b = m(credit), p = m(paid);
        if (b.signum() > 0) {
            CreditAccount a = new CreditAccount(); a.setId(11L); a.setCustomerId(10L); a.setStoreId(1L); a.setOrderId(1L);
            a.setCreditAmount(b); a.setPaidAmount(p); a.setRemainingAmount(b.subtract(p)); a.setStatus(1);
            when(creditAccountRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(a));
            when(repaymentRepository.findByCreditAccountIdForUpdate(11L)).thenAnswer(i -> repayments);
            payments.add(payment(6, credit));
        } else when(creditAccountRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        return o;
    }
    private OrderPayment payment(int type,String amount) {
        OrderPayment p = new OrderPayment(); p.setId(100L+payments.size()); p.setOrderId(1L); p.setPaymentType(type);
        p.setAmount(m(amount)); p.setRefundedAmount(m("0")); p.setCreatedAt(LocalDateTime.of(2026,10,1,9,0)); return p;
    }
    private CreditRepaymentLog repayment(int method,String amount) {
        CreditRepaymentLog r = new CreditRepaymentLog(); r.setId(200L+repayments.size()); r.setCustomerId(10L); r.setStoreId(1L);
        r.setCreditAccountId(11L); r.setAmount(m(amount)); r.setRefundedAmount(m("0")); r.setPaymentMethod(method);
        r.setCreatedAt(LocalDateTime.of(2026,10,2,9,0)); repayments.add(r); return r;
    }
    private CreditAccount account() { return creditAccountRepository.findByOrderIdForUpdate(1L).orElseThrow(); }
    private void finished(String r,String w,String f) {
        AfterSales a = new AfterSales(); a.setId(300L+history.size()); a.setOrderId(1L); a.setStatus(AfterSales.STATUS_FINISHED);
        a.setRefundAmount(m(r)); a.setCreditReductionAmount(m(w)); a.setCashRefundAmount(m(f)); history.add(a);
    }
    private BigDecimal m(String s) { return new BigDecimal(s).setScale(2); }

    @Test void zeroOffsetAloneCannotRestoreCouponWithoutActualFullReturn() {
        Order o=fixture("0","0","0");
        var payment=payment(5,"100");payment.setCouponId(99L);payments.add(payment);
        var coupon=new com.agri_system.domain.coupon.model.CustomerCoupon();coupon.setId(99L);coupon.setStatus(2);coupon.setUsedOrderId(1L);
        when(couponRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(coupon));
        service.refund(o,m("0"),7L);
        assertEquals(2,coupon.getStatus(),"没有全量实退数量证据不能恢复全额抵扣券");
    }
    @Test void fundingFingerprintIgnoresHistoryRepositoryReadOrder() {
        Order o=fixture("100","0","0");var payment=payment(1,"100");payment.setRefundedAmount(m("30"));payments.add(payment);
        finished("10","0","10");finished("20","0","20");
        var first=service.previewRefund(o,m("10"));Collections.reverse(history);var second=service.previewRefund(o,m("10"));
        assertEquals(first.state(),second.state());
    }
    @Test void fullCouponRestoresOnlyAfterBothZeroAmountGoodsAreActuallyReturned() {
        Order o=fixture("0","0","0");var payment=payment(5,"100");payment.setCouponId(99L);payments.add(payment);
        var coupon=new com.agri_system.domain.coupon.model.CustomerCoupon();coupon.setId(99L);coupon.setStatus(2);coupon.setUsedOrderId(1L);
        when(couponRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(coupon));
        var goods=new com.agri_system.domain.order.model.OrderItem();goods.setId(20L);goods.setQuantity(new BigDecimal("2"));when(itemRepository.findByOrderId(1L)).thenReturn(List.of(goods));
        service.refund(o,m("0"),7L);finished("0","0","0");
        var first=new com.agri_system.domain.after_sales.model.AfterSalesItem();first.setOrderItemId(20L);first.setQuantity(BigDecimal.ONE);
        when(afterSalesItemRepository.findByAfterSalesId(300L)).thenReturn(List.of(first));
        service.restoreCouponIfFullyReturned(o);assertEquals(2,coupon.getStatus());
        service.refund(o,m("0"),7L);finished("0","0","0");
        var second=new com.agri_system.domain.after_sales.model.AfterSalesItem();second.setOrderItemId(20L);second.setQuantity(BigDecimal.ONE);
        when(afterSalesItemRepository.findByAfterSalesId(301L)).thenReturn(List.of(second));
        service.restoreCouponIfFullyReturned(o);assertEquals(1,coupon.getStatus());
    }
    @Test void positivePennyNetCannotRestoreLargeCouponBeforeFinalZeroAmountGoodReturns() {
        Order o=fixture("0.01","0","0");payments.add(payment(1,"0.01"));var couponPayment=payment(5,"99.99");couponPayment.setCouponId(99L);payments.add(couponPayment);
        var coupon=new com.agri_system.domain.coupon.model.CustomerCoupon();coupon.setId(99L);coupon.setStatus(2);coupon.setUsedOrderId(1L);
        when(couponRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(coupon));
        var goods=new com.agri_system.domain.order.model.OrderItem();goods.setId(20L);goods.setQuantity(new BigDecimal("3"));when(itemRepository.findByOrderId(1L)).thenReturn(List.of(goods));
        for(int step=0;step<3;step++) {
            String amount=step==1?"0.01":"0";service.refund(o,m(amount),7L);finished(amount,"0",amount);
            var item=new com.agri_system.domain.after_sales.model.AfterSalesItem();item.setOrderItemId(20L);item.setQuantity(BigDecimal.ONE);
            when(afterSalesItemRepository.findByAfterSalesId(300L+step)).thenReturn(List.of(item));
            service.restoreCouponIfFullyReturned(o);
            assertEquals(step==2?1:2,coupon.getStatus(),"达到净金额仍须最后一件实物回齐");
        }
    }
    @Test void unpaidCreditReturnOnlyReducesOriginalDebt() {
        Order o=fixture("100","100","0");
        assertDoesNotThrow(() -> service.refund(o,m("30"),7L));
        assertEquals(m("70"),account().getRemainingAmount()); assertEquals(m("0"),account().getPaidAmount());
        verifyNoInteractions(walletService);
    }
    @Test void repaidCreditReturnReducesDebtThenReturnsAllocatedRepayment() {
        Order o=fixture("100","100","40"); var source=repayment(1,"40");
        assertDoesNotThrow(() -> service.refund(o,m("80"),7L));
        assertEquals(m("0"),account().getRemainingAmount()); assertEquals(m("40"),account().getPaidAmount());
        assertEquals(m("20"),source.getRefundedAmount());
    }
    @Test void mixedCreditReturnReconcilesCashRepaymentAndDebt() {
        Order o=fixture("100","60","20"); o.setPaymentMethod(10);
        var original=payment(1,"40"); payments.add(original); var repaid=repayment(1,"20");
        assertDoesNotThrow(() -> service.refund(o,m("100"),7L));
        assertEquals(m("0"),account().getRemainingAmount()); assertEquals(m("20"),account().getPaidAmount());
        assertEquals(m("40"),original.getRefundedAmount()); assertEquals(m("20"),repaid.getRefundedAmount());
    }
    @Test void bankRepaymentCodeFourNeverRefundsPrincipalWallet() {
        Order o=fixture("100","100","40"); var source=repayment(4,"40");
        assertDoesNotThrow(() -> service.refund(o,m("80"),7L));
        assertEquals(m("20"),source.getRefundedAmount()); verifyNoInteractions(walletService);
    }
    @Test void repeatedWalletReturnUsesRemainingSourceInsteadOfOriginalAmount() {
        Order o=fixture("100","0","0"); var source=payment(4,"100"); payments.add(source);
        var returned=new java.util.concurrent.atomic.AtomicReference<>(m("0"));
        doAnswer(i -> {returned.updateAndGet(v->v.add(i.getArgument(1)));return null;})
                .when(walletService).refundBalance(eq(10L),any(),any(),eq(1L),eq(7L));
        service.refund(o,m("30"),7L); finished("30","0","30");
        service.refund(o,m("50"),7L); finished("50","0","50");
        assertEquals(m("80"),source.getRefundedAmount()); assertEquals(m("80"),returned.get());
        assertThrows(IllegalArgumentException.class,()->service.refund(o,m("21"),7L));
        assertEquals(m("80"),returned.get());
    }
    @Test void sourceWithUnknownRefundCounterCannotBeRefundedAgain() {
        Order o=fixture("100","0","0"); var source=payment(4,"100"); source.setRefundedAmount(null);payments.add(source);
        assertThrows(IllegalArgumentException.class,()->service.refund(o,m("20"),7L)); verifyNoInteractions(walletService);
    }
    @Test void unknownTenderCannotDisappearFromFundingCalculation() {
        Order o=fixture("100","0","0");payments.add(payment(999,"100"));
        assertThrows(IllegalArgumentException.class,()->service.refund(o,m("20"),7L));
    }
    @Test void missingAllocatedRepaymentCannotBeGuessedFromAccountPaidAmount() {
        Order o=fixture("100","100","40");
        assertThrows(IllegalArgumentException.class,()->service.refund(o,m("80"),7L));
        assertEquals(m("60"),account().getRemainingAmount());
    }
    @Test void cashOnlySourceCannotRefundBeyondOriginalNetAmount() {
        Order o=fixture("100","0","0");payments.add(payment(1,"100"));
        assertThrows(IllegalArgumentException.class,()->service.refund(o,m("101"),7L));
        assertEquals(m("0"),payments.get(0).getRefundedAmount());
    }
    @Test void previousUnknownAfterSalesSplitRequiresReconciliation() {
        Order o=fixture("100","0","0"); payments.add(payment(1,"100")); finished("30","0","30");
        history.get(0).setCashRefundAmount(null);
        assertThrows(IllegalArgumentException.class,()->service.refund(o,m("20"),7L));
    }
}
