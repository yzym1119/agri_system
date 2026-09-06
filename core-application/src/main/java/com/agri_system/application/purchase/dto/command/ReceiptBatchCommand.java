package com.agri_system.application.purchase.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 一项实际到货批次；供应商批号缺失时会保存明确的内部收货批次号。 */
@Data
@Schema(description = "实际收货批次")
public class ReceiptBatchCommand {
    private String supplierBatchNo;

    @NotBlank(message = "原始单位不能为空")
    private String originalUnit;

    @NotNull(message = "原始数量不能为空")
    @DecimalMin(value = "0.00000001", message = "原始数量必须大于0")
    private BigDecimal originalQuantity;

    @NotNull(message = "原始单位单价不能为空")
    @DecimalMin(value = "0.00000001", message = "原始单位单价必须大于0")
    private BigDecimal originalUnitCost;

    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String inspectionReference;
}
