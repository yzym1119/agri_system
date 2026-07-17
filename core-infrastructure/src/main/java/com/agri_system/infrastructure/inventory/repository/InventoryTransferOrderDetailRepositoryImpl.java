package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryTransferOrderDetail;
import com.agri_system.domain.inventory.repository.InventoryTransferOrderDetailRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryTransferOrderDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 库存调拨批次明细仓储实现
 */
@Repository
@RequiredArgsConstructor
public class InventoryTransferOrderDetailRepositoryImpl implements InventoryTransferOrderDetailRepository {

    private final InventoryTransferOrderDetailMapper mapper;

    @Override
    public void saveBatch(List<InventoryTransferOrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (InventoryTransferOrderDetail detail : details) {
            if (detail.getId() == null) {
                mapper.insert(detail);
            } else {
                mapper.update(detail);
            }
        }
    }
}
