package com.agri_system.domain.order.model;

import lombok.Data;
import java.math.BigDecimal;

/** 原销售分配扣除合法回库后的只读交货数量，单位取原批次基准单位。 */
@Data
public class DeliveryItemQuantity {
    private Long orderItemId;
    private String productName;
    private BigDecimal quantity;
    private String unit;
}
