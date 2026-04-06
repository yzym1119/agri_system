package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "二维码状态视图")
public class QrStatusVO {

    @Schema(description = "二维码token")
    private String qrToken;

    @Schema(description = "状态：0-已创建 1-已绑定 2-已使用 3-已过期")
    private Integer status;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "绑定时间")
    private java.time.LocalDateTime bindAt;
}

