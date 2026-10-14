package com.agri_system.application.order.service;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.credit.repository.*;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.*;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.application.wallet.service.WalletApplicationService;
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
/** 券不作为现金来源；在完成成交冲销事实后按累计净额恢复。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class OrderPaymentRefundCouponTest {
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
    @Mock AfterSalesItemRepository afterSalesItemRepository;
    @InjectMocks OrderPaymentService service;
    private final List<AfterSales> history=new ArrayList<>();
    private Order order;
    private CustomerCoupon coupon;
    private OrderPayment cash;
    private void fixture(boolean expired,int status) {
        order=new Order();order.setId(1L);order.setPayAmount(new BigDecimal("100.00"));order.setPaymentMethod(2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        cash=new OrderPayment();cash.setId(1L);cash.setOrderId(1L);cash.setPaymentType(2);cash.setAmount(new BigDecimal("100.00"));cash.setRefundedAmount(new BigDecimal("0.00"));cash.setCreatedAt(LocalDateTime.now());
        var couponPayment=new OrderPayment();couponPayment.setId(2L);couponPayment.setOrderId(1L);couponPayment.setPaymentType(5);couponPayment.setAmount(new BigDecimal("10.00"));couponPayment.setCouponId(5L);
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(List.of(cash,couponPayment));
        when(afterSalesRepository.findByOrderId(1L)).thenAnswer(i->history);
        var goods=new com.agri_system.domain.order.model.OrderItem();goods.setId(20L);goods.setQuantity(new BigDecimal("10"));
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(goods));
        coupon=new CustomerCoupon();coupon.setId(5L);coupon.setStatus(status);coupon.setUsedOrderId(1L);coupon.setEndTime(expired?LocalDateTime.now().minusDays(1):LocalDateTime.now().plusDays(1));
        when(couponRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(coupon));
    }
    private void returnAndFinish(String amount) {
        BigDecimal r=new BigDecimal(amount);service.refund(order,r,7L);
        var fact=new AfterSales();fact.setId(100L+history.size());fact.setOrderId(1L);fact.setStatus(AfterSales.STATUS_FINISHED);fact.setRefundAmount(r);fact.setCreditReductionAmount(new BigDecimal("0.00"));fact.setCashRefundAmount(r);history.add(fact);
        var goods=new com.agri_system.domain.after_sales.model.AfterSalesItem();goods.setOrderItemId(20L);goods.setQuantity(r.divide(new BigDecimal("10")));
        when(afterSalesItemRepository.findByAfterSalesId(fact.getId())).thenReturn(List.of(goods));
        service.restoreCouponIfFullyReturned(order);
    }
    @Test void fullRefundRestoresUsableCoupon() {fixture(false,2);returnAndFinish("100.00");assertEquals(1,coupon.getStatus());assertEquals(new BigDecimal("100.00"),cash.getRefundedAmount());}
    @Test void partialRefundDoesNotRestoreCoupon() {fixture(false,2);returnAndFinish("30.00");assertEquals(2,coupon.getStatus());assertEquals(new BigDecimal("30.00"),cash.getRefundedAmount());}
    @Test void cumulativeRefundRestoresOnlyOnFinalReturn() {fixture(false,2);returnAndFinish("30.00");assertEquals(2,coupon.getStatus());returnAndFinish("70.00");assertEquals(1,coupon.getStatus());}
    @Test void fullRefundExpiredCouponNotRestored() {fixture(true,2);returnAndFinish("100.00");assertEquals(2,coupon.getStatus());}
    @Test void fullRefundNonUsedStatusNotRestored() {fixture(false,4);returnAndFinish("100.00");assertEquals(4,coupon.getStatus());}
}
