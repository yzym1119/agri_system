package com.agri_system.application.productpackage.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新套餐命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "更新套餐命令")
public class ProductPackageUpdateCommand {
    
    @NotNull(message = "套餐ID不能为空")
    @Schema(description = "套餐ID", example = "1")
    private Long id;
    
    @NotBlank(message = "套餐名称不能为空")
    @Schema(description = "套餐名称", example = "春季作物解决方案")
    private String name;
    
    @NotNull(message = "零售价不能为空")
    @PositiveOrZero(message = "零售价必须大于等于0")
    @Schema(description = "零售价（必填）", example = "200.00")
    private BigDecimal suggestedRetailPrice;
    
    @PositiveOrZero(message = "VIP价必须大于等于0")
    @Schema(description = "VIP价（可空，null=不启用VIP价）", example = "180.00")
    private BigDecimal vipPrice;
    
    @PositiveOrZero(message = "最低售价必须大于等于0")
    @Schema(description = "最低售价", example = "150.00")
    private BigDecimal minPrice;
    
    @NotEmpty(message = "套餐明细不能为空")
    @Valid
    @Schema(description = "套餐明细列表（全量替换）")
    private List<PackageItemDTO> items;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;
}



