package com.agri_system.infrastructure.customer.repository;

import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.agri_system.domain.customer.model.MarketingVipRights;
import com.agri_system.domain.customer.repository.MarketingRuleRepository;
import com.agri_system.infrastructure.customer.mapper.MarketingRechargeRuleMapper;
import com.agri_system.infrastructure.customer.mapper.MarketingVipRightsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营销规则仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class MarketingRuleRepositoryImpl implements MarketingRuleRepository {
    
    private final MarketingRechargeRuleMapper rechargeRuleMapper;
    private final MarketingVipRightsMapper vipRightsMapper;
    
    @Override
    @Deprecated
    public List<MarketingRechargeRule> findMatchingRechargeRules(BigDecimal rechargeAmount) {
        // 查询满足门槛且启用的规则，按优先级倒序（sort_order ASC，但取满足条件的）
        // 注意：此方法已过时，建议使用 findMaxMatchingThreshold 和 findRulesByThreshold
        return rechargeRuleMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("status = ?", 1)
                        .and("min_recharge_amount <= ?", rechargeAmount)
                        .orderBy("sort_order", true) // 升序，数字越小优先级越高
        );
    }
    
    @Override
    public BigDecimal findMaxMatchingThreshold(BigDecimal rechargeAmount) {
        // 查询满足条件的最大门槛金额
        // SQL: SELECT MAX(min_recharge_amount) FROM marketing_recharge_rule 
        //      WHERE min_recharge_amount <= ? AND status = 1
        return rechargeRuleMapper.selectMaxThreshold(rechargeAmount);
    }
    
    @Override
    public List<MarketingRechargeRule> findRulesByThreshold(BigDecimal thresholdAmount) {
        // 查询所有门槛严格等于该金额的规则
        // SQL: SELECT * FROM marketing_recharge_rule 
        //      WHERE min_recharge_amount = ? AND status = 1
        return rechargeRuleMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("status = ?", 1)
                        .and("min_recharge_amount = ?", thresholdAmount)
                        .orderBy("sort_order", true) // 按优先级排序
        );
    }
    
    @Override
    public List<MarketingVipRights> findVipRightsByLevel(Integer vipLevel) {
        return vipRightsMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("vip_level = ?", vipLevel)
                        .and("status = ?", 1)
        );
    }
    
    @Override
    public MarketingRechargeRule saveRechargeRule(MarketingRechargeRule rule) {
        if (rule.getId() == null) {
            rechargeRuleMapper.insert(rule);
        } else {
            // 使用 updateForce 强制更新，包括 null 值
            rechargeRuleMapper.updateForce(
                    rule.getId(),
                    rule.getName(),
                    rule.getMinRechargeAmount(),
                    rule.getTargetVipLevel(),
                    rule.getGiftDurationDays(),
                    rule.getGiftSpecialFund(),
                    rule.getGiftTemplateId(), // 支持 null 值
                    rule.getGiftTemplateCount(),
                    rule.getStatus(),
                    rule.getSortOrder(),
                    rule.getUpdatedAt()
            );
        }
        return rule;
    }
    
    @Override
    public MarketingVipRights saveVipRights(MarketingVipRights rights) {
        if (rights.getId() == null) {
            vipRightsMapper.insert(rights);
        } else {
            // 使用 updateForce 强制更新，包括 null 值
            vipRightsMapper.updateForce(
                    rights.getId(),
                    rights.getVipLevel(),
                    rights.getBenefitType(),
                    rights.getBenefitValue(),
                    rights.getTemplateId(), // 支持 null 值
                    rights.getTemplateCount(),
                    rights.getStatus(),
                    rights.getUpdatedAt()
            );
        }
        return rights;
    }
    
    @Override
    public List<MarketingRechargeRule> findAllRechargeRules() {
        return rechargeRuleMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .orderBy("sort_order ASC, created_at DESC")
        );
    }
    
    @Override
    public List<MarketingVipRights> findAllVipRights() {
        return vipRightsMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .orderBy("vip_level ASC, created_at DESC")
        );
    }
    
    @Override
    public java.util.Optional<MarketingRechargeRule> findRechargeRuleById(Long id) {
        return java.util.Optional.ofNullable(rechargeRuleMapper.selectOneById(id));
    }
    
    @Override
    public java.util.Optional<MarketingVipRights> findVipRightsById(Long id) {
        return java.util.Optional.ofNullable(vipRightsMapper.selectOneById(id));
    }
    
    @Override
    public void deleteRechargeRule(Long id) {
        rechargeRuleMapper.deleteById(id);
    }
    
    @Override
    public void deleteVipRights(Long id) {
        vipRightsMapper.deleteById(id);
    }
    
    @Override
    public void saveVipRightsBatch(Integer vipLevel, List<MarketingVipRights> rightsList) {
        // 1. 删除该VIP等级下的所有旧记录
        vipRightsMapper.deleteByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("vip_level = ?", vipLevel)
        );
        
        // 2. 批量插入新记录
        if (rightsList != null && !rightsList.isEmpty()) {
            for (MarketingVipRights rights : rightsList) {
                vipRightsMapper.insert(rights);
            }
        }
    }
}







