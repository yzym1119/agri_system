package com.agri_system.application.order.dto.command;

import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部支付确认命令（小程序）
 */
@Data
@Schema(description = "外部支付确认命令")
public class OrderExternalPaymentConfirmCommand {

    @NotBlank(message = "支付意图号不能为空")
    @Schema(description = "支付意图号，由 payment-intent 接口返回", example = "PI202604111030001")
    private String intentNo;

    @Schema(description = "外部支付方式：2-微信，3-支付宝。为空时默认按微信处理", example = "2")
    private Integer externalPaymentType = OrderMainPaymentMethod.WECHAT.getCode();

    @NotBlank(message = "外部支付流水号不能为空")
    @Schema(description = "外部支付流水号", example = "wx202604110001")
    private String externalTransactionNo;

    @Schema(description = "外部支付成功时间。为空时默认使用当前时间", example = "2026-04-11T10:30:00")
    private LocalDateTime externalPaidAt;
}
