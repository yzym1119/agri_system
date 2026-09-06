package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceipt;
import com.agri_system.domain.inventory.repository.InventoryReceiptRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryReceiptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryReceiptRepositoryImpl implements InventoryReceiptRepository {
    private final InventoryReceiptMapper mapper;

    @Override
    public void save(InventoryReceipt receipt) {
        if (receipt.getId() == null) {
            mapper.insert(receipt);
        } else {
            mapper.update(receipt);
        }
    }
}
