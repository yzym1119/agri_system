package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;
import com.agri_system.domain.inventory.repository.InventoryBalanceFeatureStatusRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryBalanceFeatureStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryBalanceFeatureStatusRepositoryImpl implements InventoryBalanceFeatureStatusRepository {
    private final InventoryBalanceFeatureStatusMapper mapper;

    @Override
    public Optional<InventoryBalanceFeatureStatus> findByStoreAndProduct(Long storeId, Long productId) {
        return Optional.ofNullable(mapper.findByStoreAndProduct(storeId, productId));
    }
}
