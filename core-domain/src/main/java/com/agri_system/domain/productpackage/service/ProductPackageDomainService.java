package com.agri_system.domain.productpackage.service;

import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 套餐领域服务
 * 处理不能归属实体的业务规则
 * 
 * @author AI Generated
 */
@RequiredArgsConstructor
public class ProductPackageDomainService {
    
    private final ProductRepository productRepository;
    
    /**
     * 生成套餐编码
     * 格式：PKG + 年月日 + 4位序号（如：PKG202401010001）
     * 
     * @param existingCode 已存在的编码（用于生成下一个序号）
     * @return 新的套餐编码
     */
    public String generatePackageCode(String existingCode) {
        String prefix = "PKG" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        if (existingCode == null || !existingCode.startsWith(prefix)) {
            return prefix + "0001";
        }
        
        // 提取序号部分
        String sequencePart = existingCode.substring(prefix.length());
        try {
            int sequence = Integer.parseInt(sequencePart);
            sequence++;
            return prefix + String.format("%04d", sequence);
        } catch (NumberFormatException e) {
            return prefix + "0001";
        }
    }
    
    /**
     * 校验重复商品
     * 
     * @param items 明细列表
     * @throws IllegalArgumentException 如果存在重复商品
     */
    public void validateNoDuplicateProducts(List<ProductPackageItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        Set<Long> productIds = new HashSet<>();
        for (ProductPackageItem item : items) {
            if (item.getProductId() != null) {
                if (productIds.contains(item.getProductId())) {
                    throw new IllegalArgumentException("package-1005: 套餐包含重复商品");
                }
                productIds.add(item.getProductId());
            }
        }
    }
    
    /**
     * 校验商品有效性
     * 
     * @param productId 商品ID
     * @throws IllegalArgumentException 如果商品不存在
     */
    public void validateProductExists(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + productId));
        
        // 可以添加更多校验，如商品状态等
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new IllegalArgumentException("商品已停用: " + productId);
        }
    }
    
    /**
     * 校验套餐明细列表
     * 
     * @param items 明细列表
     * @throws IllegalArgumentException 如果校验失败
     */
    public void validatePackageItems(List<ProductPackageItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("package-1004: 套餐必须至少包含一个商品");
        }
        
        // 校验数量
        for (ProductPackageItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("商品数量必须大于0");
            }
            
            // 校验商品存在
            validateProductExists(item.getProductId());
        }
        
        // 校验重复商品
        validateNoDuplicateProducts(items);
    }
    
    /**
     * 校验上下架逻辑
     * 
     * @param packageEntity 套餐实体
     * @param newStatus 新状态
     * @throws IllegalStateException 如果状态转换不合法
     */
    public void validateStatusChange(com.agri_system.domain.productpackage.model.ProductPackage packageEntity, Integer newStatus) {
        if (packageEntity == null) {
            throw new IllegalArgumentException("套餐不存在");
        }
        
        if (packageEntity.isDeleted()) {
            throw new IllegalStateException("已删除的套餐不能修改状态");
        }
        
        // 状态值校验：1=上架，0=下架，-1=删除
        if (newStatus != null && newStatus != 1 && newStatus != 0 && newStatus != -1) {
            throw new IllegalArgumentException("无效的状态值: " + newStatus);
        }
    }
}

