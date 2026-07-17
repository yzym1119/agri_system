package com.agri_system.domain.customer.service;

import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户领域服务
 * 
 * @author AI Generated
 */
public class CustomerDomainService {
    
    private final CustomerWalletRepository walletRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final OrderRepository orderRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final PointsExchangeOrderRepository pointsExchangeOrderRepository;
    
    public CustomerDomainService(CustomerWalletRepository walletRepository, 
                                 CreditAccountRepository creditAccountRepository,
                                 OrderRepository orderRepository,
                                 AfterSalesRepository afterSalesRepository,
                                 PointsExchangeOrderRepository pointsExchangeOrderRepository) {
        this.walletRepository = walletRepository;
        this.creditAccountRepository = creditAccountRepository;
        this.orderRepository = orderRepository;
        this.afterSalesRepository = afterSalesRepository;
        this.pointsExchangeOrderRepository = pointsExchangeOrderRepository;
    }
    
    /**
     * 从模板生成客户优惠券
     * 
     * @param template 优惠券模板
     * @param customerId 客户ID
     * @return 客户优惠券实例
     */
    public CustomerCoupon createCouponFromTemplate(CouponTemplate template, Long customerId) {
        if (template == null || !template.isEnabled()) {
            throw new IllegalArgumentException("优惠券模板不存在或已停用");
        }
        
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setCustomerId(customerId);
        coupon.setTemplateId(template.getId());
        coupon.setName(template.getName());
        coupon.setType(template.getType());
        coupon.setAmount(template.getAmount());
        coupon.setMinOrderAmount(template.getMinOrderAmount());
        coupon.setLimitCategoryId(template.getLimitCategoryId());
        coupon.setLimitProductId(template.getLimitProductId());
        coupon.setStatus(1); // 未使用
        coupon.setCreatedAt(LocalDateTime.now());
        coupon.setUpdatedAt(LocalDateTime.now());
        
        // 处理有效期
        if (template.getValidityType() == 1) {
            // 固定日期范围：完全复制模板的起止时间
            coupon.setStartTime(template.getValidStartTime());
            coupon.setEndTime(template.getValidEndTime());
        } else if (template.getValidityType() == 2) {
            // 领取后有效天数：从当前时间开始计算
            LocalDateTime now = LocalDateTime.now();
            coupon.setStartTime(now);
            if (template.getValidDays() != null) {
                coupon.setEndTime(now.plusDays(template.getValidDays()));
            }
        }
        
        return coupon;
    }
    
    /**
     * 计算积分（向下取整）
     * 
     * @param payAmount 支付金额
     * @return 积分
     */
    public Integer calculatePoints(java.math.BigDecimal payAmount) {
        if (payAmount == null || payAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return 0;
        }
        // 向下取整：FLOOR(pay_amount)
        return payAmount.intValue();
    }
    
    /**
     * 验证客户是否可以删除
     * 
     * @param customerId 客户ID
     * @throws IllegalArgumentException 如果客户有余额或欠款，抛出异常
     */
    public void validateDelete(Long customerId) {
        // 检查钱包余额（本金和专用金）
        walletRepository.findByCustomerIdAndWalletType(customerId, 1)
                .ifPresent(wallet -> {
                    BigDecimal balance = wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
                    BigDecimal frozenBalance = wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO;
                    if (balance.compareTo(BigDecimal.ZERO) > 0 || frozenBalance.compareTo(BigDecimal.ZERO) > 0) {
                        throw new IllegalArgumentException("customer-2001: 客户账户仍有余额，禁止删除");
                    }
                });
        
        walletRepository.findByCustomerIdAndWalletType(customerId, 2)
                .ifPresent(wallet -> {
                    BigDecimal balance = wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
                    BigDecimal frozenBalance = wallet.getFrozenBalance() != null ? wallet.getFrozenBalance() : BigDecimal.ZERO;
                    if (balance.compareTo(BigDecimal.ZERO) > 0 || frozenBalance.compareTo(BigDecimal.ZERO) > 0) {
                        throw new IllegalArgumentException("customer-2001: 客户账户仍有余额，禁止删除");
                    }
                });
        
        // 检查赊销欠款
        List<com.agri_system.domain.credit.model.CreditAccount> outstandingAccounts = 
                creditAccountRepository.findOutstandingAccounts(customerId);
        if (outstandingAccounts != null && !outstandingAccounts.isEmpty()) {
            // 检查是否有未还清的记录（status = 1）
            boolean hasUnpaidDebt = outstandingAccounts.stream()
                    .anyMatch(account -> account.getStatus() != null && account.getStatus() == 1);
            if (hasUnpaidDebt) {
                throw new IllegalArgumentException("customer-2002: 客户存在未结赊销款，禁止删除");
            }
        }

        long activeOrderCount = orderRepository.countActiveOrdersByCustomerId(customerId);
        if (activeOrderCount > 0) {
            throw new IllegalArgumentException("customer-2003: 客户存在进行中的订单，禁止删除");
        }

        long activeAfterSalesCount = afterSalesRepository.countActiveByCustomerId(customerId);
        if (activeAfterSalesCount > 0) {
            throw new IllegalArgumentException("customer-2004: 客户存在处理中的售后单，禁止删除");
        }

        long activePointsExchangeCount = pointsExchangeOrderRepository.countActiveByCustomerId(customerId);
        if (activePointsExchangeCount > 0) {
            throw new IllegalArgumentException("customer-2005: 客户存在未完成的积分兑换单，禁止删除");
        }
    }
}
