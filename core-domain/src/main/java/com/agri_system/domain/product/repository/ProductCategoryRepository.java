package com.agri_system.domain.product.repository;

import com.agri_system.domain.product.model.ProductCategory;

import java.util.List;
import java.util.Optional;

/**
 * 商品分类仓储接口
 * 
 * @author AI Generated
 */
public interface ProductCategoryRepository {
    
    /**
     * 保存分类
     */
    void save(ProductCategory category);
    
    /**
     * 根据ID查找分类
     */
    Optional<ProductCategory> findById(Long id);
    
    /**
     * 根据编码查找分类
     */
    Optional<ProductCategory> findByCode(String code);
    
    /**
     * 查询所有分类（树形结构）
     */
    List<ProductCategory> findAllTree();
    
    /**
     * 查询所有分类（扁平列表，不构建树）
     */
    List<ProductCategory> findAll();
    
    /**
     * 根据父分类ID查询子分类
     */
    List<ProductCategory> findByParentId(Long parentId);
    
    /**
     * 删除分类
     */
    void delete(Long id);
}











