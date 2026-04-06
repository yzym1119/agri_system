package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "一次性二维码创建结果")
public class QrCreateResultVO {

    @Schema(description = "二维码token")
    private String qrToken;

    @Schema(description = "展示用图片（Base64）")
    private String imageBase64;

    @Schema(description = "过期时间")
    private LocalDateTime expireAt;
}

