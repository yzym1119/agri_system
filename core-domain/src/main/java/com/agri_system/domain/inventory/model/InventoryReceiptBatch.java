package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 不可由采购单号推断的实际收货批次及其单位、成本、日期快照。 */
@Data
@Table("inventory_receipt_batch")
public class InventoryReceiptBatch {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long receiptLineId;
    private Long productId;
    @Column(tenantId = true)
    private Long storeId;
    private String supplierBatchNo;
    private String internalReceiptBatchNo;
    private Long originalUnitId;
    private String originalUnitName;
    private BigDecimal originalQuantity;
    private BigDecimal originalUnitCost;
    private Long baseUnitId;
    private BigDecimal baseQuantity;
    private BigDecimal baseUnitCost;
    private String dateStatus;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String inspectionReference;
    private Long receiverId;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
}
