package com.agri_system.infrastructure.customer.mapper;

import com.agri_system.domain.customer.model.MarketingVipRights;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP周期权益Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface MarketingVipRightsMapper extends BaseMapper<MarketingVipRights> {
    
    /**
     * 强制更新VIP权益（包括 null 值和 0 值字段）
     * 用于处理需要清空字段或设置值为 0 的情况
     */
    @Update("UPDATE marketing_vip_rights SET " +
            "vip_level = #{vipLevel}, " +
            "benefit_type = #{benefitType}, " +
            "benefit_value = #{benefitValue}, " +
            "template_id = #{templateId}, " +
            "template_count = #{templateCount}, " +
            "status = #{status}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int updateForce(@Param("id") Long id,
                    @Param("vipLevel") Integer vipLevel,
                    @Param("benefitType") Integer benefitType,
                    @Param("benefitValue") BigDecimal benefitValue,
                    @Param("templateId") Long templateId,
                    @Param("templateCount") Integer templateCount,
                    @Param("status") Integer status,
                    @Param("updatedAt") LocalDateTime updatedAt);
}







