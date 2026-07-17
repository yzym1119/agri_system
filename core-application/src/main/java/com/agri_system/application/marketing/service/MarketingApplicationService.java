package com.agri_system.application.marketing.service;

import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.agri_system.domain.customer.model.MarketingVipRights;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.domain.customer.repository.MarketingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 营销应用服务
 *
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class MarketingApplicationService {
    
    private final MarketingRuleRepository marketingRuleRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponService couponService;
    
    /**
     * 查询所有充值规则
     */
    @Transactional(readOnly = true)
    public List<MarketingRechargeRule> listRechargeRules() {
        List<MarketingRechargeRule> rules = marketingRuleRepository.findAllRechargeRules();
        
        // 获取所有优惠券模板ID
        List<Long> templateIds = rules.stream()
                .filter(r -> r.getGiftTemplateId() != null)
                .map(MarketingRechargeRule::getGiftTemplateId)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询优惠券模板（包含名称和状态）
        Map<Long, CouponTemplate> templateMap = templateIds.stream()
                .map(couponTemplateRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(opt -> opt.get())
                .collect(Collectors.toMap(
                        CouponTemplate::getId,
                        template -> template,
                        (v1, v2) -> v1
                ));
        
        // 设置优惠券模板名称和状态（通过反射）
        rules.forEach(rule -> {
            if (rule.getGiftTemplateId() != null) {
                CouponTemplate template = templateMap.get(rule.getGiftTemplateId());
                if (template != null) {
                    try {
                        java.lang.reflect.Field nameField = MarketingRechargeRule.class.getDeclaredField("giftTemplateName");
                        nameField.setAccessible(true);
                        nameField.set(rule, template.getName());
                        
                        java.lang.reflect.Field statusField = MarketingRechargeRule.class.getDeclaredField("giftTemplateStatus");
                        statusField.setAccessible(true);
                        statusField.set(rule, template.getStatus());
                    } catch (Exception e) {
                        // 忽略反射异常
                    }
                }
            }
        });
        
        return rules;
    }
    
    /**
     * 创建或更新充值规则
     */
    @Transactional(rollbackFor = Exception.class)
    public MarketingRechargeRule saveRechargeRule(MarketingRechargeRule rule) {
        // 如果选择了优惠券模板，校验模板状态
        if (rule.getGiftTemplateId() != null) {
            couponService.validateTemplateEnabled(rule.getGiftTemplateId());
        }
        
        if (rule.getGiftDurationDays() == null && rule.getTargetVipLevel() != null) {
            rule.setGiftDurationDays(365); // 默认1年
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1); // 默认启用
        }
        if (rule.getSortOrder() == null) {
            rule.setSortOrder(0);
        }
        LocalDateTime now = LocalDateTime.now();
        if (rule.getId() == null) {
            // 新增
            rule.setCreatedAt(now);
        }
        rule.setUpdatedAt(now);
        return marketingRuleRepository.saveRechargeRule(rule);
    }
    
    /**
     * 删除充值规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRechargeRule(Long id) {
        MarketingRechargeRule rule = marketingRuleRepository.findRechargeRuleById(id)
                .orElseThrow(() -> new IllegalArgumentException("充值规则不存在"));
        if (rule.getStatus() != null && rule.getStatus() == 1) {
            throw new IllegalArgumentException("启用中的充值规则不允许直接删除，请先停用");
        }
        marketingRuleRepository.deleteRechargeRule(id);
    }
    
    /**
     * 切换充值规则状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleRechargeRuleStatus(Long id, Integer status) {
        MarketingRechargeRule rule = marketingRuleRepository.findRechargeRuleById(id)
                .orElseThrow(() -> new IllegalArgumentException("充值规则不存在"));
        rule.setStatus(status);
        rule.setUpdatedAt(LocalDateTime.now());
        marketingRuleRepository.saveRechargeRule(rule);
    }
    
    /**
     * 查询所有VIP权益
     */
    @Transactional(readOnly = true)
    public List<MarketingVipRights> listVipRights() {
        List<MarketingVipRights> rightsList = marketingRuleRepository.findAllVipRights();
        
        // 获取所有优惠券模板ID
        List<Long> templateIds = rightsList.stream()
                .filter(r -> r.getTemplateId() != null)
                .map(MarketingVipRights::getTemplateId)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询优惠券模板（包含名称和状态）
        Map<Long, CouponTemplate> templateMap = templateIds.stream()
                .map(couponTemplateRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(opt -> opt.get())
                .collect(Collectors.toMap(
                        CouponTemplate::getId,
                        template -> template,
                        (v1, v2) -> v1
                ));
        
        // 设置优惠券模板名称和状态（通过反射）
        rightsList.forEach(rights -> {
            if (rights.getTemplateId() != null) {
                CouponTemplate template = templateMap.get(rights.getTemplateId());
                if (template != null) {
                    try {
                        // 设置模板名称
                        java.lang.reflect.Field nameField = MarketingVipRights.class.getDeclaredField("templateName");
                        nameField.setAccessible(true);
                        nameField.set(rights, template.getName());
                        
                        // 设置模板状态
                        java.lang.reflect.Field statusField = MarketingVipRights.class.getDeclaredField("templateStatus");
                        statusField.setAccessible(true);
                        statusField.set(rights, template.getStatus());
                    } catch (Exception e) {
                        // 忽略反射异常
                    }
                }
            }
        });
        
        return rightsList;
    }
    
    /**
     * 根据VIP等级查询权益
     */
    @Transactional(readOnly = true)
    public List<MarketingVipRights> getVipRightsByLevel(Integer vipLevel) {
        return marketingRuleRepository.findVipRightsByLevel(vipLevel);
    }
    
    /**
     * 保存VIP权益（单条，兼容旧接口）
     */
    @Transactional(rollbackFor = Exception.class)
    public MarketingVipRights saveVipRights(MarketingVipRights rights) {
        // 如果选择了优惠券模板，校验模板状态
        if (rights.getTemplateId() != null) {
            couponService.validateTemplateEnabled(rights.getTemplateId());
        }
        
        if (rights.getStatus() == null) {
            rights.setStatus(1); // 默认启用
        }
        LocalDateTime now = LocalDateTime.now();
        if (rights.getId() == null) {
            // 新增
            rights.setCreatedAt(now);
        }
        rights.setUpdatedAt(now);
        return marketingRuleRepository.saveVipRights(rights);
    }
    
    /**
     * 批量保存VIP权益（支持一个等级对应多条权益）
     * 
     * @param vipLevel VIP等级
     * @param rightsList 权益列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveVipRightsBatch(Integer vipLevel, List<MarketingVipRights> rightsList) {
        // 校验所有优惠券模板状态
        for (MarketingVipRights rights : rightsList) {
            if (rights.getTemplateId() != null) {
                couponService.validateTemplateEnabled(rights.getTemplateId());
            }
        }
        
        // 设置默认值和时间戳
        LocalDateTime now = LocalDateTime.now();
        for (MarketingVipRights rights : rightsList) {
            rights.setVipLevel(vipLevel);
            if (rights.getStatus() == null) {
                rights.setStatus(1); // 默认启用
            }
            if (rights.getId() == null) {
                rights.setCreatedAt(now);
            }
            rights.setUpdatedAt(now);
        }
        
        // 删除旧记录并批量插入
        marketingRuleRepository.saveVipRightsBatch(vipLevel, rightsList);
    }
    
    /**
     * 删除VIP权益
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVipRights(Long id) {
        MarketingVipRights rights = marketingRuleRepository.findVipRightsById(id)
                .orElseThrow(() -> new IllegalArgumentException("VIP权益不存在"));
        if (rights.getStatus() != null && rights.getStatus() == 1) {
            throw new IllegalArgumentException("启用中的VIP权益不允许直接删除，请先停用");
        }
        marketingRuleRepository.deleteVipRights(id);
    }
}
