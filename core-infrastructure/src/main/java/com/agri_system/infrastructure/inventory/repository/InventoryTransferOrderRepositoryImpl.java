package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryTransferOrder;
import com.agri_system.domain.inventory.repository.InventoryTransferOrderRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryTransferOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 库存调拨单仓储实现
 */
@Repository
@RequiredArgsConstructor
public class InventoryTransferOrderRepositoryImpl implements InventoryTransferOrderRepository {

    private final InventoryTransferOrderMapper mapper;

    @Override
    public void save(InventoryTransferOrder order) {
        if (order.getId() == null) {
            mapper.insert(order);
        } else {
            mapper.update(order);
        }
    }

    @Override
    public String findMaxTransferNoByPrefix(String prefix) {
        return mapper.findMaxTransferNoByPrefix(prefix);
    }
}
