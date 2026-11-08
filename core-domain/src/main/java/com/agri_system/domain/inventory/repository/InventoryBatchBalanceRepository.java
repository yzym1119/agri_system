package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InventoryBatchBalanceRepository {
    /** 当前页商品的门店销售可用余额，与销售出库筛选一致，只读、不加库存锁。 */
    List<com.agri_system.domain.inventory.vo.SaleableStock> findSaleableStock(Long storeId, List<Long> productIds);

    void save(InventoryBatchBalance balance);

    Optional<InventoryBatchBalance> findByBatchAndLocationForUpdate(Long batchId, Long locationId);

    /** 查询某货位上某 SKU 的全部批次余额（货架批次归属裁决用）。 */
    List<InventoryBatchBalance> findByLocationAndProduct(Long locationId, Long productId);

    BigDecimal sumAvailableBaseQuantity(Long locationId, Long productId);

    BigDecimal sumBaseQuantityByStoreAndProduct(Long storeId, Long productId);

    /** 门店可用库存（base - reserved）按商品分组，商品ID → 可用量；一次分组查询。 */
    Map<Long, BigDecimal> sumAvailableBaseQuantityGroupedByProduct(Long storeId);

    /** 门店可用库存（base - reserved）按批次分组，批次ID → 可用量；一次分组查询。 */
    Map<Long, BigDecimal> sumAvailableBaseQuantityGroupedByBatch(Long storeId);
}
