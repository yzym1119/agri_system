package com.agri_system.infrastructure.product.repository;

import com.agri_system.domain.product.model.ProductCategory;
import com.agri_system.domain.product.repository.ProductCategoryRepository;
import com.agri_system.infrastructure.product.mapper.ProductCategoryMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 商品分类仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class ProductCategoryRepositoryImpl implements ProductCategoryRepository {
    
    private final ProductCategoryMapper categoryMapper;
    
    @Override
    public void save(ProductCategory category) {
        if (category.getId() == null) {
            categoryMapper.insert(category);
        } else {
            categoryMapper.update(category);
        }
    }
    
    @Override
    public Optional<ProductCategory> findById(Long id) {
        return Optional.ofNullable(categoryMapper.selectOneById(id));
    }
    
    @Override
    public Optional<ProductCategory> findByCode(String code) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("code = ?", code);
        return Optional.ofNullable(categoryMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public List<ProductCategory> findAllTree() {
        // 查询所有分类
        List<ProductCategory> allCategories = categoryMapper.selectAll();
        
        // 构建树形结构
        return buildTree(allCategories, null);
    }
    
    @Override
    public List<ProductCategory> findAll() {
        // 查询所有分类（扁平列表）
        return categoryMapper.selectAll();
    }
    
    @Override
    public List<ProductCategory> findByParentId(Long parentId) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (parentId == null || parentId == 0) {
            queryWrapper.where("parent_id IS NULL OR parent_id = 0");
        } else {
            queryWrapper.where("parent_id = ?", parentId);
        }
        queryWrapper.orderBy("sort_order ASC");
        return categoryMapper.selectListByQuery(queryWrapper);
    }
    
    @Override
    public void delete(Long id) {
        categoryMapper.deleteById(id);
    }
    
    /**
     * 构建树形结构
     */
    private List<ProductCategory> buildTree(List<ProductCategory> allCategories, Long parentId) {
        List<ProductCategory> result = new ArrayList<>();
        for (ProductCategory category : allCategories) {
            Long catParentId = category.getParentId();
            if ((parentId == null && (catParentId == null || catParentId == 0)) ||
                (parentId != null && parentId.equals(catParentId))) {
                result.add(category);
            }
        }
        return result;
    }
}

