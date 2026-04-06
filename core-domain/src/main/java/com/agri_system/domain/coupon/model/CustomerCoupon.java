package com.agri_system.domain.coupon.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户优惠券实体
 * 
 * @author AI Generated
 */
@Data
@Table("customer_coupon")
public class CustomerCoupon {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 关联客户ID */
    private Long customerId;
    
    /** 来源模板ID */
    private Long templateId;
    
    /** 券名称 */
    private String name;
    
    /** 面额（元） */
    private BigDecimal amount;
    
    /** 使用门槛金额（元） */
    private BigDecimal minOrderAmount;
    
    /** 生效时间 */
    private LocalDateTime startTime;
    
    /** 过期时间 */
    private LocalDateTime endTime;
    
    /** 状态：1-未使用, 2-已使用, 3-已过期, 4-已作废 */
    private Integer status;
    
    /** 限制商品分类ID */
    private Long limitCategoryId;
    
    /** 限制具体商品ID */
    private Long limitProductId;
    
    /** 使用时关联的订单ID */
    private Long usedOrderId;
    
    /** 使用时间 */
    private LocalDateTime usedTime;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查优惠券是否可用
     * 
     * @param orderAmount 订单金额
     * @param productIds 订单商品ID列表（用于校验限制）
     * @param categoryIds 订单商品分类ID列表（用于校验限制）
     * @return 是否可用
     */
    public boolean isUsable(BigDecimal orderAmount, java.util.List<Long> productIds, java.util.List<Long> categoryIds) {
        // 检查状态
        if (status == null || status != 1) {
            return false;
        }
        
        // 检查时间
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return false;
        }
        
        // 检查门槛
        if (minOrderAmount != null && orderAmount != null && orderAmount.compareTo(minOrderAmount) < 0) {
            return false;
        }
        
        // 检查商品限制
        if (limitProductId != null) {
            if (productIds == null || !productIds.contains(limitProductId)) {
                return false;
            }
        }
        
        // 检查分类限制
        if (limitCategoryId != null) {
            if (categoryIds == null || !categoryIds.contains(limitCategoryId)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取可用金额（即面额，优惠券为一次性核销）
     * 
     * @return 可用金额（面额）
     */
    public BigDecimal getAvailableAmount() {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount;
    }
    
    /**
     * 使用优惠券（一次性核销，不设找零）
     * 
     * @param orderId 订单ID
     * @param pendingAmount 订单剩余待付金额
     * @return 实际抵扣金额（取订单待付金额和券面额的较小值）
     */
    public BigDecimal use(Long orderId, BigDecimal pendingAmount) {
        // 校验状态：必须是未使用
        if (status == null || status != 1) {
            throw new IllegalArgumentException("优惠券状态不正确，无法使用");
        }
        
        // 计算实际抵扣额：取订单待付金额和券面额的较小值
        BigDecimal actualDeduct = pendingAmount.min(amount);
        
        // 强制标记为已使用（一次性核销，不找零）
        status = 2; // 已使用
        usedOrderId = orderId;
        usedTime = LocalDateTime.now();
        
        return actualDeduct;
    }
}


