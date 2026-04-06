package com.agri_system.domain.product.service;

import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductCategory;
import com.agri_system.domain.product.model.ProductUnitConversion;

/**
 * 商品领域服务
 * 处理不能归属实体的业务规则
 * 
 * @author AI Generated
 */
public class ProductDomainService {
    
    /**
     * 验证商品编码唯一性
     * 
     * @param code 商品编码
     * @param existingProduct 已存在的商品（如果存在）
     * @param currentProductId 当前商品ID（更新时使用）
     * @throws IllegalArgumentException 如果编码已存在
     */
    public void validateProductCodeUnique(String code, Product existingProduct, Long currentProductId) {
        if (existingProduct != null && !existingProduct.getId().equals(currentProductId)) {
            throw new IllegalArgumentException("商品编码已存在: " + code);
        }
    }
    
    /**
     * 验证分类是否有效
     * 
     * @param category 分类
     * @throws IllegalArgumentException 如果分类不存在或已失效
     */
    public void validateCategory(ProductCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        if (!category.isActive()) {
            throw new IllegalArgumentException("分类已失效");
        }
    }
    
    /**
     * 验证单位换算配置是否冲突
     * 
     * @param conversion 单位换算
     * @param existingConversion 已存在的单位换算（如果存在）
     * @throws IllegalArgumentException 如果配置冲突
     */
    public void validateUnitConversion(ProductUnitConversion conversion, ProductUnitConversion existingConversion) {
        if (existingConversion != null && !existingConversion.getId().equals(conversion.getId())) {
            throw new IllegalArgumentException("单位换算配置冲突: " + conversion.getFromUnit() + " -> " + conversion.getToUnit());
        }
    }
    
    /**
     * 验证商品是否可以修改敏感字段
     * 
     * @param product 商品
     * @throws IllegalStateException 如果商品已上架，禁止修改
     */
    public void validateCanModifySensitiveFields(Product product) {
        if (!product.canModifySensitiveFields()) {
            throw new IllegalStateException("商品已上架，禁止修改敏感字段，请先下架");
        }
    }
}


