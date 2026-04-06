package com.agri_system.domain.customer.repository;

import com.agri_system.domain.customer.model.CouponTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 优惠券模板仓储接口
 * 
 * @author AI Generated
 */
public interface CouponTemplateRepository {
    
    /**
     * 保存模板
     */
    CouponTemplate save(CouponTemplate template);
    
    /**
     * 根据ID查询模板
     */
    Optional<CouponTemplate> findById(Long id);
    
    /**
     * 查询所有启用的模板
     */
    List<CouponTemplate> findAllEnabled();
    
    /**
     * 分页查询模板
     */
    com.mybatisflex.core.paginate.Page<CouponTemplate> findPage(Integer pageNum, Integer pageSize, String name, Integer status);
    
    /**
     * 删除模板
     */
    void deleteById(Long id);
}







