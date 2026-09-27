package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单行批次归属事实。
 *
 * <p>POS 结账时按货架批次余额写入 RULE_ALLOCATED/UNKNOWN 证据；库房取货完成后
 * 以实际拣货移动升级为 SCANNED/FULFILLMENT_CONFIRMED 并补齐实际批次成本。</p>
 */
@Data
@Table("order_item_batch_allocation")
public class OrderItemBatchAllocation {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long orderItemId;

    private Long productId;

    @Column(tenantId = true)
    private Long storeId;

    private Long batchId;

    private Long locationId;

    /** 库房取货请求ID（货架缺货或选择库房取货时关联）。 */
    private Long pickupRequestId;

    private BigDecimal allocatedBaseQuantity;

    private BigDecimal batchUnitCost;

    private BigDecimal allocatedCost;

    private String evidenceLevel;

    private String allocationRule;

    private String allocationRuleVersion;

    /** 产生本归属的库存移动事实ID（库房拣货完成时回填）。 */
    private Long sourceMovementId;

    private LocalDateTime createdAt;

    private Long createdBy;
}
