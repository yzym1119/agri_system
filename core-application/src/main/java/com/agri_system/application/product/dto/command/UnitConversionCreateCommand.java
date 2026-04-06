package com.agri_system.application.product.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建单位换算命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "创建单位换算命令")
public class UnitConversionCreateCommand {
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @NotBlank(message = "源单位不能为空")
    @Schema(description = "源单位", example = "袋")
    private String fromUnit;
    
    @NotBlank(message = "目标单位不能为空")
    @Schema(description = "目标单位", example = "箱")
    private String toUnit;
    
    @NotNull(message = "换算比例不能为空")
    @Positive(message = "换算比例必须大于0")
    @Schema(description = "换算比例（1源单位=？目标单位）", example = "20.0000")
    private BigDecimal conversionRatio;
}




