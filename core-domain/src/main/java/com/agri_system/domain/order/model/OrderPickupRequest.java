package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库房取货请求（后仓拣货履约）。
 *
 * <p>货架余额不足或操作员选择库房取货时创建；拣货员扫描内部批次标签或显式确认
 * 批次与货位后完成，产生实际的批次拣货移动与成本归属。</p>
 */
@Data
@Table("order_pickup_request")
public class OrderPickupRequest {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long orderId;

    private Long orderItemId;

    @Column(tenantId = true)
    private Long storeId;

    private Long productId;

    private BigDecimal requestedBaseQuantity;

    /** 期望拣货的源货位（可空时由履约时解析默认后仓）。 */
    private Long sourceLocationId;

    private String status;

    private LocalDateTime createdAt;

    private Long createdBy;

    private LocalDateTime fulfilledAt;

    private Long fulfilledBy;

    public enum Status {
        PENDING, FULFILLED
    }
}
