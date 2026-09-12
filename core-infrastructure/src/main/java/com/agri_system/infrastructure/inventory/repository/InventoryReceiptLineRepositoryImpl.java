package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceiptLine;
import com.agri_system.domain.inventory.repository.InventoryReceiptLineRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryReceiptLineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryReceiptLineRepositoryImpl implements InventoryReceiptLineRepository {
    private final InventoryReceiptLineMapper mapper;

    @Override
    public void save(InventoryReceiptLine line) {
        if (line.getId() == null) {
            mapper.insert(line);
        } else {
            mapper.update(line);
        }
    }
}
