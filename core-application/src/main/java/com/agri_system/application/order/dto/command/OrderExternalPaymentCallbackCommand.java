package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部支付回调命令
 */
@Data
@Schema(description = "外部支付回调命令")
public class OrderExternalPaymentCallbackCommand {

    @NotBlank(message = "支付意图号不能为空")
    @Schema(description = "支付意图号", example = "PI202604111030001")
    private String intentNo;

    @NotBlank(message = "外部支付流水号不能为空")
    @Schema(description = "外部支付流水号", example = "wx202604110001")
    private String externalTransactionNo;

    @Schema(description = "外部支付成功时间。为空时默认使用当前时间", example = "2026-04-11T10:30:00")
    private LocalDateTime externalPaidAt;
}
