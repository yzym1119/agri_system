package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单支付预览VO（预运算结果）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单支付预览")
public class OrderPayPreviewVO {
    
    @Schema(description = "订单总金额", example = "100.00")
    private BigDecimal totalAmount;
    
    @Schema(description = "优惠券抵扣金额", example = "20.00")
    private BigDecimal couponAmount;
    
    @Schema(description = "专用金抵扣金额", example = "30.00")
    private BigDecimal specialFundAmount;
    
    @Schema(description = "本金余额抵扣金额", example = "50.00")
    private BigDecimal balanceAmount;
    
    @Schema(description = "外部支付金额（微信/支付宝）", example = "0.00")
    private BigDecimal externalAmount;
    
    @Schema(description = "支付明细列表")
    private List<OrderPaymentVO> payments;
}








