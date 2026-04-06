package com.agri_system.application.after_sales.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后退款命令
 */
@Data
@Schema(description = "售后退款命令")
public class AfterSalesRefundCommand {

    @NotNull(message = "售后单ID不能为空")
    private Long afterSalesId;

    @DecimalMin(value = "0.01", message = "退款金额必须大于0")
    @Schema(description = "退款金额（为空则按售后单金额）")
    private BigDecimal refundAmount;
}








