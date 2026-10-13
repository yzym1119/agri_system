package com.agri_system.domain.coupon.repository;

import com.agri_system.domain.coupon.model.CustomerCoupon;

import java.util.Optional;

/**
 * 客户优惠券仓储接口
 * 
 * @author AI Generated
 */
public interface CustomerCouponRepository {
    
    /**
     * 保存优惠券
     */
    CustomerCoupon save(CustomerCoupon coupon);
    
    /**
     * 根据ID查询优惠券
     */
    Optional<CustomerCoupon> findById(Long id);

    /** 核销在事务内锁定当前券状态，避免不同订单并发使用同一张券。 */
    Optional<CustomerCoupon> findByIdForUpdate(Long id);
    
    /**
     * 分页查询客户优惠券
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param customerId 客户ID
     * @param status 状态（可选）
     * @return 分页结果
     */
    com.mybatisflex.core.paginate.Page<CustomerCoupon> findPage(Integer pageNum, Integer pageSize, Long customerId, Integer status);
    
    /**
     * 统计指定模板已发放的优惠券数量
     * 
     * @param templateId 模板ID
     * @return 已发放数量
     */
    long countByTemplateId(Long templateId);
}





























