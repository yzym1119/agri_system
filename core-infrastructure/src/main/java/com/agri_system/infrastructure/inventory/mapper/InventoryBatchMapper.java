package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryBatch;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存批次Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface InventoryBatchMapper extends BaseMapper<InventoryBatch> {
    
    /**
     * FIFO排序查询批次列表（不使用锁）
     * 排序规则：
     * 1. 有日期的优先于无日期的（expire_date IS NULL 排后）
     * 2. 日期早的优先（expire_date ASC）
     * 3. 入库早的优先（created_at ASC）
     */
    @Select("SELECT * FROM inventory_batch " +
            "WHERE store_id = #{storeId} AND product_id = #{productId} " +
            "AND is_active = 1 AND quantity > 0 " +
            "ORDER BY " +
            "(expire_date IS NULL) ASC, " +
            "expire_date ASC, " +
            "created_at ASC")
    List<InventoryBatch> findActiveBatchesForFIFO(Long storeId, Long productId);
    
    /**
     * FIFO排序查询批次列表（使用悲观锁 FOR UPDATE）
     * 排序规则：
     * 1. 有日期的优先于无日期的（expire_date IS NULL 排后）
     * 2. 日期早的优先（expire_date ASC）
     * 3. 入库早的优先（created_at ASC）
     */
    @Select("SELECT * FROM inventory_batch " +
            "WHERE store_id = #{storeId} AND product_id = #{productId} " +
            "AND is_active = 1 AND quantity > 0 " +
            "ORDER BY " +
            "(expire_date IS NULL) ASC, " +
            "expire_date ASC, " +
            "created_at ASC " +
            "FOR UPDATE")
    List<InventoryBatch> findActiveBatchesForFIFOWithLock(Long storeId, Long productId);
    
    /**
     * 统计指定门店和商品的总库存数量（包含所有有效批次，不限制is_active）
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM inventory_batch " +
            "WHERE store_id = #{storeId} AND product_id = #{productId}")
    BigDecimal sumQuantityByStoreAndProduct(Long storeId, Long productId);
    
    /**
     * 查询指定门店和商品的所有有效批次
     */
    @Select("SELECT * FROM inventory_batch " +
            "WHERE store_id = #{storeId} AND product_id = #{productId} " +
            "AND is_active = 1 " +
            "ORDER BY created_at DESC")
    List<InventoryBatch> findActiveBatches(Long storeId, Long productId);
    
    /**
     * 统计指定商品在所有门店的总库存数量（用于计算平均成本）
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM inventory_batch " +
            "WHERE product_id = #{productId}")
    BigDecimal sumQuantityByProduct(Long productId);
}

