package com.agri_system.application.customer.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户优惠券VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "客户优惠券")
public class CustomerCouponVO {
    
    @Schema(description = "优惠券ID", example = "1")
    private Long id;
    
    @Schema(description = "客户ID", example = "1")
    private Long customerId;
    
    @Schema(description = "券名称", example = "化肥券")
    private String name;
    
    @Schema(description = "面额（元）", example = "100.00")
    private BigDecimal amount;
    
    @Schema(description = "使用门槛金额（元）", example = "200.00")
    private BigDecimal minOrderAmount;
    
    @Schema(description = "生效时间")
    private LocalDateTime startTime;
    
    @Schema(description = "过期时间")
    private LocalDateTime endTime;
    
    @Schema(description = "状态：1-未使用, 2-已使用, 3-已过期, 4-已作废", example = "1")
    private Integer status;
    
    @Schema(description = "限制商品分类ID", example = "1")
    private Long limitCategoryId;
    
    @Schema(description = "限制商品分类名称", example = "化肥")
    private String limitCategoryName;
    
    @Schema(description = "限制商品ID", example = "1")
    private Long limitProductId;
    
    @Schema(description = "使用时关联的订单ID", example = "1")
    private Long usedOrderId;
    
    @Schema(description = "使用时间")
    private LocalDateTime usedTime;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

