package com.agri_system.application.purchase.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购明细DTO
 *
 * @author AI Generated
 */
@Data
@Schema(description = "采购明细")
public class PurchaseItemDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于0")
    @Schema(description = "数量", example = "100.0000")
    private BigDecimal qty;

    @NotBlank(message = "单位不能为空")
    @Schema(description = "单位", example = "kg")
    private String unit;

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.01", message = "单价必须大于0")
    @Schema(description = "单价（元）", example = "10.50")
    private BigDecimal price;
}

