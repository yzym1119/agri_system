package com.agri_system.application.customer.service;

import com.agri_system.application.customer.dto.RechargeCommand;
import com.agri_system.application.customer.dto.RechargeResultVO;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.repository.MarketingRuleRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 钱包充值服务（充值引擎）
 * 
 * @author AI Generated
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletRechargeService {
    
    private final CustomerRepository customerRepository;
    private final CustomerWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final MarketingRuleRepository marketingRuleRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CustomerCouponRepository couponRepository;
    private final CustomerDomainService customerDomainService;
    private final CustomerBenefitLogRepository benefitLogRepository;
    
    /**
     * 充值（充值引擎）
     * 
     * @param command 充值命令
     * @return 充值结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeResultVO recharge(RechargeCommand command) {
        // 1. 查询客户（带锁）
        Customer customer = customerRepository.findByIdWithLock(command.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        
        // 2. 入账：本金钱包增加
        CustomerWallet balanceWallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(
                command.getCustomerId(), 1)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(command.getCustomerId());
                    newWallet.setWalletType(1);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = balanceWallet.getBalance();
        balanceWallet.add(command.getAmount());
        walletRepository.save(balanceWallet);
        
        // 记录充值流水
        recordTransaction(command.getCustomerId(), 1, 1, command.getAmount(),
                balanceBefore, balanceWallet.getBalance(),
                "RECHARGE", null, command.getCreatedBy(), "充值", command.getPaymentMethod());
        
        RechargeResultVO result = new RechargeResultVO();
        result.setRechargeAmount(command.getAmount());
        result.setLatestBalance(balanceWallet.getBalance());
        
        // 3. 两步走策略：匹配充值规则
        // 第一步：锁定最高档位
        BigDecimal targetThreshold = marketingRuleRepository.findMaxMatchingThreshold(command.getAmount());
        
        if (targetThreshold == null) {
            // 未达到最低充值要求，不赠送任何权益，直接返回
            log.info("客户[{}]充值{}，未达到任何充值门槛，仅入账本金", command.getCustomerId(), command.getAmount());
            result.setLatestVipLevel(customer.getVipLevel());
            result.setVipEndDate(customer.getVipEndDate());
            result.setGrantedCoupons(new ArrayList<>());
            customerRepository.save(customer);
            return result;
        }
        
        // 第二步：获取该档位所有规则
        List<MarketingRechargeRule> rules = marketingRuleRepository.findRulesByThreshold(targetThreshold);
        
        if (rules.isEmpty()) {
            log.warn("客户[{}]充值{}，匹配到门槛{}，但未找到启用规则", 
                    command.getCustomerId(), command.getAmount(), targetThreshold);
            result.setLatestVipLevel(customer.getVipLevel());
            result.setVipEndDate(customer.getVipEndDate());
            result.setGrantedCoupons(new ArrayList<>());
            customerRepository.save(customer);
            return result;
        }
        
        // 记录规则ID列表用于日志
        List<Long> ruleIds = rules.stream().map(MarketingRechargeRule::getId).collect(Collectors.toList());
        log.info("客户[{}]充值{}，匹配档位{}，命中规则ID {}", 
                command.getCustomerId(), command.getAmount(), targetThreshold, ruleIds);
        
        // 4. 权益聚合执行
        List<RechargeResultVO.CouponInfo> grantedCoupons = new ArrayList<>();
        StringBuilder logContent = new StringBuilder();
        
        // 4.1 聚合专用金（求和）
        BigDecimal totalGiftSpecialFund = rules.stream()
                .filter(r -> r.getGiftSpecialFund() != null && r.getGiftSpecialFund().compareTo(BigDecimal.ZERO) > 0)
                .map(MarketingRechargeRule::getGiftSpecialFund)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalGiftSpecialFund.compareTo(BigDecimal.ZERO) > 0) {
            CustomerWallet specialFundWallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(
                    command.getCustomerId(), 2)
                    .orElseGet(() -> {
                        CustomerWallet newWallet = new CustomerWallet();
                        newWallet.setCustomerId(command.getCustomerId());
                        newWallet.setWalletType(2);
                        newWallet.setBalance(BigDecimal.ZERO);
                        newWallet.setFrozenBalance(BigDecimal.ZERO);
                        newWallet.setVersion(0);
                        return walletRepository.save(newWallet);
                    });
            
            BigDecimal specialFundBefore = specialFundWallet.getBalance();
            specialFundWallet.add(totalGiftSpecialFund);
            walletRepository.save(specialFundWallet);
            
            recordTransaction(command.getCustomerId(), 2, 4, totalGiftSpecialFund,
                    specialFundBefore, specialFundWallet.getBalance(),
                    "RECHARGE_GIFT", null, command.getCreatedBy(), "充值活动赠送(叠加)");
            
            result.setLatestSpecialFund(specialFundWallet.getBalance());
            if (logContent.length() > 0) logContent.append(" + ");
            logContent.append(totalGiftSpecialFund).append("元");
        }
        
        // 4.2 聚合优惠券（遍历发放）
        for (MarketingRechargeRule rule : rules) {
            if (rule.getGiftTemplateId() != null) {
                CouponTemplate template = couponTemplateRepository.findById(rule.getGiftTemplateId())
                        .orElse(null);
                
                if (template != null && template.isEnabled()) {
                    int count = rule.getGiftTemplateCount() != null ? rule.getGiftTemplateCount() : 1;
                    for (int i = 0; i < count; i++) {
                        CustomerCoupon coupon = customerDomainService.createCouponFromTemplate(
                                template, command.getCustomerId());
                        coupon = couponRepository.save(coupon);
                        
                        RechargeResultVO.CouponInfo couponInfo = new RechargeResultVO.CouponInfo();
                        couponInfo.setCouponId(coupon.getId());
                        couponInfo.setName(coupon.getName());
                        couponInfo.setAmount(coupon.getAmount());
                        grantedCoupons.add(couponInfo);
                    }
                    
                    if (logContent.length() > 0) logContent.append(" + ");
                    logContent.append("[").append(template.getName()).append("]×").append(count);
                }
            }
        }
        
        // 4.3 聚合VIP等级（取最大值）
        Integer maxVipLevel = rules.stream()
                .map(MarketingRechargeRule::getTargetVipLevel)
                .filter(level -> level != null)
                .max(Integer::compareTo)
                .orElse(null);
        
        // 4.4 聚合VIP时长（取最大值）
        Integer maxDurationDays = rules.stream()
                .map(MarketingRechargeRule::getGiftDurationDays)
                .filter(days -> days != null)
                .max(Integer::compareTo)
                .orElse(365); // 默认1年
        
        // 应用VIP变更
        if (maxVipLevel != null) {
            // 关键修复：如果当前是普通用户，强制认为当前等级为0
            int currentLevel = (customer.getType() == 1 || customer.getVipLevel() == null) 
                               ? 0 
                               : customer.getVipLevel();
            
            // 保级逻辑：取当前等级和目标等级的最大值
            int newLevel = Math.max(currentLevel, maxVipLevel);
            customer.setVipLevel(newLevel);
            customer.upgradeToVip(newLevel, maxDurationDays);
            if (logContent.length() > 0) logContent.append(" + ");
            logContent.append("VIP").append(newLevel);
        } else if (maxDurationDays != null && customer.isVip()) {
            // 如果没有目标VIP等级，但客户已经是VIP，只延长有效期
            LocalDate now = LocalDate.now();
            if (customer.getVipEndDate() == null || customer.getVipEndDate().isBefore(now)) {
                customer.setVipEndDate(now.plusDays(maxDurationDays));
            } else {
                customer.setVipEndDate(customer.getVipEndDate().plusDays(maxDurationDays));
            }
        }
        
        // 4.5 记录充值奖励发放到权益流水表
        if (maxVipLevel != null || !grantedCoupons.isEmpty() || totalGiftSpecialFund.compareTo(BigDecimal.ZERO) > 0) {
            // 构建本次充值奖励内容
            StringBuilder contentBuilder = new StringBuilder();
            if (maxVipLevel != null) {
                contentBuilder.append("升级至VIP").append(maxVipLevel);
            }
            if (totalGiftSpecialFund.compareTo(BigDecimal.ZERO) > 0) {
                if (contentBuilder.length() > 0) contentBuilder.append("，");
                contentBuilder.append("赠送专用金¥").append(totalGiftSpecialFund);
            }
            if (!grantedCoupons.isEmpty()) {
                if (contentBuilder.length() > 0) contentBuilder.append("，");
                // 按券名分组统计
                String couponSummary = grantedCoupons.stream()
                        .collect(Collectors.groupingBy(
                                RechargeResultVO.CouponInfo::getName,
                                Collectors.counting()
                        ))
                        .entrySet().stream()
                        .map(e -> e.getKey() + "×" + e.getValue())
                        .collect(Collectors.joining("、"));
                contentBuilder.append("赠送").append(couponSummary);
            }
            
            // 获取关联ID（使用第一个规则的ID，如果有多个规则则使用第一个）
            Long associatedId = !rules.isEmpty() ? rules.get(0).getId() : null;
            
            // 写入权益流水表
            CustomerBenefitLog benefitLog = new CustomerBenefitLog();
            benefitLog.setCustomerId(command.getCustomerId());
            benefitLog.setSourceType(2); // 2-充值赠送
            benefitLog.setBenefitContent(contentBuilder.toString());
            benefitLog.setAssociatedId(associatedId);
            benefitLog.setCreatedBy(command.getCreatedBy());
            benefitLog.setCreatedAt(LocalDateTime.now());
            benefitLogRepository.save(benefitLog);
        }
        
        // 记录操作日志
        log.info("用户充值{}，匹配档位{}，命中规则ID {}，共赠送{}", 
                command.getAmount(), targetThreshold, ruleIds, 
                logContent.length() > 0 ? logContent.toString() : "无");
        
        customerRepository.save(customer);
        
        result.setLatestVipLevel(customer.getVipLevel());
        result.setVipEndDate(customer.getVipEndDate());
        result.setGrantedCoupons(grantedCoupons);
        
        return result;
    }
    
    private void recordTransaction(Long customerId, Integer walletType, Integer transactionType,
                                   BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   String referenceType, Long referenceId, Long createdBy, String remark) {
        recordTransaction(customerId, walletType, transactionType, amount, balanceBefore, balanceAfter,
                referenceType, referenceId, createdBy, remark, null);
    }
    
    private void recordTransaction(Long customerId, Integer walletType, Integer transactionType,
                                   BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   String referenceType, Long referenceId, Long createdBy, String remark,
                                   Integer paymentMethod) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomerId(customerId);
        transaction.setWalletType(walletType);
        transaction.setTransactionType(transactionType);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setRemark(remark);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(createdBy);
        transactionRepository.save(transaction);
    }
}






