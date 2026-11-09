package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.Inventory;
import com.agri_system.domain.inventory.vo.InventoryPageVO;
import com.mybatisflex.core.paginate.Page;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 库存快照仓储接口
 * 
 * @author AI Generated
 */
public interface InventoryRepository {
    
    /**
     * 保存或更新库存快照
     */
    void save(Inventory inventory);

    /** 库存写入先锁商品，使成本汇总和跨店调拨遵循相同锁序。 */
    void lockProduct(Long productId);
    
    /**
     * 根据门店和商品查找库存快照
     */
    Optional<Inventory> findByStoreAndProduct(Long storeId, Long productId);
    
    /**
     * 更新库存快照数量
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param quantity 新数量
     */
    /** 从基准数量投影为商品主单位；所有外部库存数量继续使用明确的主单位口径。 */
    void updateBaseSnapshot(Long storeId, Long productId, BigDecimal baseQuantity);

    void updateSnapshot(Long storeId, Long productId, BigDecimal quantity);

    /**
     * 悲观锁查询库存快照(FOR UPDATE),串行化同门店同商品的并发投影
     *
     * @param storeId 门店ID
     * @param productId 商品ID
     * @return 已加锁的库存快照
     */
    Optional<Inventory> findByStoreAndProductForUpdate(Long storeId, Long productId);
    
    /**
     * 如果不存在则创建库存快照
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param quantity 初始数量
     */
    void createIfNotExists(Long storeId, Long productId, BigDecimal quantity);

    /**
     * 分页查询库存快照（含商品、门店信息）
     */
    Page<InventoryPageVO> findPage(Integer pageNum, Integer pageSize, Long storeId, String productName, Boolean onlyHasStock, Boolean onlyHasSaleableStock);
    
    /**
     * 批量查询指定门店和商品列表的库存
     * 
     * @param storeId 门店ID
     * @param productIds 商品ID列表
     * @return Map<productId, quantity> 商品ID到库存数量的映射
     */
    java.util.Map<Long, java.math.BigDecimal> findInventoryByStoreAndProducts(Long storeId, java.util.List<Long> productIds);

    /**
     * 统计指定门店仍有余额的库存快照数量
     */
    long countPositiveInventoryByStoreId(Long storeId);

    /**
     * 汇总指定商品在所有门店（或指定门店）的总库存
     *
     * @param productId 商品ID
     * @param storeId   门店ID（为null时汇总全部门店）
     * @return 总库存数量
     */
    java.math.BigDecimal sumQuantityByProduct(Long productId, Long storeId);
}







