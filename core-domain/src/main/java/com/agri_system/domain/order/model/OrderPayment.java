package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付明细实体（支持混合支付）
 * 
 * @author AI Generated
 */
@Data
@Table("order_payment")
public class OrderPayment {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 订单ID */
    private Long orderId;
    
    /** 支付类型：1-微信, 2-支付宝, 3-本金余额, 4-专用金余额, 5-红包/优惠券, 6-赊销 */
    private Integer paymentType;
    
    /** 该方式支付金额（元） */
    private BigDecimal amount;
    
    /** 红包/优惠券ID（当payment_type=5时填充） */
    private Long couponId;
    
    /** 外部支付流水号（微信/支付宝） */
    private String outerTransNo;
    
    /** 外部支付回调时间（微信/支付宝） */
    private LocalDateTime callbackTime;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
}








