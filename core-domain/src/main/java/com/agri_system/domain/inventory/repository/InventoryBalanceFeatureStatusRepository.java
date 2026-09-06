package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;

import java.util.Optional;

public interface InventoryBalanceFeatureStatusRepository {
    Optional<InventoryBalanceFeatureStatus> findByStoreAndProduct(Long storeId, Long productId);
}
