package com.agri_system.application.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "钱包验证码校验命令")
public class WalletAuthCodeVerifyCommand {

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须为6位数字")
    @Schema(description = "6位钱包验证码", example = "123456")
    private String authCode;
}
