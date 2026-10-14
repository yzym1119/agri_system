package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单退款命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单退款命令")
public class OrderRefundCommand {
    /** 正式退款必须确认预览；同一预览标识防止网络重试重复退款。 */
    @NotBlank(message = "请先确认最新退货资金预览")
    private String quoteId;

    
    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", example = "1")
    private Long orderId;
    
    @NotNull(message = "退款原因不能为空")
    @Schema(description = "退款原因", example = "商品质量问题")
    private String refundReason;
    
    @Schema(description = "退款金额（为空则全额退款）", example = "100.00")
    private BigDecimal refundAmount;

    @Valid
    @Schema(description = "退款明细（部分退款时必填）")
    private List<RefundItem> items;

    @Data
    @Schema(description = "退款明细")
    public static class RefundItem {
        @NotNull(message = "订单明细ID不能为空")
        @Schema(description = "订单明细ID", example = "1")
        private Long orderItemId;

        @NotNull(message = "退款数量不能为空")
        @DecimalMin(value = "0.0001", message = "退款数量必须大于0")
        @Schema(description = "退款数量", example = "1.0000")
        private BigDecimal quantity;

        @DecimalMin(value = "0.00", message = "明细退款金额不能为负数")
        @Schema(description = "明细退款金额", example = "50.00")
        private BigDecimal refundAmount;
    }
}







