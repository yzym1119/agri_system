package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 新库存链路下，一个收货批次在一个位置的基准单位余额。 */
@Data
@Table("inventory_batch_balance")
public class InventoryBatchBalance {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long batchId;
    private Long productId;
    @Column(tenantId = true)
    private Long storeId;
    private Long locationId;
    private BigDecimal baseQuantity;
    private BigDecimal reservedBaseQuantity;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BigDecimal availableBaseQuantity() {
        return safe(baseQuantity).subtract(safe(reservedBaseQuantity));
    }

    public void increase(BigDecimal quantity) {
        baseQuantity = safe(baseQuantity).add(quantity);
    }

    public void deductAvailable(BigDecimal quantity) {
        if (availableBaseQuantity().compareTo(quantity) < 0) {
            throw new IllegalArgumentException("货位库存不足");
        }
        baseQuantity = safe(baseQuantity).subtract(quantity);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
