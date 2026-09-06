package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceipt;

/** 收货单事实仓储。 */
public interface InventoryReceiptRepository {
    void save(InventoryReceipt receipt);
}
