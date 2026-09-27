package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;

import java.util.Optional;

public interface InventoryBalanceFeatureStatusRepository {
    Optional<InventoryBalanceFeatureStatus> findByStoreAndProduct(Long storeId, Long productId);

    /** 按 (storeId, productId) 自然键 upsert；回滚 = 保存 balanceMode=LEGACY 的同一行，不删已记录事实。 */
    InventoryBalanceFeatureStatus save(InventoryBalanceFeatureStatus status);

    /** 将门店 SKU 切换为 BATCH_LOCATION；幂等，重复激活仅刷新 cutover 时间戳。 */
    InventoryBalanceFeatureStatus enable(Long storeId, Long productId, Long operatorId);
}
