package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单退款命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单退款命令")
public class OrderRefundCommand {
    
    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", example = "1")
    private Long orderId;
    
    @NotNull(message = "退款原因不能为空")
    @Schema(description = "退款原因", example = "商品质量问题")
    private String refundReason;
    
    @Schema(description = "退款金额（为空则全额退款）", example = "100.00")
    private BigDecimal refundAmount;
}








