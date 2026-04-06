package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 库存批次仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class InventoryBatchRepositoryImpl implements InventoryBatchRepository {
    
    private final InventoryBatchMapper batchMapper;
    
    @Override
    public void save(InventoryBatch batch) {
        if (batch.getId() == null) {
            batchMapper.insert(batch);
        } else {
            batchMapper.update(batch);
        }
    }
    
    @Override
    public Optional<InventoryBatch> findById(Long id) {
        return Optional.ofNullable(batchMapper.selectOneById(id));
    }
    
    @Override
    public Optional<InventoryBatch> findByStoreAndProductAndBatch(Long storeId, Long productId, String batchNo) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("store_id = ?", storeId)
                .where("product_id = ?", productId)
                .where("batch_no = ?", batchNo);
        return Optional.ofNullable(batchMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public Optional<InventoryBatch> findBySameSource(Long storeId, Long productId, String batchNo,
                                                     String sourceType, Long sourceId, Integer sourceLineNo) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("store_id = ?", storeId)
                .where("product_id = ?", productId)
                .where("batch_no = ?", batchNo)
                .where("is_active = 1");
        
        // 处理sourceType（允许null）
        if (sourceType == null) {
            queryWrapper.where("source_type IS NULL");
        } else {
            queryWrapper.where("source_type = ?", sourceType);
        }
        
        // 处理sourceId（允许null）
        if (sourceId == null) {
            queryWrapper.where("source_id IS NULL");
        } else {
            queryWrapper.where("source_id = ?", sourceId);
        }
        
        // 处理sourceLineNo（允许null）
        if (sourceLineNo == null) {
            queryWrapper.where("source_line_no IS NULL");
        } else {
            queryWrapper.where("source_line_no = ?", sourceLineNo);
        }
        
        return Optional.ofNullable(batchMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public List<InventoryBatch> findActiveBatchesForFIFO(Long storeId, Long productId) {
        return batchMapper.findActiveBatchesForFIFO(storeId, productId);
    }
    
    @Override
    public List<InventoryBatch> findActiveBatchesForFIFOWithLock(Long storeId, Long productId) {
        return batchMapper.findActiveBatchesForFIFOWithLock(storeId, productId);
    }
    
    @Override
    public BigDecimal sumQuantityByStoreAndProduct(Long storeId, Long productId) {
        BigDecimal result = batchMapper.sumQuantityByStoreAndProduct(storeId, productId);
        return result != null ? result : BigDecimal.ZERO;
    }
    
    @Override
    public List<InventoryBatch> findActiveBatches(Long storeId, Long productId) {
        return batchMapper.findActiveBatches(storeId, productId);
    }
    
    @Override
    public BigDecimal sumQuantityByProduct(Long productId) {
        BigDecimal result = batchMapper.sumQuantityByProduct(productId);
        return result != null ? result : BigDecimal.ZERO;
    }
}

