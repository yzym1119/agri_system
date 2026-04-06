package com.agri_system.application.purchase.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购入库命令
 *
 * 支持传入实际入库明细（数量/成本调整），为空则按原采购明细入库
 */
@Data
@Schema(description = "采购入库命令")
public class PurchaseInboundCommand {

    @NotNull(message = "采购单ID不能为空")
    @Schema(description = "采购单ID", example = "1")
    private Long orderId;

    @Valid
    @Schema(description = "实际入库明细列表（可选，为空则按采购单原明细入库）")
    private List<InboundItem> items;

    @Data
    @Schema(description = "实际入库明细")
    public static class InboundItem {
        @NotNull(message = "商品ID不能为空")
        @Schema(description = "商品ID", example = "1")
        private Long productId;

        @NotNull(message = "实际入库数量不能为空")
        @Schema(description = "实际入库数量", example = "8")
        private BigDecimal quantity;

        @NotNull(message = "实际成本价不能为空")
        @Schema(description = "实际成本价", example = "12.50")
        private BigDecimal costPrice;

        @Schema(description = "生产日期（可选）", example = "2024-01-01")
        private LocalDate productionDate;

        @Schema(description = "有效期至（可选）", example = "2025-01-01")
        private LocalDate expireDate;
    }
}

