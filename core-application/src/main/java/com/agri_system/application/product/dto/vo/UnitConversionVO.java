package com.agri_system.application.product.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单位换算视图对象
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "单位换算视图对象")
public class UnitConversionVO {
    
    @Schema(description = "单位换算ID", example = "1")
    private Long id;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "源单位", example = "袋")
    private String fromUnit;
    
    @Schema(description = "目标单位", example = "箱")
    private String toUnit;
    
    @Schema(description = "换算比例（1源单位=？目标单位）", example = "20.0000")
    private BigDecimal conversionRatio;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}











