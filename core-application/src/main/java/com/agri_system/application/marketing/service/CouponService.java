package com.agri_system.application.marketing.service;

import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠券服务
 * 负责优惠券发放、更新等业务逻辑
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class CouponService {
    
    private final CouponTemplateRepository couponTemplateRepository;
    private final CustomerCouponRepository couponRepository;
    
    /**
     * 发放优惠券（带校验）
     * 
     * @param templateId 优惠券模板ID
     * @param customerId 客户ID
     * @param count 发放数量
     * @throws IllegalArgumentException 如果模板停用或超发，抛出异常
     */
    @Transactional(readOnly = true)
    public void validateGrant(Long templateId, Long customerId, Integer count) {
        if (templateId == null || customerId == null || count == null || count <= 0) {
            throw new IllegalArgumentException("参数错误");
        }
        
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        
        // 校验1：状态检查
        if (template.getStatus() == null || template.getStatus() == 0) {
            throw new IllegalArgumentException("coupon-5001: 该优惠券模板已停用");
        }
        
        // 校验2：总量检查
        if (template.getTotalCount() != null && template.getTotalCount() != -1) {
            long currentCount = couponRepository.countByTemplateId(templateId);
            if (currentCount + count > template.getTotalCount()) {
                throw new IllegalArgumentException("coupon-5002: 优惠券发行量已达上限");
            }
        }
    }
    
    /**
     * 更新优惠券模板（带校验）
     * 
     * @param templateId 模板ID
     * @param newTotalCount 新的发行总量
     * @throws IllegalArgumentException 如果新总量小于已发放数量，抛出异常
     */
    @Transactional(readOnly = true)
    public void validateUpdateTotalCount(Long templateId, Integer newTotalCount) {
        if (templateId == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        
        // 如果新总量不是-1（不限），需要检查已发放数量
        if (newTotalCount != null && newTotalCount != -1) {
            long currentIssuedCount = couponRepository.countByTemplateId(templateId);
            if (newTotalCount < currentIssuedCount) {
                throw new IllegalArgumentException(
                    String.format("coupon-5003: 发行总量不能小于已发放数量(当前已发: %d张)", currentIssuedCount)
                );
            }
        }
    }
    
    /**
     * 校验优惠券模板是否可用（用于规则保存时）
     * 
     * @param templateId 模板ID
     * @throws IllegalArgumentException 如果模板停用，抛出异常
     */
    @Transactional(readOnly = true)
    public void validateTemplateEnabled(Long templateId) {
        if (templateId == null) {
            return; // 可选字段，允许为空
        }
        
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        
        if (template.getStatus() == null || template.getStatus() == 0) {
            throw new IllegalArgumentException("coupon-5001: 该优惠券模板已停用，无法用于营销规则");
        }
    }
}

