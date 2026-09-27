package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryBatchBalanceRepositoryImpl implements InventoryBatchBalanceRepository {
    private final InventoryBatchBalanceMapper mapper;

    @Override
    public void save(InventoryBatchBalance balance) {
        if (balance.getId() == null) mapper.insert(balance); else mapper.update(balance);
    }

    @Override
    public Optional<InventoryBatchBalance> findByBatchAndLocationForUpdate(Long batchId, Long locationId) {
        return Optional.ofNullable(mapper.findByBatchAndLocationForUpdate(batchId, locationId));
    }

    @Override
    public BigDecimal sumAvailableBaseQuantity(Long locationId, Long productId) {
        return mapper.sumAvailableBaseQuantity(locationId, productId);
    }

    @Override
    public BigDecimal sumBaseQuantityByStoreAndProduct(Long storeId, Long productId) {
        return mapper.sumBaseQuantityByStoreAndProduct(storeId, productId);
    }

    @Override
    public List<InventoryBatchBalance> findByLocationAndProduct(Long locationId, Long productId) {
        return mapper.findByLocationAndProduct(locationId, productId);
    }
}
