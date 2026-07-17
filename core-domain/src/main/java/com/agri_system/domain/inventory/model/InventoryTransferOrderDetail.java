package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存调拨批次明细实体
 */
@Data
@Table("inventory_transfer_detail")
public class InventoryTransferOrderDetail {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long transferId;

    private Long fromBatchId;

    private Long toBatchId;

    private BigDecimal quantity;

    private BigDecimal costPrice;

    private LocalDateTime createdAt;
}
