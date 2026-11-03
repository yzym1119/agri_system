package com.agri_system.domain.product.repository;

import com.agri_system.domain.product.model.Product;
import com.mybatisflex.core.paginate.Page;

import java.util.Optional;

/**
 * 商品仓储接口
 * 
 * @author AI Generated
 */
public interface ProductRepository {
    
    /**
     * 保存商品
     */
    void save(Product product);

    /** 编辑完整商品资料，保留原强制写入null/零值的持久化语义。 */
    void updateForce(Product product);
    
    /**
     * 根据ID查找商品
     */
    Optional<Product> findById(Long id);
    
    /**
     * 根据编码查找商品
     */
    Optional<Product> findByCode(String code);
    
    /**
     * 根据名称+规格+厂家查找商品（用于查重）
     */
    Optional<Product> findByNameSpecManufacturer(String name, String specification, String manufacturer);
    
    /**
     * 根据名称+规格+厂家查找商品（排除指定ID）
     */
    Optional<Product> findByNameSpecManufacturerExcludeId(String name, String specification, String manufacturer, Long excludeId);
    
    /**
     * 分页查询商品
     */
    Page<Product> findPage(String code, String name, Long categoryId, Integer isOnline, 
                          java.time.LocalDateTime createdStartTime, java.time.LocalDateTime createdEndTime,
                          int pageNum, int pageSize);
    
    /** 按商品状态过滤后分页；状态为空时保留原查询范围。 */
    Page<Product> findPage(String code, String name, Long categoryId, Integer isOnline, Integer status,
                          java.time.LocalDateTime createdStartTime, java.time.LocalDateTime createdEndTime,
                          int pageNum, int pageSize);

    /**
     * 根据分类ID查询商品数量
     */
    long countByCategoryId(Long categoryId);
    
    /**
     * 更新商品上架状态
     */
    void updateOnlineStatus(Long id, Integer isOnline);
    
    /**
     * 更新商品排序
     */
    void updateSort(Long id, Integer sort);
    
    /**
     * 删除商品
     */
    void delete(Long id);

    /**
     * 查询所有设置了最低库存阈值的商品
     */
    java.util.List<Product> findAllWithMinStock();

    /**
     * 查询所有设置了最高库存阈值的商品
     */
    java.util.List<Product> findAllWithMaxStock();

    /**
     * 按 ID 集合批量查询商品（单条 IN 查询）
     */
    java.util.List<Product> findByIds(java.util.Collection<Long> ids);
}











