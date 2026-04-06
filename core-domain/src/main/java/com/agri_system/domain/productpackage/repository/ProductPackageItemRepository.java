package com.agri_system.domain.productpackage.repository;

import com.agri_system.domain.productpackage.model.ProductPackageItem;

import java.util.List;

/**
 * 套餐明细仓储接口
 * 
 * @author AI Generated
 */
public interface ProductPackageItemRepository {
    
    /**
     * 保存套餐明细
     */
    void save(ProductPackageItem item);
    
    /**
     * 批量保存套餐明细
     */
    void saveBatch(List<ProductPackageItem> items);
    
    /**
     * 根据套餐ID查找明细列表
     */
    List<ProductPackageItem> findItemsByPackageId(Long packageId);
    
    /**
     * 删除套餐的所有明细
     */
    void deleteByPackageId(Long packageId);
    
    /**
     * 删除明细
     */
    void delete(Long id);
}










