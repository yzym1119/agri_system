package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单外部支付意图
 */
@Data
@Table("order_payment_intent")
public class OrderPaymentIntent {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String intentNo;

    private Long orderId;

    private Long customerId;

    private Boolean useBalance;

    private Boolean useSpecialFund;

    private Long couponId;

    private Integer externalPaymentType;

    private BigDecimal externalAmount;

    /**
     * 1-待确认 2-已确认 3-已失效
     */
    private Integer status;

    private String externalTransactionNo;

    private LocalDateTime expireAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
