package com.agri_system.application.order.dto.command;

import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单支付命令（小程序）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单支付命令")
public class OrderPayCommand {
    
    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", example = "1")
    private Long orderId;
    
    @Schema(description = "是否使用本金余额", example = "true")
    private Boolean useBalance;
    
    @Schema(description = "是否使用专用金", example = "true")
    private Boolean useSpecialFund;
    
    @Schema(description = "优惠券ID", example = "1")
    private Long couponId;

    @Schema(description = "外部支付方式：2-微信，3-支付宝。为空时默认按微信处理", example = "2")
    private Integer externalPaymentType = OrderMainPaymentMethod.WECHAT.getCode();
}





