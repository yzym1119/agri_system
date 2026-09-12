package com.agri_system.application.product.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** 完整替换 SKU 单位集合中的一行。 */
@Data
@Schema(description = "商品业务单位")
public class ProductUnitUpsertCommand {
    @Schema(description = "单位ID，新增时为空")
    private Long id;

    @NotBlank(message = "单位名称不能为空")
    private String unitName;

    @NotNull(message = "基准数量系数不能为空")
    @Positive(message = "基准数量系数必须大于0")
    private BigDecimal baseQuantityPerUnit;

    @NotNull(message = "是否基准单位不能为空")
    private Integer isBaseUnit;

    private Integer allowPurchase = 1;
    private Integer allowSale = 1;
    private Integer allowInventory = 1;
    private Integer allowTransfer = 1;
    private String barcode;
    private Integer status = 1;
}
