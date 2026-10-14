package com.agri_system.application.after_sales.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "售后分页项")
public class AfterSalesPageVO {

    @Schema(description = "售后单ID", example = "1")
    private Long id;

    @Schema(description = "售后单号", example = "AS202604140001")
    private String afterSalesNo;

    @Schema(description = "订单ID", example = "1001")
    private Long orderId;

    @Schema(description = "订单号", example = "ORD202604140001")
    private String orderNo;

    @Schema(description = "订单状态", example = "6")
    private Integer orderStatus;

    @Schema(description = "客户ID", example = "88")
    private Long customerId;

    @Schema(description = "客户名称", example = "张三")
    private String customerName;

    @Schema(description = "客户手机号", example = "13800000000")
    private String customerPhone;

    @Schema(description = "门店ID", example = "1")
    private Long storeId;

    @Schema(description = "门店名称", example = "总店")
    private String storeName;

    @Schema(description = "售后类型：1-客户取消，2-管理端退货", example = "2")
    private Integer type;

    @Schema(description = "售后状态：10-已申请，40-待退款，50-已完成，90-已驳回", example = "40")
    private Integer status;

    @Schema(description = "退款金额", example = "128.00")
    private BigDecimal refundAmount;
    private BigDecimal creditReductionAmount;
    private BigDecimal cashRefundAmount;

    @Schema(description = "售后原因", example = "商品损坏")
    private String reason;

    @Schema(description = "审核备注", example = "通过，待财务退款")
    private String auditRemark;

    @Schema(description = "审核人ID", example = "12")
    private Long auditBy;

    @Schema(description = "审核人名称", example = "店长A")
    private String auditByName;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "退款人ID", example = "12")
    private Long refundBy;

    @Schema(description = "退款人名称", example = "财务B")
    private String refundByName;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
