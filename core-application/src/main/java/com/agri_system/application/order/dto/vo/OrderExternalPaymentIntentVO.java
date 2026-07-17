package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部支付发起信息
 */
@Data
@Schema(description = "外部支付发起信息")
public class OrderExternalPaymentIntentVO {

    @Schema(description = "支付意图号", example = "PI202604111030001")
    private String intentNo;

    @Schema(description = "订单ID", example = "1")
    private Long orderId;

    @Schema(description = "订单号", example = "SO202604110001")
    private String orderNo;

    @Schema(description = "外部支付方式：2-微信，3-支付宝", example = "2")
    private Integer externalPaymentType;

    @Schema(description = "外部支付金额", example = "88.50")
    private BigDecimal externalAmount;

    @Schema(description = "意图过期时间", example = "2026-04-11T10:45:00")
    private LocalDateTime expireAt;

    @Schema(description = "支付预览")
    private OrderPayPreviewVO preview;
}
