package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryReceiptBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryReceiptBatchRepositoryImpl implements InventoryReceiptBatchRepository {
    private final InventoryReceiptBatchMapper mapper;

    @Override
    public void save(InventoryReceiptBatch batch) {
        if (batch.getId() == null) {
            mapper.insert(batch);
        } else {
            mapper.update(batch);
        }
    }

    @Override
    public Optional<InventoryReceiptBatch> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public List<InventoryReceiptBatch> findByReceiptId(Long receiptId) {
        return mapper.findByReceiptId(receiptId);
    }

    @Override
    public BigDecimal sumBaseQuantityByPurchaseOrderItemId(Long purchaseOrderItemId) {
        return mapper.sumBaseQuantityByPurchaseOrderItemId(purchaseOrderItemId);
    }
}
