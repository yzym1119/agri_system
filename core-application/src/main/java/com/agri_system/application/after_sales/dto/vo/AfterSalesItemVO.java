package com.agri_system.application.after_sales.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "售后明细")
public class AfterSalesItemVO {

    @Schema(description = "售后明细ID", example = "1")
    private Long id;

    @Schema(description = "订单明细ID", example = "101")
    private Long orderItemId;

    @Schema(description = "商品ID", example = "1001")
    private Long productId;

    @Schema(description = "商品名称", example = "复合肥")
    private String productName;

    @Schema(description = "单位", example = "袋")
    private String unit;

    @Schema(description = "售后数量", example = "2")
    private BigDecimal quantity;

    @Schema(description = "退款金额", example = "88.00")
    private BigDecimal refundAmount;
}
