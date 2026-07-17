package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryTransferOrderDetail;

import java.util.List;

/**
 * 库存调拨批次明细仓储接口
 */
public interface InventoryTransferOrderDetailRepository {

    void saveBatch(List<InventoryTransferOrderDetail> details);
}
