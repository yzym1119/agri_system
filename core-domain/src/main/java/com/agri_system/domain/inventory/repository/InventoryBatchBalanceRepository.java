package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryBatchBalanceRepository {
    void save(InventoryBatchBalance balance);

    Optional<InventoryBatchBalance> findByBatchAndLocationForUpdate(Long batchId, Long locationId);

    /** 查询某货位上某 SKU 的全部批次余额（货架批次归属裁决用）。 */
    List<InventoryBatchBalance> findByLocationAndProduct(Long locationId, Long productId);

    BigDecimal sumAvailableBaseQuantity(Long locationId, Long productId);

    BigDecimal sumBaseQuantityByStoreAndProduct(Long storeId, Long productId);
}
