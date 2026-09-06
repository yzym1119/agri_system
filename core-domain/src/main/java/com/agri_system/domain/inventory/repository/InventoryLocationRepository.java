package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryLocation;

import java.util.Optional;

public interface InventoryLocationRepository {
    void save(InventoryLocation location);

    Optional<InventoryLocation> findById(Long id);

    Optional<InventoryLocation> findEnabledByStoreAndType(Long storeId, String locationType);
}
