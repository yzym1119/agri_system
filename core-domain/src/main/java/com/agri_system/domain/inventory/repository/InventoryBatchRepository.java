package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 库存批次仓储接口
 * 
 * @author AI Generated
 */
public interface InventoryBatchRepository {
    
    /**
     * 保存批次
     */
    void save(InventoryBatch batch);
    
    /**
     * 根据ID查找批次
     */
    Optional<InventoryBatch> findById(Long id);
    
    /**
     * 根据门店、商品、批次号查找批次
     */
    Optional<InventoryBatch> findByStoreAndProductAndBatch(Long storeId, Long productId, String batchNo);
    
    /**
     * 根据同源判定字段查找批次（用于入库合并）
     * 同源规则：store_id + product_id + batch_no + source_type + source_id + source_line_no 完全一致
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param batchNo 批次号
     * @param sourceType 来源类型
     * @param sourceId 来源单据ID
     * @param sourceLineNo 来源单据行号
     * @return 批次（如果存在）
     */
    Optional<InventoryBatch> findBySameSource(Long storeId, Long productId, String batchNo,
                                              String sourceType, Long sourceId, Integer sourceLineNo);
    
    /**
     * FIFO排序查询批次列表（不使用锁，用于查询）
     * 排序规则：
     * 1. 有日期的优先于无日期的（expire_date IS NULL 排后）
     * 2. 日期早的优先（expire_date ASC）
     * 3. 入库早的优先（created_at ASC）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @return 排序后的批次列表
     */
    List<InventoryBatch> findActiveBatchesForFIFO(Long storeId, Long productId);
    
    /**
     * FIFO排序查询批次列表（使用悲观锁 FOR UPDATE，用于出库）
     * 排序规则：
     * 1. 有日期的优先于无日期的（expire_date IS NULL 排后）
     * 2. 日期早的优先（expire_date ASC）
     * 3. 入库早的优先（created_at ASC）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @return 排序后的批次列表（已加锁）
     */
    List<InventoryBatch> findActiveBatchesForFIFOWithLock(Long storeId, Long productId);
    
    /**
     * 统计指定门店和商品的总库存数量
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @return 总库存数量
     */
    BigDecimal sumQuantityByStoreAndProduct(Long storeId, Long productId);
    
    /**
     * 查询指定门店和商品的所有有效批次
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @return 批次列表
     */
    List<InventoryBatch> findActiveBatches(Long storeId, Long productId);
    
    /**
     * 统计指定商品在所有门店的总库存数量（用于计算平均成本）
     * 
     * @param productId 商品ID
     * @return 总库存数量
     */
    BigDecimal sumQuantityByProduct(Long productId);
}

