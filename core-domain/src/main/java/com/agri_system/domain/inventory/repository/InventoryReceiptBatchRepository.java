package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceiptBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 收货批次事实仓储。 */
public interface InventoryReceiptBatchRepository {
    void save(InventoryReceiptBatch batch);

    Optional<InventoryReceiptBatch> findById(Long id);

    List<InventoryReceiptBatch> findByReceiptId(Long receiptId);

    BigDecimal sumBaseQuantityByPurchaseOrderItemId(Long purchaseOrderItemId);
}
