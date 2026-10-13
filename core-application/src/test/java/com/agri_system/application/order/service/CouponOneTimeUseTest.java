package com.agri_system.application.order.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 优惠券一次性核销测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class CouponOneTimeUseTest {
    
    @Mock
    private OrderPaymentRepository paymentRepository;
    
    @Mock
    private CustomerCouponRepository couponRepository;
    
    @Mock
    private WalletApplicationService walletService;
    
    @Mock
    private OrderDomainService orderDomainService;
    
    @Mock
    private OrderItemRepository itemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderPaymentService paymentService;
    
    private Order order;
    private CustomerCoupon coupon;
    
    @BeforeEach
    void setUp() {
        // 创建订单：金额 50元
        order = new Order();
        order.setId(1L);
        order.setCustomerId(1L);
        order.setPayAmount(new BigDecimal("50.00"));
        
        // 创建优惠券：面额 100元
        coupon = new CustomerCoupon();
        coupon.setId(1L);
        coupon.setCustomerId(1L);
        coupon.setAmount(new BigDecimal("100.00"));
        coupon.setStatus(1); // 未使用
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(30));
        coupon.setMinOrderAmount(BigDecimal.ZERO);
    }
    
    /**
     * 场景：订单金额 50元，使用 100元优惠券
     * 验证点：
     * 1. 订单实付变为 0
     * 2. customer_coupon 状态变为 USED (2)
     * 3. 再次使用该券应抛出异常
     */
    @Test
    void testCouponOneTimeUse_Order50_Coupon100() {
        // Given
        when(couponRepository.findById(1L)).thenReturn(java.util.Optional.of(coupon));
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(coupon));
        when(itemRepository.findByOrderId(1L)).thenReturn(java.util.Collections.emptyList());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        var payments = paymentService.executePayment(order, false, false, 1L, null,
                false, null, null, null);
        
        // Then
        // 1. 验证优惠券状态变为已使用
        assertEquals(2, coupon.getStatus(), "优惠券状态应为已使用");
        assertNotNull(coupon.getUsedTime(), "使用时间应已设置");
        assertEquals(1L, coupon.getUsedOrderId(), "使用订单ID应已设置");
        
        // 2. 验证支付明细：优惠券抵扣 50元（订单金额）
        assertEquals(1, payments.size(), "应只有一条支付记录");
        var payment = payments.get(0);
        assertEquals(5, payment.getPaymentType(), "支付类型应为优惠券");
        assertEquals(new BigDecimal("50.00"), payment.getAmount(), "抵扣金额应为订单金额50元");
        assertEquals(1L, payment.getCouponId(), "优惠券ID应正确");
        
        // 3. 验证再次使用该券应抛出异常
        coupon.setStatus(2); // 已使用
        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.executePayment(order, false, false, 1L, null,
                    false, null, null, null);
        }, "已使用的优惠券再次使用应抛出异常");
    }
    
    /**
     * 场景：订单金额 150元，使用 100元优惠券
     * 验证点：
     * 1. 优惠券抵扣 100元（券面额）
     * 2. 订单还需支付 50元
     * 3. 优惠券状态变为已使用
     */
    @Test
    void testCouponOneTimeUse_Order150_Coupon100() {
        // Given
        order.setPayAmount(new BigDecimal("150.00"));
        when(couponRepository.findById(1L)).thenReturn(java.util.Optional.of(coupon));
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(coupon));
        when(itemRepository.findByOrderId(1L)).thenReturn(java.util.Collections.emptyList());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        var payments = paymentService.executePayment(order, false, false, 1L, null,
                true, 2, null, null);
        
        // Then
        // 1. 验证优惠券状态变为已使用
        assertEquals(2, coupon.getStatus(), "优惠券状态应为已使用");
        
        // 2. 验证支付明细：优惠券抵扣 100元（券面额）
        var couponPayment = payments.stream()
                .filter(p -> p.getPaymentType() == 5)
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("100.00"), couponPayment.getAmount(), "抵扣金额应为券面额100元");
        
        // 3. 验证总支付金额
        BigDecimal totalPaid = payments.stream()
                .map(p -> p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("150.00"), totalPaid, "总支付金额应为订单金额150元");
    }
}








