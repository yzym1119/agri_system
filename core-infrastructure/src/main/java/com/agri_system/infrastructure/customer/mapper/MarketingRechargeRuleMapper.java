package com.agri_system.infrastructure.customer.mapper;

import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值营销规则Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface MarketingRechargeRuleMapper extends BaseMapper<MarketingRechargeRule> {
    
    /**
     * 查找满足充值金额的最大门槛金额
     * 
     * @param rechargeAmount 充值金额
     * @return 最大门槛金额，如果为null说明未达到最低充值要求
     */
    @Select("SELECT MAX(min_recharge_amount) FROM marketing_recharge_rule " +
            "WHERE min_recharge_amount <= #{rechargeAmount} AND status = 1")
    BigDecimal selectMaxThreshold(@Param("rechargeAmount") BigDecimal rechargeAmount);
    
    /**
     * 强制更新充值规则（包括 null 值和 0 值字段）
     * 用于处理需要清空字段或设置值为 0 的情况
     */
    @Update("UPDATE marketing_recharge_rule SET " +
            "name = #{name}, " +
            "min_recharge_amount = #{minRechargeAmount}, " +
            "target_vip_level = #{targetVipLevel}, " +
            "gift_duration_days = #{giftDurationDays}, " +
            "gift_special_fund = #{giftSpecialFund}, " +
            "gift_template_id = #{giftTemplateId}, " +
            "gift_template_count = #{giftTemplateCount}, " +
            "status = #{status}, " +
            "sort_order = #{sortOrder}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int updateForce(@Param("id") Long id,
                    @Param("name") String name,
                    @Param("minRechargeAmount") BigDecimal minRechargeAmount,
                    @Param("targetVipLevel") Integer targetVipLevel,
                    @Param("giftDurationDays") Integer giftDurationDays,
                    @Param("giftSpecialFund") BigDecimal giftSpecialFund,
                    @Param("giftTemplateId") Long giftTemplateId,
                    @Param("giftTemplateCount") Integer giftTemplateCount,
                    @Param("status") Integer status,
                    @Param("sortOrder") Integer sortOrder,
                    @Param("updatedAt") LocalDateTime updatedAt);
}







