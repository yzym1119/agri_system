package com.agri_system.application.order.service;

import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.application.wallet.service.WalletApplicationService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderPaymentService.refund 的优惠券退回分支：
 * 仅全额退款才退回券；部分退款或券已过期均不退。
 */
@DisplayName("退款优惠券退回策略")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPaymentRefundCouponTest {

    @Mock OrderPaymentRepository paymentRepository;
    @Mock CustomerCouponRepository couponRepository;
    @Mock WalletApplicationService walletService;
    @Mock OrderDomainService orderDomainService;
    @Mock OrderItemRepository itemRepository;
    @Mock ProductRepository productRepository;

    @InjectMocks OrderPaymentService paymentService;

    private Order order(int paymentMethod, BigDecimal payAmount) {
        Order o = new Order();
        o.setId(1L);
        o.setPaymentMethod(paymentMethod);
        o.setPayAmount(payAmount);
        return o;
    }

    private OrderPayment couponPayment(BigDecimal amount, Long couponId) {
        OrderPayment p = new OrderPayment();
        p.setPaymentType(PaymentMethod.COUPON.getCode());
        p.setAmount(amount);
        p.setCouponId(couponId);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    private CustomerCoupon usedCoupon(LocalDateTime endTime) {
        CustomerCoupon c = new CustomerCoupon();
        c.setId(5L);
        c.setStatus(2); // 已使用
        c.setUsedOrderId(1L);
        c.setUsedTime(LocalDateTime.now().minusDays(1));
        c.setEndTime(endTime);
        return c;
    }

    @Test
    @DisplayName("全额退款 + 券未过期：恢复为未使用")
    void fullRefundRestoresUsableCoupon() {
        Order o = order(2, new BigDecimal("100")); // paymentMethod=微信(非赊销)
        when(paymentRepository.findByOrderId(1L))
                .thenReturn(new ArrayList<>(List.of(couponPayment(new BigDecimal("100"), 5L))));
        when(couponRepository.findById(5L)).thenReturn(Optional.of(usedCoupon(LocalDateTime.now().plusDays(7))));

        paymentService.refund(o, null, 7L); // null = 全额退款

        ArgumentCaptor<CustomerCoupon> cap = ArgumentCaptor.forClass(CustomerCoupon.class);
        verify(couponRepository).save(cap.capture());
        assertEquals(1, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("部分退款：不退回券（save 不触发）")
    void partialRefundDoesNotRestoreCoupon() {
        Order o = order(2, new BigDecimal("100"));
        when(paymentRepository.findByOrderId(1L))
                .thenReturn(new ArrayList<>(List.of(couponPayment(new BigDecimal("100"), 5L))));

        paymentService.refund(o, new BigDecimal("30"), 7L); // 部分退款

        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("全额退款但券已过期：不退回（save 不触发，状态保持已使用）")
    void fullRefundExpiredCouponNotRestored() {
        Order o = order(2, new BigDecimal("100"));
        when(paymentRepository.findByOrderId(1L))
                .thenReturn(new ArrayList<>(List.of(couponPayment(new BigDecimal("100"), 5L))));
        CustomerCoupon expired = usedCoupon(LocalDateTime.now().minusDays(1));
        when(couponRepository.findById(5L)).thenReturn(Optional.of(expired));

        paymentService.refund(o, null, 7L);

        verify(couponRepository, never()).save(any());
        assertEquals(2, expired.getStatus()); // 状态不变
    }

    @Test
    @DisplayName("全额退款但券非已使用状态（如已作废）：不退回")
    void fullRefundNonUsedStatusNotRestored() {
        Order o = order(2, new BigDecimal("100"));
        when(paymentRepository.findByOrderId(1L))
                .thenReturn(new ArrayList<>(List.of(couponPayment(new BigDecimal("100"), 5L))));
        CustomerCoupon voided = usedCoupon(LocalDateTime.now().plusDays(7));
        voided.setStatus(4); // 已作废
        when(couponRepository.findById(5L)).thenReturn(Optional.of(voided));

        paymentService.refund(o, null, 7L);

        verify(couponRepository, never()).save(any());
    }
}
