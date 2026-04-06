package com.agri_system.domain.product.repository;

import com.agri_system.domain.product.model.ProductUnitConversion;

import java.util.List;
import java.util.Optional;

/**
 * 商品单位换算仓储接口
 * 
 * @author AI Generated
 */
public interface ProductUnitConversionRepository {
    
    /**
     * 保存单位换算
     */
    void save(ProductUnitConversion conversion);
    
    /**
     * 根据ID查找单位换算
     */
    Optional<ProductUnitConversion> findById(Long id);
    
    /**
     * 根据商品ID和单位组合查找单位换算
     */
    Optional<ProductUnitConversion> findByProductIdAndUnits(Long productId, String fromUnit, String toUnit);
    
    /**
     * 根据商品ID查询所有单位换算
     */
    List<ProductUnitConversion> findByProductId(Long productId);
    
    /**
     * 删除单位换算
     */
    void delete(Long id);
}











