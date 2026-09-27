package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryLocation;

import java.util.List;
import java.util.Optional;

public interface InventoryLocationRepository {
    void save(InventoryLocation location);

    Optional<InventoryLocation> findById(Long id);

    Optional<InventoryLocation> findEnabledByStoreAndType(Long storeId, String locationType);

    /**
     * 查询门店全部货位（按 id 升序）
     */
    List<InventoryLocation> findByStoreId(Long storeId);

    /**
     * 按门店 + 货位编码查询启用中的货位
     */
    Optional<InventoryLocation> findByStoreIdAndCode(Long storeId, String code);
}
