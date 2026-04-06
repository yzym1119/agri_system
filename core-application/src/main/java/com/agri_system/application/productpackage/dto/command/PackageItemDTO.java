package com.agri_system.application.productpackage.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 套餐明细DTO（供items[]使用）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "套餐明细DTO")
public class PackageItemDTO {
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    @Schema(description = "数量（必须 > 0）", example = "2.0000")
    private BigDecimal quantity;
    
    @NotNull(message = "单位不能为空")
    @Schema(description = "单位", example = "袋")
    private String unit;
    
    @Schema(description = "排序顺序", example = "0")
    private Integer sortOrder;
}



