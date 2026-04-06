package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单分页列表 VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单分页列表")
public class OrderPageVO {
    
    @Schema(description = "订单ID", example = "1")
    private Long id;
    
    @Schema(description = "订单号", example = "ORD20240101000001")
    private String orderNo;
    
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "门店名称", example = "总店")
    private String storeName;
    
    @Schema(description = "客户ID", example = "1")
    private Long customerId;
    
    @Schema(description = "客户姓名", example = "张三")
    private String customerName;
    
    @Schema(description = "客户电话", example = "13800138000")
    private String customerPhone;
    
    @Schema(description = "订单类型：1-POS，2-小程序自提，3-小程序配送", example = "1")
    private Integer orderType;
    
    @Schema(description = "配送方式：1-自提，2-同城配送，3-快递", example = "1")
    private Integer deliveryType;
    
    @Schema(description = "订单状态", example = "2")
    private Integer orderStatus;
    
    @Schema(description = "订单总金额", example = "1050.00")
    private BigDecimal totalAmount;
    
    @Schema(description = "优惠金额", example = "50.00")
    private BigDecimal discountAmount;
    
    @Schema(description = "优惠券金额", example = "10.00")
    private BigDecimal couponAmount;
    
    @Schema(description = "实付金额", example = "990.00")
    private BigDecimal payAmount;
    
    @Schema(description = "主支付方式", example = "1")
    private Integer paymentMethod;
    
    @Schema(description = "收货人姓名", example = "张三")
    private String receiverName;
    
    @Schema(description = "收货人电话", example = "13800138000")
    private String receiverPhone;
    
    @Schema(description = "收货地址", example = "北京市朝阳区xxx")
    private String receiverAddress;
    
    @Schema(description = "自提码（自提订单）", example = "123456")
    private String pickupCode;
    
    @Schema(description = "订单明细列表（仅显示前几个）")
    private List<OrderItemVO> items;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}









