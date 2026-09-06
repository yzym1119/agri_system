package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryMovement;

/** 仅允许追加移动事实，禁止更新或删除。 */
public interface InventoryMovementRepository {
    void append(InventoryMovement movement);
}
