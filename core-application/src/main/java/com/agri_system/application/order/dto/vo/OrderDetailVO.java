package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单详情")
public class OrderDetailVO {
    
    @Schema(description = "订单ID", example = "1")
    private Long id;
    
    @Schema(description = "订单号", example = "202401011200001234")
    private String orderNo;
    
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "客户ID", example = "1")
    private Long customerId;
    
    @Schema(description = "订单类型：1-线下POS，2-小程序自提，3-小程序配送", example = "2")
    private Integer orderType;
    
    @Schema(description = "订单状态", example = "2")
    private Integer orderStatus;
    
    @Schema(description = "订单总金额", example = "100.00")
    private BigDecimal totalAmount;
    
    @Schema(description = "优惠金额", example = "10.00")
    private BigDecimal discountAmount;
    
    @Schema(description = "优惠券抵扣总额", example = "5.00")
    private BigDecimal couponAmount;
    
    @Schema(description = "净应结算额（含赊销）", example = "85.00")
    private BigDecimal payAmount;

    @Schema(description = "已收金额（不含赊销）", example = "60.00")
    private BigDecimal receivedAmount;

    @Schema(description = "赊销金额", example = "25.00")
    private BigDecimal creditAmount;
    private BigDecimal creditReductionAmount;
    private BigDecimal cashRefundedAmount;
    private BigDecimal remainingCreditAmount;
    private BigDecimal repaidAmount;

    
    @Schema(description = "主支付方式", example = "10")
    private Integer paymentMethod;

    @Schema(description = "主支付方式展示名称", example = "混合支付")
    private String paymentMethodName;
    
    @Schema(description = "自提码（仅自提订单）", example = "123456")
    private String pickupCode;
    
    @Schema(description = "门店名称", example = "总店")
    private String storeName;
    
    @Schema(description = "客户姓名", example = "张三")
    private String customerName;
    
    @Schema(description = "客户电话", example = "13800138000")
    private String customerPhone;
    
    @Schema(description = "收货人姓名", example = "张三")
    private String receiverName;
    
    @Schema(description = "收货人电话", example = "13800138000")
    private String receiverPhone;
    
    @Schema(description = "收货地址", example = "北京市朝阳区xxx")
    private String receiverAddress;
    
    @Schema(description = "订单明细列表")
    private List<OrderItemVO> items;
    
    @Schema(description = "支付明细列表")
    private List<OrderPaymentVO> payments;
    
    @Schema(description = "配送信息")
    private OrderDeliveryVO delivery;
    
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作员姓名")
    private String operatorName;
    
    @Schema(description = "售后记录列表")
    private List<AfterSalesVO> afterSalesList;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}





