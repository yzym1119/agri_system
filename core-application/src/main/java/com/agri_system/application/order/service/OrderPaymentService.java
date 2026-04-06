package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.application.order.dto.vo.OrderPaymentVO;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单支付服务
 * 实现混合支付扣减链和退款链
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentService {
    
    private final OrderPaymentRepository paymentRepository;
    private final CustomerCouponRepository couponRepository;
    private final WalletApplicationService walletService;
    private final OrderDomainService orderDomainService;
    private final OrderItemRepository itemRepository;
    
    /**
     * 预运算支付金额（不实际扣减）
     * 
     * @param order 订单
     * @param useBalance 是否使用本金余额
     * @param useSpecialFund 是否使用专用金
     * @param couponId 优惠券ID
     * @return 支付预览
     */
    public OrderPayPreviewVO previewPayment(Order order, Boolean useBalance, Boolean useSpecialFund, Long couponId) {
        BigDecimal totalAmount = order.getPayAmount();
        List<OrderPaymentVO> payments = new ArrayList<>();
        
        BigDecimal remaining = totalAmount;
        BigDecimal couponAmount = BigDecimal.ZERO;
        BigDecimal specialFundAmount = BigDecimal.ZERO;
        BigDecimal balanceAmount = BigDecimal.ZERO;
        
        // 1. 优惠券抵扣
        if (couponId != null && order.getCustomerId() != null) {
            CustomerCoupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券不存在"));
            
            // 获取订单商品ID和分类ID用于校验
            List<OrderItem> items = itemRepository.findByOrderId(order.getId());
            List<Long> productIds = items.stream()
                    .filter(item -> item.getProductId() != null)
                    .map(OrderItem::getProductId)
                    .collect(Collectors.toList());
            List<Long> categoryIds = new ArrayList<>(); // TODO: 从商品表获取分类ID
            
            if (coupon.isUsable(totalAmount, productIds, categoryIds)) {
                // 优惠券一次性核销：实际抵扣 = min(订单待付, 券面额)
                BigDecimal faceValue = coupon.getAvailableAmount();
                couponAmount = remaining.min(faceValue);
                remaining = remaining.subtract(couponAmount);
                
                OrderPaymentVO payment = new OrderPaymentVO();
                payment.setPaymentType(PaymentMethod.COUPON.getCode());
                payment.setAmount(couponAmount);
                payment.setCouponId(couponId);
                payments.add(payment);
            }
        }
        
        // 2. 专用金抵扣（仅允许专用金的商品）
        if (Boolean.TRUE.equals(useSpecialFund) && order.getCustomerId() != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
            List<OrderItem> items = itemRepository.findByOrderId(order.getId());
            BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(items);
            BigDecimal specialFundBalance = walletService.getSpecialFundBalance(order.getCustomerId());
            
            specialFundAmount = remaining.compareTo(maxSpecialFund) > 0 ? maxSpecialFund : remaining;
            if (specialFundAmount.compareTo(specialFundBalance) > 0) {
                specialFundAmount = specialFundBalance;
            }
            
            if (specialFundAmount.compareTo(BigDecimal.ZERO) > 0) {
                remaining = remaining.subtract(specialFundAmount);
                
                OrderPaymentVO payment = new OrderPaymentVO();
                payment.setPaymentType(4);
                payment.setAmount(specialFundAmount);
                payments.add(payment);
            }
        }
        
        // 3. 本金余额抵扣
        if (Boolean.TRUE.equals(useBalance) && order.getCustomerId() != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balance = walletService.getBalance(order.getCustomerId());
            balanceAmount = remaining.compareTo(balance) > 0 ? balance : remaining;
            
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                remaining = remaining.subtract(balanceAmount);
                
                OrderPaymentVO payment = new OrderPaymentVO();
                payment.setPaymentType(3);
                payment.setAmount(balanceAmount);
                payments.add(payment);
            }
        }
        
        // 4. 外部支付（微信/支付宝）
        BigDecimal externalAmount = remaining;
        if (externalAmount.compareTo(BigDecimal.ZERO) > 0) {
            OrderPaymentVO payment = new OrderPaymentVO();
            payment.setPaymentType(1); // 微信
            payment.setAmount(externalAmount);
            payments.add(payment);
        }
        
        OrderPayPreviewVO preview = new OrderPayPreviewVO();
        preview.setTotalAmount(totalAmount);
        preview.setCouponAmount(couponAmount);
        preview.setSpecialFundAmount(specialFundAmount);
        preview.setBalanceAmount(balanceAmount);
        preview.setExternalAmount(externalAmount);
        preview.setPayments(payments);
        
        return preview;
    }
    
    /**
     * 执行混合支付扣减（实际扣减）
     * 
     * @param order 订单
     * @param useBalance 是否使用本金余额
     * @param useSpecialFund 是否使用专用金
     * @param couponId 优惠券ID
     * @param createdBy 操作人ID
     * @return 支付明细列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderPayment> executePayment(Order order, Boolean useBalance, Boolean useSpecialFund, 
                                            Long couponId, Long createdBy) {
        // 先预运算
        OrderPayPreviewVO preview = previewPayment(order, useBalance, useSpecialFund, couponId);
        
        List<OrderPayment> payments = new ArrayList<>();
        BigDecimal totalPaid = BigDecimal.ZERO;
        
        // 1. 优惠券抵扣（一次性核销，不设找零）
        if (preview.getCouponAmount().compareTo(BigDecimal.ZERO) > 0 && couponId != null) {
            CustomerCoupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券不存在"));
            
            // 校验状态：必须是未使用
            if (coupon.getStatus() == null || coupon.getStatus() != 1) {
                throw new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券已使用或状态不正确");
            }
            
            // 计算实际抵扣额：取订单待付金额和券面额的较小值
            // 此时 totalPaid 为 0，订单待付金额就是订单总金额
            BigDecimal pendingAmount = order.getPayAmount();
            BigDecimal actualDeduct = coupon.use(order.getId(), pendingAmount);
            
            couponRepository.save(coupon);
            
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(PaymentMethod.COUPON.getCode());
            payment.setAmount(actualDeduct);
            payment.setCouponId(couponId);
            payment.setCreatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            payments.add(payment);
            totalPaid = totalPaid.add(actualDeduct);
        }
        
        // 2. 专用金抵扣
        if (preview.getSpecialFundAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductSpecialFund(order.getCustomerId(), preview.getSpecialFundAmount(), 
                                           "ORDER", order.getId(), createdBy, "订单支付扣减专用金");
            
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(4);
            payment.setAmount(preview.getSpecialFundAmount());
            payment.setCreatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            payments.add(payment);
            totalPaid = totalPaid.add(preview.getSpecialFundAmount());
        }
        
        // 3. 本金余额抵扣
        if (preview.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductBalance(order.getCustomerId(), preview.getBalanceAmount(), 
                                       "ORDER", order.getId(), createdBy, "订单支付扣减本金余额");
            
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(3);
            payment.setAmount(preview.getBalanceAmount());
            payment.setCreatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            payments.add(payment);
            totalPaid = totalPaid.add(preview.getBalanceAmount());
        }
        
        // 4. 外部支付（微信/支付宝）- 这里只记录，实际支付由前端调起
        if (preview.getExternalAmount().compareTo(BigDecimal.ZERO) > 0) {
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(1); // 微信
            payment.setAmount(preview.getExternalAmount());
            payment.setCreatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            payments.add(payment);
            totalPaid = totalPaid.add(preview.getExternalAmount());
        }
        
        // 校验支付总额
        if (totalPaid.compareTo(order.getPayAmount()) != 0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 支付总额不匹配");
        }
        
        return payments;
    }
    
    /**
     * 退款（LIFO顺序：外部支付 -> 本金余额 -> 专用金 -> 优惠券）
     * 
     * @param order 订单
     * @param refundAmount 退款金额（为空则全额退款）
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void refund(Order order, BigDecimal refundAmount, Long createdBy) {
        // 赊销订单不支持退款
        if (order.getPaymentMethod() != null && order.getPaymentMethod().equals(PaymentMethod.CREDIT.getCode())) {
            throw new IllegalArgumentException(OrderErrorCode.CREDIT_ORDER_NO_REFUND + ": 赊销订单不支持退款");
        }
        
        if (refundAmount == null) {
            refundAmount = order.getPayAmount();
        }
        
        // 查询支付明细（按创建时间倒序，LIFO）
        List<OrderPayment> payments = paymentRepository.findByOrderId(order.getId());
        payments.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        
        BigDecimal remaining = refundAmount;
        
        for (OrderPayment payment : payments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal refund = payment.getAmount().compareTo(remaining) > 0 ? remaining : payment.getAmount();
            
            // 根据支付类型退款
            if (payment.getPaymentType().equals(PaymentMethod.WECHAT.getCode()) || payment.getPaymentType().equals(PaymentMethod.ALIPAY.getCode())) {
                // 外部支付：原路退回（TODO: 调用第三方退款接口）
                // 这里只记录，实际退款由第三方处理
            } else if (payment.getPaymentType().equals(PaymentMethod.BALANCE.getCode())) {
                // 本金余额：退回余额
                walletService.refundBalance(order.getCustomerId(), refund, "ORDER_REFUND", order.getId(), createdBy);
            } else if (payment.getPaymentType().equals(PaymentMethod.SPECIAL_FUND.getCode())) {
                // 专用金：退回专用金
                walletService.refundSpecialFund(order.getCustomerId(), refund, "ORDER_REFUND", order.getId(), createdBy);
            } else if (payment.getPaymentType().equals(PaymentMethod.COUPON.getCode())) {
                // 优惠券：视策略处理（通常不退）
                // TODO: 根据业务策略决定是否退回优惠券
            }
            
            remaining = remaining.subtract(refund);
        }
    }
}


