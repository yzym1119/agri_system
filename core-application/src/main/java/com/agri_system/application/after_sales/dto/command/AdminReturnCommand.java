package com.agri_system.application.after_sales.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端退货命令
 */
@Data
@Schema(description = "管理端退货命令")
public class AdminReturnCommand {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.00", message = "退款金额不能为负数")
    @Schema(description = "退款金额")
    private BigDecimal refundAmount;

    @Schema(description = "退货原因")
    private String reason;

    @NotEmpty(message = "退货明细不能为空")
    @Valid
    @Schema(description = "退货明细列表")
    private List<ReturnItem> items;

    @Data
    @Schema(description = "退货明细")
    public static class ReturnItem {
        @NotNull(message = "订单明细ID不能为空")
        private Long orderItemId;

        @NotNull(message = "退货数量不能为空")
        @DecimalMin(value = "0.0001", message = "退货数量必须大于0")
        private BigDecimal quantity;

        @NotNull(message = "明细退款金额不能为空")
        @DecimalMin(value = "0.00", message = "明细退款金额不能为负数")
        private BigDecimal refundAmount;
    }
}








