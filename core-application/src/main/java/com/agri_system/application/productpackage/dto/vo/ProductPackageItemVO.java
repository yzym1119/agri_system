package com.agri_system.application.productpackage.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 套餐明细VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "套餐明细VO")
public class ProductPackageItemVO {
    
    @Schema(description = "明细ID", example = "1")
    private Long id;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "商品名称", example = "复合肥料")
    private String productName;
    
    @Schema(description = "数量", example = "2.0000")
    private BigDecimal quantity;
    
    @Schema(description = "单位", example = "袋")
    private String unit;
    
    @Schema(description = "排序顺序", example = "0")
    private Integer sortOrder;
}










