package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;

import java.math.BigDecimal;
import java.util.Optional;

public interface InventoryBatchBalanceRepository {
    void save(InventoryBatchBalance balance);

    Optional<InventoryBatchBalance> findByBatchAndLocationForUpdate(Long batchId, Long locationId);

    BigDecimal sumAvailableBaseQuantity(Long locationId, Long productId);

    BigDecimal sumBaseQuantityByStoreAndProduct(Long storeId, Long productId);
}
