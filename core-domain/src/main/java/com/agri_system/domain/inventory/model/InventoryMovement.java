package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 追加写的批次位置移动审计事实。 */
@Data
@Table("inventory_movement")
public class InventoryMovement {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String movementNo;
    private Long batchId;
    private Long productId;
    @Column(tenantId = true)
    private Long storeId;
    private Long fromLocationId;
    private Long toLocationId;
    private String movementType;
    private String transferId;
    private Long originalUnitId;
    private String originalUnitName;
    private BigDecimal originalQuantity;
    private BigDecimal unitCost;
    private BigDecimal baseQuantity;
    private String referenceType;
    private Long referenceId;
    private Long operatorId;
    private LocalDateTime occurredAt;
    private String remark;
    private LocalDateTime createdAt;
}
