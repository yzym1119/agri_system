package com.agri_system.domain.customer.repository;

import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.agri_system.domain.customer.model.MarketingVipRights;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营销规则仓储接口
 * 
 * @author AI Generated
 */
public interface MarketingRuleRepository {
    
    /**
     * 根据充值金额倒序匹配充值规则（匹配满足门槛的规则，按优先级倒序）
     * 
     * @param rechargeAmount 充值金额
     * @return 匹配的规则列表（按sortOrder倒序）
     * @deprecated 使用 findMaxMatchingThreshold 和 findRulesByThreshold 替代
     */
    @Deprecated
    List<MarketingRechargeRule> findMatchingRechargeRules(BigDecimal rechargeAmount);
    
    /**
     * 查找满足充值金额的最大门槛金额（第一步：锁定最高档位）
     * 
     * @param rechargeAmount 充值金额
     * @return 最大门槛金额，如果为null说明未达到最低充值要求
     */
    BigDecimal findMaxMatchingThreshold(BigDecimal rechargeAmount);
    
    /**
     * 根据门槛金额查询所有规则（第二步：获取该档位所有规则）
     * 
     * @param thresholdAmount 门槛金额（必须严格等于）
     * @return 该门槛的所有启用规则列表
     */
    List<MarketingRechargeRule> findRulesByThreshold(BigDecimal thresholdAmount);
    
    /**
     * 根据VIP等级查询权益配置
     * 
     * @param vipLevel VIP等级
     * @return 权益配置列表
     */
    List<MarketingVipRights> findVipRightsByLevel(Integer vipLevel);
    
    /**
     * 保存充值规则
     */
    MarketingRechargeRule saveRechargeRule(MarketingRechargeRule rule);
    
    /**
     * 保存VIP权益（单条）
     */
    MarketingVipRights saveVipRights(MarketingVipRights rights);
    
    /**
     * 批量保存VIP权益（删除旧记录+批量插入）
     * 
     * @param vipLevel VIP等级
     * @param rightsList 权益列表
     */
    void saveVipRightsBatch(Integer vipLevel, List<MarketingVipRights> rightsList);
    
    /**
     * 查询所有充值规则
     */
    List<MarketingRechargeRule> findAllRechargeRules();
    
    /**
     * 查询所有VIP权益
     */
    List<MarketingVipRights> findAllVipRights();
    
    /**
     * 根据ID查询充值规则
     */
    java.util.Optional<MarketingRechargeRule> findRechargeRuleById(Long id);
    
    /**
     * 根据ID查询VIP权益
     */
    java.util.Optional<MarketingVipRights> findVipRightsById(Long id);
    
    /**
     * 删除充值规则
     */
    void deleteRechargeRule(Long id);
    
    /**
     * 删除VIP权益
     */
    void deleteVipRights(Long id);
}







