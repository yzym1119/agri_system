package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceiptBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 收货批次事实仓储。 */
public interface InventoryReceiptBatchRepository {
    void save(InventoryReceiptBatch batch);

    Optional<InventoryReceiptBatch> findById(Long id);

    /** 按内部收货批次号（可打印的库房标签）查询批次事实；仅内部标签可产生强证据。 */
    Optional<InventoryReceiptBatch> findByInternalReceiptBatchNo(String internalReceiptBatchNo);

    List<InventoryReceiptBatch> findByReceiptId(Long receiptId);

    BigDecimal sumBaseQuantityByPurchaseOrderItemId(Long purchaseOrderItemId);
}
