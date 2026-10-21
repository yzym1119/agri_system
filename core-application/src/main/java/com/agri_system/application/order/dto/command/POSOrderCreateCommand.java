package com.agri_system.application.order.dto.command;

 
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * POS订单创建命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "POS订单创建命令")
public class POSOrderCreateCommand {
    @NotNull(message="收银请求号不能为空，请刷新收银台")
    @jakarta.validation.constraints.Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", message="收银请求号格式不正确")
    private String requestKey;
    
    @NotNull(message = "门店ID不能为空")
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "客户ID（可为空，支持散客）", example = "1")
    private Long customerId;
    
    @Schema(description = "履约方式：1-当场交货（默认），2-门店配送")
    private Integer deliveryType;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    @Schema(description = "订单明细列表")
    private List<OrderItemDTO> items;
    
    @NotNull(message = "支付方式不能为空")
    @Schema(description = "主支付方式：1-现金, 2-微信, 3-支付宝, 4-余额, 6-赊销, 10-混合支付", example = "2")
    private Integer paymentType;
    
    @Schema(description = "整单优惠金额（手工优惠，可选）", example = "10.00")
    private BigDecimal discountAmount;
    
    @Schema(description = "实收金额（现金支付时必填）", example = "100.00")
    private BigDecimal receiveAmount;
    
    @Schema(description = "钱包验证码（余额/专用金支付时必填）", example = "123456")
    private String walletAuthCode;
    
    @Schema(description = "是否使用专用金（余额支付时可选）", example = "true")
    private Boolean useSpecialFund;

    @Schema(description = "使用的优惠券ID（可选）", example = "1")
    private Long couponId;
    
    @Schema(description = "支付明细（混合支付时使用，兼容 legacy paymentType/receiveAmount；余额会由后端自动拆分为本金余额/专用金流水）")
    private List<PaymentDetailDTO> payments;
    
    @NotNull(message = "销售员工ID不能为空")
    @Schema(description = "销售员工ID", example = "1")
    private Long staffId;
    
    @Schema(description = "备注", example = "POS订单")
    private String remark;
}







