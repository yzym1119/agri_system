package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 收货事实单头；一张采购单可以对应多张收货单。 */
@Data
@Table("inventory_receipt")
public class InventoryReceipt {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String receiptNo;
    private Long purchaseOrderId;
    private Long supplierId;
    @Column(tenantId = true)
    private Long storeId;
    private String status;
    private LocalDateTime receivedAt;
    private Long receiverId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
