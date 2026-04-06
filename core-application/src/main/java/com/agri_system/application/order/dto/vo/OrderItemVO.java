package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单明细")
public class OrderItemVO {
    
    @Schema(description = "明细ID", example = "1")
    private Long id;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "套餐ID", example = "1")
    private Long packageId;
    
    @Schema(description = "明细类型：1-商品，2-套餐", example = "1")
    private Integer itemType;
    
    @Schema(description = "商品/套餐名称", example = "复合肥")
    private String productName;
    
    @Schema(description = "数量", example = "10.0000")
    private BigDecimal quantity;
    
    @Schema(description = "单位", example = "袋")
    private String unit;
    
    @Schema(description = "规格", example = "50kg")
    private String specification;
    
    @Schema(description = "厂家/品牌", example = "XX化工")
    private String manufacturer;
    @Schema(description = "单价", example = "50.00")
    private BigDecimal unitPrice;
    
    @Schema(description = "小计金额", example = "500.00")
    private BigDecimal totalPrice;
}








