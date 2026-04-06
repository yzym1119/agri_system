package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付明细 DTO，用于混合支付时指定每种支付方式的金额
 */
@Data
@Schema(description = "支付明细")
public class PaymentDetailDTO {

    @Schema(description = "支付方式代码（1=现金,2=微信,3=支付宝,3=余额/本金,4=专用金,6=赊销,10=混合）", example = "1")
    private Integer paymentType;

    @Schema(description = "该方式支付的金额", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "可选：钱包验证码（余额/专用金时可能需要）", example = "123456")
    private String authCode;
}









