package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "扫码绑定命令")
public class QrBindCommand {

    @NotBlank
    @Schema(description = "二维码token")
    private String qrToken;

    @NotNull
    @Schema(description = "客户ID（小程序登录后生成）")
    private Long customerId;

    @Schema(description = "微信OpenID")
    private String openid;

    @Schema(description = "手机号")
    private String phone;
}

