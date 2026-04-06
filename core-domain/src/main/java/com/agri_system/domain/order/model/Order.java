package com.agri_system.domain.order.model;

import com.agri_system.domain.order.enums.PaymentMethod;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（聚合根）
 * 
 * @author AI Generated
 */
@Data
@Table("order")
public class Order {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 订单号 */
    private String orderNo;
    
    /** 门店ID */
    private Long storeId;
    
    /** 客户ID（可为空，支持散客） */
    private Long customerId;
    
    /** 订单类型：1-线下POS，2-小程序自提，3-小程序配送 */
    private Integer orderType;
    
    /** 订单状态：1-待支付(WAIT_PAY), 2-已支付(PAID), 3-待自提(WAIT_PICKUP),
     *           4-待发货(WAIT_SHIP), 5-已发货(SHIPPED), 6-已完成(FINISHED),
     *           7-已取消(CANCELLED), 8-已退款(REFUNDED), 9-部分退款(PART_REFUNDED) */
    private Integer orderStatus;
    
    /** 订单总金额（元） */
    private BigDecimal totalAmount;
    
    /** 优惠金额（元） */
    private BigDecimal discountAmount;
    
    /** 优惠券抵扣总额（元） */
    private BigDecimal couponAmount;
    
    /** 实付金额（元） */
    private BigDecimal payAmount;
    
    /** 主支付方式：1-现金, 2-微信, 3-支付宝, 5-赊销, 10-混合支付(详见order_payment表) */
    private Integer paymentMethod;
    
    /** 销售员工ID（线下POS必填） */
    private Long staffId;
    
    /** 收货人姓名（下单时固化） */
    private String receiverName;
    
    /** 收货人电话（下单时固化） */
    private String receiverPhone;
    
    /** 收货地址（下单时固化） */
    private String receiverAddress;
    
    /** 自提核销码（仅自提订单生成，6位数字） */
    private String pickupCode;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /** 创建人ID */
    private Long createdBy;
    
    /**
     * 检查订单是否已支付
     */
    public boolean isPaid() {
        return orderStatus != null && orderStatus == 2;
    }
    
    /**
     * 检查订单是否可以取消
     */
    public boolean canCancel() {
        return orderStatus != null && (orderStatus == 1 || orderStatus == 2);
    }
    
    /**
     * 检查订单是否可以退款
     */
    public boolean canRefund() {
        return orderStatus != null && orderStatus == 2 && paymentMethod != null && !paymentMethod.equals(PaymentMethod.CREDIT.getCode()); // 赊销不支持退款
    }
    
    /**
     * 标记为已支付
     */
    public void markAsPaid() {
        this.orderStatus = 2;
    }
    
    /**
     * 标记为待自提
     */
    public void markAsWaitPickup() {
        this.orderStatus = 3;
    }
    
    /**
     * 标记为待发货
     */
    public void markAsWaitShip() {
        this.orderStatus = 4;
    }
    
    /**
     * 标记为已发货
     */
    public void markAsShipped() {
        this.orderStatus = 5;
    }
    
    /**
     * 标记为已完成
     */
    public void markAsFinished() {
        this.orderStatus = 6;
    }
    
    /**
     * 标记为已取消
     */
    public void markAsCancelled() {
        this.orderStatus = 7;
    }
    
    /**
     * 标记为已退款
     */
    public void markAsRefunded() {
        this.orderStatus = 8;
    }
    /**
     * 标记为部分退款
     */
    public void markAsPartRefunded() {
        this.orderStatus = 9;
    }
}

