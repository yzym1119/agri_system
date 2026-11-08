package com.agri_system.application.order.dto.command;

import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单支付命令（小程序）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单支付命令")
public class OrderPayCommand {
    
    @Schema(description = "由订单URL填入，不要求请求体重复提交", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long orderId;
    
    @Schema(description = "是否使用本金余额", example = "true")
    private Boolean useBalance;
    
    @Schema(description = "是否使用专用金", example = "true")
    private Boolean useSpecialFund;
    
    @Schema(description = "优惠券ID", example = "1")
    private Long couponId;

    @Schema(description = "外部支付方式：2-微信，3-支付宝。为空时默认按微信处理", example = "2")
    private Integer externalPaymentType = OrderMainPaymentMethod.WECHAT.getCode();

    @Schema(description = "支付发起幂等键：同键重复/重试请求返回同一支付意图，不会作废已创建意图。"
            + "建议客户端按「订单号+支付选项+重试序号」生成，长度不超过64字符", example = "pay-1-20260411103000-a1b2c3")
    private String requestKey;
}





