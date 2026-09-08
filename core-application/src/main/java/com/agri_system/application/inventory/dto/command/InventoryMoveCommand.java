package com.agri_system.application.inventory.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "批次货位移动命令；数量按原始业务单位输入")
public class InventoryMoveCommand {
    @NotNull(message = "收货批次ID不能为空")
    private Long batchId;
    @Schema(description = "跨店调拨目的门店已收货的同谱系批次ID")
    private Long destinationBatchId;
    private Long fromLocationId;
    private Long toLocationId;
    @NotBlank(message = "移动用途不能为空")
    private String purpose;
    private String transferId;
    @NotBlank(message = "原始单位不能为空")
    private String originalUnit;
    @NotNull(message = "原始数量不能为空")
    @DecimalMin(value = "0.00000001", message = "原始数量必须大于0")
    private BigDecimal originalQuantity;
    private String referenceType;
    private Long referenceId;
    private String remark;
    @Schema(description = "单边增减方向,仅盘点调整/退货使用:IN=入库,OUT=出库")
    private String direction;
    @Schema(description = "幂等键,防止盘点调整/退货重复提交")
    private String idempotencyKey;
}
