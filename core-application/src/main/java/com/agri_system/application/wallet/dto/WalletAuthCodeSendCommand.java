package com.agri_system.application.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "钱包验证码发送命令")
public class WalletAuthCodeSendCommand {

    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0.01", message = "支付金额必须大于0")
    @Schema(description = "本次计划使用余额支付的金额", example = "88.50")
    private BigDecimal amount;
}
