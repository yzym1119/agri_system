package com.agri_system.infrastructure.coupon.mapper;

import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户优惠券Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface CustomerCouponMapper extends BaseMapper<CustomerCoupon> {
    
    /**
     * 统计指定模板已发放的优惠券数量
     * 
     * @param templateId 模板ID
     * @return 已发放数量
     */
    @Select("SELECT COUNT(*) FROM customer_coupon WHERE template_id = #{templateId}")
    long countByTemplateId(@Param("templateId") Long templateId);
}



















