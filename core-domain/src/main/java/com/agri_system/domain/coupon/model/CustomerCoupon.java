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

    /** 类型：1-满减券，2-折扣券 */
    private Integer type;

    /** 面额/折扣率 */
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
     * 获取可用抵扣金额
     * - 满减券(type=1): 直接返回面额
     * - 折扣券(type=2): amount 表示"X折"，抵扣金额 = orderAmount × (1 - amount/10)
     *   例如 amount=8.5 表示 8.5折，抵扣 = orderAmount × 15%
     *
     * @param orderAmount 订单金额（仅折扣券需要）
     * @return 可抵扣金额
     */
    public BigDecimal getAvailableAmount(BigDecimal orderAmount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (type != null && type == 2 && orderAmount != null) {
            // 折扣券：抵扣金额 = 订单金额 × (1 - 折扣/10)
            // 例如 8.5折 → 抵扣 = orderAmount × (1 - 0.85) = orderAmount × 0.15
            BigDecimal discountRate = amount.divide(BigDecimal.TEN, 4, java.math.RoundingMode.HALF_UP);
            BigDecimal saveRate = BigDecimal.ONE.subtract(discountRate);
            return orderAmount.multiply(saveRate).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return amount;
    }

    /**
     * 获取可用金额（无参版本，仅适用于满减券）
     *
     * @return 面额
     */
    public BigDecimal getAvailableAmount() {
        return getAvailableAmount(null);
    }
    
    /**
     * 使用优惠券（一次性核销，不设找零）
     *
     * @param orderId 订单ID
     * @param pendingAmount 订单剩余待付金额
     * @return 实际抵扣金额
     */
    public BigDecimal use(Long orderId, BigDecimal pendingAmount) {
        // 校验状态：必须是未使用
        if (status == null || status != 1) {
            throw new IllegalArgumentException("优惠券状态不正确，无法使用");
        }

        // 校验金额合法性
        if (pendingAmount == null || pendingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("订单待付金额必须大于0");
        }

        // 计算实际抵扣额
        BigDecimal faceValue = getAvailableAmount(pendingAmount);
        BigDecimal actualDeduct = pendingAmount.min(faceValue);

        // 强制标记为已使用（一次性核销，不找零）
        status = 2; // 已使用
        usedOrderId = orderId;
        usedTime = LocalDateTime.now();

        return actualDeduct;
    }

    /**
     * 退款回退优惠券：将"已使用"的券恢复为"未使用"，使其可在后续订单复用。
     * <p>
     * 业务规则（与 {@code use} 对称的回退）：
     * <ul>
     *   <li>仅当当前状态为"已使用(2)"时才回退；其他状态（未使用/已作废）原样不动。</li>
     *   <li>若券已过期（endTime 早于当前时间），不回退 —— 保持已使用状态，客户无法再用。</li>
     *   <li>回退成功：状态置为"未使用(1)"，清空使用订单与使用时间。</li>
     * </ul>
     * 注意：是否触发回退由调用方按"是否全额退款"决定；本方法只负责状态与过期判定。
     *
     * @return true=已成功恢复为未使用；false=不满足回退条件（未改动）
     */
    public boolean restore() {
        if (status == null || status != 2) {
            return false; // 仅回退"已使用"的券
        }
        LocalDateTime now = LocalDateTime.now();
        if (endTime != null && now.isAfter(endTime)) {
            return false; // 已过期，不退回
        }
        status = 1; // 未使用
        usedOrderId = null;
        usedTime = null;
        return true;
    }
}


