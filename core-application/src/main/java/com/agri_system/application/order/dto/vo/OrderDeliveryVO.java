package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "订单配送信息")
public class OrderDeliveryVO {

    @Schema(description = "物流公司名称", example = "顺丰快递")
    private String logisticsCompany;

    @Schema(description = "快递单号", example = "SF1234567890")
    private String trackingNo;

    @Schema(description = "配送状态", example = "3")
    private Integer deliveryStatus;

    @Schema(description = "配送方式：2-同城配送，3-快递配送", example = "3")
    private Integer deliveryType;

    @Schema(description = "配送员ID", example = "1001")
    private Long deliveryStaffId;

    @Schema(description = "配送员姓名", example = "张师傅")
    private String deliveryStaffName;

    @Schema(description = "配送时间")
    private LocalDateTime deliveryAt;
}
