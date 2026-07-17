package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryTransferOrder;

/**
 * 库存调拨单仓储接口
 */
public interface InventoryTransferOrderRepository {

    void save(InventoryTransferOrder order);

    String findMaxTransferNoByPrefix(String prefix);
}
