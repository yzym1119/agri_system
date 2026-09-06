package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.repository.InventoryMovementRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryMovementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryMovementRepositoryImpl implements InventoryMovementRepository {
    private final InventoryMovementMapper mapper;

    @Override
    public void append(InventoryMovement movement) {
        if (movement.getId() != null) throw new IllegalArgumentException("库存移动事实仅允许追加");
        mapper.insert(movement);
    }
}
