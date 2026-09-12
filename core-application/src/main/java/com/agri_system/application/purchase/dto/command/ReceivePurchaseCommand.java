package com.agri_system.application.purchase.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 采购收货命令：一采购明细行可包含多个真实批次。 */
@Data
@Schema(description = "采购收货命令")
public class ReceivePurchaseCommand {
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseOrderId;

    @NotEmpty(message = "收货行不能为空")
    @Valid
    private List<ReceiptLineCommand> lines;

    /** 仅在本次累计收货超过采购数量时允许放行，并作为收货审计事实保存。 */
    private String exceptionReason;
    private String remark;

    @Data
    @Schema(description = "采购收货行")
    public static class ReceiptLineCommand {
        @NotNull(message = "采购明细ID不能为空")
        private Long purchaseOrderItemId;

        @NotNull(message = "商品ID不能为空")
        private Long productId;

        private String remark;

        @NotEmpty(message = "收货批次不能为空")
        @Valid
        private List<ReceiptBatchCommand> batches;
    }
}
