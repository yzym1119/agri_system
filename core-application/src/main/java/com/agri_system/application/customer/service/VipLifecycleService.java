package com.agri_system.application.customer.service;

import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.*;
import com.agri_system.domain.customer.repository.*;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * VIP生命周期服务（降级、月度权益）
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class VipLifecycleService {
    
    private final CustomerRepository customerRepository;
    private final MarketingRuleRepository marketingRuleRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CustomerCouponRepository couponRepository;
    private final VipMonthlyRecordRepository monthlyRecordRepository;
    private final CustomerBenefitLogRepository benefitLogRepository;
    private final CustomerDomainService customerDomainService;
    private final CustomerWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final com.agri_system.application.marketing.service.CouponService couponService;
    
    /**
     * VIP每月权益发放（幂等）
     * 
     * @param customerId 客户ID
     * @param currentMonth 当前月份（格式：2023-10）
     */
    @Transactional(rollbackFor = Exception.class)
    public void grantMonthlyBenefit(Long customerId, String currentMonth) {
        // 幂等性检查：查询是否已发放
        if (monthlyRecordRepository.findByCustomerIdAndYearMonth(customerId, currentMonth).isPresent()) {
            return; // 已发放，跳过
        }
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        
        if (!customer.isVip() || customer.getVipLevel() == null) {
            return; // 不是VIP，跳过
        }
        
        if (customer.isVipExpired()) {
            return; // VIP已过期，跳过
        }
        
        // 查询VIP权益配置
        List<MarketingVipRights> rights = marketingRuleRepository.findVipRightsByLevel(customer.getVipLevel());
        
        // 构建权益内容描述
        StringBuilder benefitContentBuilder = new StringBuilder();
        
        for (MarketingVipRights right : rights) {
            try {
                if (right.isCouponBenefit() && right.getTemplateId() != null) {
                    // 送券
                    CouponTemplate template = couponTemplateRepository.findById(right.getTemplateId())
                            .orElse(null);
                    
                    if (template != null && template.isEnabled()) {
                        int count = right.getTemplateCount() != null ? right.getTemplateCount() : 1;
                        
                        // 校验发放（状态和总量）
                        try {
                            couponService.validateGrant(right.getTemplateId(), customerId, count);
                        } catch (IllegalArgumentException e) {
                            // 捕获coupon-5001或coupon-5002异常，记录日志并继续
                            String errorMsg = e.getMessage();
                            if (errorMsg != null && (errorMsg.contains("coupon-5001") || errorMsg.contains("coupon-5002"))) {
                                // 记录失败日志
                                CustomerBenefitLog failLog = new CustomerBenefitLog();
                                failLog.setCustomerId(customerId);
                                failLog.setSourceType(1); // 1-每月权益(自动)
                                failLog.setBenefitContent("权益发放失败：" + errorMsg);
                                failLog.setAssociatedId(right.getTemplateId());
                                failLog.setCreatedBy(0L); // 0为系统
                                failLog.setCreatedAt(LocalDateTime.now());
                                benefitLogRepository.save(failLog);
                                
                                // 继续执行下一个权益
                                continue;
                            } else {
                                // 其他异常继续抛出
                                throw e;
                            }
                        }
                        
                        // 发放优惠券
                        for (int i = 0; i < count; i++) {
                            CustomerCoupon coupon = customerDomainService.createCouponFromTemplate(
                                    template, customerId);
                            couponRepository.save(coupon);
                        }
                        
                        if (benefitContentBuilder.length() > 0) benefitContentBuilder.append("，");
                        benefitContentBuilder.append(template.getName()).append("×").append(count);
                    }
                } else if (right.isSpecialFundBenefit() && right.getBenefitValue() != null 
                        && right.getBenefitValue().compareTo(BigDecimal.ZERO) > 0) {
                    // 送专用金
                    CustomerWallet specialFundWallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(
                            customerId, 2)
                            .orElseGet(() -> {
                                CustomerWallet newWallet = new CustomerWallet();
                                newWallet.setCustomerId(customerId);
                                newWallet.setWalletType(2);
                                newWallet.setBalance(BigDecimal.ZERO);
                                newWallet.setFrozenBalance(BigDecimal.ZERO);
                                newWallet.setVersion(0);
                                return walletRepository.save(newWallet);
                            });
                    
                    BigDecimal balanceBefore = specialFundWallet.getBalance();
                    specialFundWallet.add(right.getBenefitValue());
                    walletRepository.save(specialFundWallet);
                    
                    // 记录流水
                    WalletTransaction transaction = new WalletTransaction();
                    transaction.setCustomerId(customerId);
                    transaction.setWalletType(2);
                    transaction.setTransactionType(4); // 赠送
                    transaction.setAmount(right.getBenefitValue());
                    transaction.setBalanceBefore(balanceBefore);
                    transaction.setBalanceAfter(specialFundWallet.getBalance());
                    transaction.setReferenceType("VIP_MONTHLY");
                    transaction.setReferenceId(null);
                    transaction.setRemark("VIP每月权益：赠送专用金");
                    transaction.setCreatedAt(LocalDateTime.now());
                    transaction.setCreatedBy(null);
                    transactionRepository.save(transaction);
                    
                    if (benefitContentBuilder.length() > 0) benefitContentBuilder.append("，");
                    benefitContentBuilder.append("专用金¥").append(right.getBenefitValue());
                }
            } catch (Exception e) {
                // 捕获其他异常，记录日志并继续
                org.slf4j.LoggerFactory.getLogger(VipLifecycleService.class)
                        .error("VIP月度权益发放失败，客户ID: {}, 权益ID: {}, 错误: {}", 
                                customerId, right.getId(), e.getMessage(), e);
                
                // 记录失败日志
                CustomerBenefitLog failLog = new CustomerBenefitLog();
                failLog.setCustomerId(customerId);
                failLog.setSourceType(1); // 1-每月权益(自动)
                failLog.setBenefitContent("权益发放失败：" + e.getMessage());
                failLog.setAssociatedId(right.getId());
                failLog.setCreatedBy(0L); // 0为系统
                failLog.setCreatedAt(LocalDateTime.now());
                benefitLogRepository.save(failLog);
                
                // 继续执行下一个权益，不影响其他权益和其他用户
                continue;
            }
        }
        
        // 双写：1. 写入 vip_monthly_record（幂等性保证）
        VipMonthlyRecord record = new VipMonthlyRecord();
        record.setCustomerId(customerId);
        record.setYearMonth(currentMonth);
        record.setVipLevel(customer.getVipLevel());
        record.setRecordType(1); // 1-系统自动发放（月度权益）
        record.setGrantTime(LocalDateTime.now());
        record.setContent(benefitContentBuilder.length() > 0 ? benefitContentBuilder.toString() : "系统自动发放");
        monthlyRecordRepository.save(record);
        
        // 双写：2. 写入 customer_benefit_log（流水记录供前端展示）
        if (benefitContentBuilder.length() > 0) {
            CustomerBenefitLog benefitLog = new CustomerBenefitLog();
            benefitLog.setCustomerId(customerId);
            benefitLog.setSourceType(1); // 1-每月权益(自动)
            benefitLog.setBenefitContent(benefitContentBuilder.toString());
            benefitLog.setAssociatedId(null);
            benefitLog.setCreatedBy(0L); // 0为系统
            benefitLog.setCreatedAt(LocalDateTime.now());
            benefitLogRepository.save(benefitLog);
        }
    }
    
    /**
     * VIP降级任务
     * 检查所有VIP客户，如果过期则降级为普通客户
     */
    @Transactional(rollbackFor = Exception.class)
    public void downgradeExpiredVips() {
        List<Customer> vipCustomers = customerRepository.findAllVipCustomers();
        int downgradeCount = 0;
        
        for (Customer customer : vipCustomers) {
            if (customer.isVipExpired()) {
                customer.downgradeToNormal();
                customerRepository.save(customer);
                downgradeCount++;
            }
        }
        
        if (downgradeCount > 0) {
            org.slf4j.LoggerFactory.getLogger(VipLifecycleService.class)
                    .info("VIP降级任务完成，共降级 {} 个客户", downgradeCount);
        }
    }
    
    /**
     * 获取当前月份字符串（格式：2023-10）
     * 
     * @return 月份字符串
     */
    public String getCurrentMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}



