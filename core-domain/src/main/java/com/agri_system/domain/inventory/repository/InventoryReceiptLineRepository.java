package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceiptLine;

/** 收货行事实仓储。 */
public interface InventoryReceiptLineRepository {
    void save(InventoryReceiptLine line);
}
