package com.agri_system.domain.product.repository;

import com.agri_system.domain.product.model.ProductPrice;

import java.util.List;
import java.util.Optional;

/**
 * 商品价格仓储接口
 * 
 * @author AI Generated
 */
public interface ProductPriceRepository {
    
    /**
     * 保存价格规则
     */
    void save(ProductPrice price);
    
    /**
     * 根据ID查找价格规则
     */
    Optional<ProductPrice> findById(Long id);
    
    /**
     * 根据商品ID查询价格规则列表
     */
    List<ProductPrice> findByProductId(Long productId);
    
    /**
     * 统计某商品启用状态的价格规则数量
     */
    long countActiveByProductId(Long productId);
    
    /**
     * 删除价格规则
     */
    void delete(Long id);
}











