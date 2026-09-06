package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 收货行与采购明细一一对应，行下允许多个实际批次。 */
@Data
@Table("inventory_receipt_line")
public class InventoryReceiptLine {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long receiptId;
    private Integer lineNo;
    private Long purchaseOrderItemId;
    private Long productId;
    @Column(tenantId = true)
    private Long storeId;
    private String remark;
    private LocalDateTime createdAt;
}
