package com.agri_system.application.product.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 更新价格规则命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "更新价格规则命令")
public class PriceRuleUpdateCommand {
    
    @NotNull(message = "价格规则ID不能为空")
    @Schema(description = "价格规则ID", example = "1")
    private Long id;
    
    @PositiveOrZero(message = "价格必须大于等于0")
    @Schema(description = "价格（元）", example = "45.00")
    private BigDecimal price;
    
    @Schema(description = "生效日期", example = "2024-01-01")
    private LocalDate effectiveDate;
    
    @Schema(description = "失效日期", example = "2024-12-31")
    private LocalDate expiryDate;
    
    @Schema(description = "状态：1-启用，0-停用", example = "1")
    private Integer status;
}



