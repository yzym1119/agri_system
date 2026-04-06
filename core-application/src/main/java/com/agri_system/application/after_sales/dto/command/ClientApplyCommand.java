package com.agri_system.application.after_sales.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 小程序售后申请命令
 */
@Data
@Schema(description = "小程序售后申请命令")
public class ClientApplyCommand {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "申请理由")
    private String reason;
}








