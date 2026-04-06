package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付明细VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单支付明细")
public class OrderPaymentVO {
    
    @Schema(description = "支付明细ID", example = "1")
    private Long id;
    
    @Schema(description = "支付类型：1-微信, 2-支付宝, 3-本金余额, 4-专用金余额, 5-红包/优惠券, 6-赊销", example = "3")
    private Integer paymentType;
    
    @Schema(description = "支付金额", example = "50.00")
    private BigDecimal amount;
    
    @Schema(description = "优惠券ID", example = "1")
    private Long couponId;
    
    @Schema(description = "外部支付流水号", example = "wx1234567890")
    private String outerTransNo;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}








