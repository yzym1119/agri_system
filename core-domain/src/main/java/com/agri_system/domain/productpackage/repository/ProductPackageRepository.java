package com.agri_system.domain.productpackage.repository;

import com.agri_system.domain.productpackage.model.ProductPackage;
import com.mybatisflex.core.paginate.Page;

import java.util.Optional;

/**
 * 套餐仓储接口
 * 
 * @author AI Generated
 */
public interface ProductPackageRepository {
    
    /**
     * 保存套餐
     */
    void save(ProductPackage productPackage);
    
    /**
     * 根据ID查找套餐
     */
    Optional<ProductPackage> findById(Long id);
    
    /**
     * 根据编码查找套餐
     */
    Optional<ProductPackage> findByCode(String code);
    
    /**
     * 分页查询套餐
     */
    Page<ProductPackage> findPage(String code, String name, Integer status, 
                                   int pageNum, int pageSize);
    
    /**
     * 删除套餐（软删除）
     */
    void delete(Long id);
    
    /**
     * 更新套餐状态
     */
    void updateStatus(Long id, Integer status);
}










