package com.agri_system.infrastructure.product.repository;

import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.infrastructure.product.mapper.ProductMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 商品仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    
    private final ProductMapper productMapper;
    
    @Override
    public void save(Product product) {
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            // MyBatis-Flex 的 update 方法默认只更新非 null 字段
            // 对于需要更新 null 值或 0 值的字段，需要使用 UpdateWrapper 明确指定
            // 但这里我们先尝试直接更新，如果字段值为 0 或非 null，应该会被更新
            productMapper.update(product);
        }
    }
    
    /**
     * 强制更新商品（包括 null 值和 0 值字段）
     * 用于处理需要清空字段或设置值为 0 的情况
     */
    @Override
    public void updateForce(Product product) {
        productMapper.updateForce(
                product.getId(),
                product.getName(),
                product.getCategoryId(),
                product.getSpecification(),
                product.getDosageForm(),
                product.getManufacturer(),
                product.getMainUnit(),
                product.getAuxUnit(),
                product.getMainToAuxRatio(),
                product.getSuggestedRetailPrice(),
                product.getVipPrice(),
                product.getMinPrice(),
                product.getImageUrl(),
                product.getDetailText(),
                product.getIsOnline(),
                product.getBaseShippingFee(),
                product.getAllowSpecialFund(),
                product.getRemark(),
                product.getBarcode(),
                product.getRegistrationNo(),
                product.getStorageConditions(),
                product.getShelfLifeDays(),
                product.getUsageMethod(),
                product.getMinStock(),
                product.getMaxStock(),
                product.getReorderQuantity(),
                product.getUpdatedAt(),
                product.getUpdatedBy()
        );
    }
    
    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(productMapper.selectOneById(id));
    }
    
    @Override
    public Optional<Product> findByCode(String code) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("code = ?", code);
        return Optional.ofNullable(productMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public Optional<Product> findByNameSpecManufacturer(String name, String specification, String manufacturer) {
        QueryWrapper qw = QueryWrapper.create()
                .where("name = ?", name == null ? "" : name)
                .and("COALESCE(specification,'') = ?", specification == null ? "" : specification)
                .and("COALESCE(manufacturer,'') = ?", manufacturer == null ? "" : manufacturer);
        return Optional.ofNullable(productMapper.selectOneByQuery(qw));
    }
    
    @Override
    public Optional<Product> findByNameSpecManufacturerExcludeId(String name, String specification, String manufacturer, Long excludeId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("name = ?", name == null ? "" : name)
                .and("COALESCE(specification,'') = ?", specification == null ? "" : specification)
                .and("COALESCE(manufacturer,'') = ?", manufacturer == null ? "" : manufacturer);
        if (excludeId != null) {
            qw.and("id <> ?", excludeId);
        }
        return Optional.ofNullable(productMapper.selectOneByQuery(qw));
    }
    
    @Override
    public Page<Product> findPage(String code, String name, Long categoryId, Integer isOnline,
                                   LocalDateTime createdStartTime, LocalDateTime createdEndTime,
                                   int pageNum, int pageSize) {
        return findPage(code, name, categoryId, isOnline, null, createdStartTime, createdEndTime, pageNum, pageSize);
    }

    @Override
    public Page<Product> findPage(String code, String name, Long categoryId, Integer isOnline, Integer status,
                                 LocalDateTime createdStartTime, LocalDateTime createdEndTime,
                                 int pageNum, int pageSize) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        
        if (code != null && !code.isEmpty()) {
            queryWrapper.where("code LIKE ?", "%" + code + "%");
        }
        if (name != null && !name.isEmpty()) {
            queryWrapper.where("name LIKE ?", "%" + name + "%");
        }
        if (categoryId != null) {
            queryWrapper.where("category_id = ?", categoryId);
        }
        if (isOnline != null) {
            queryWrapper.where("is_online = ?", isOnline);
        }
        if (status != null) {
            queryWrapper.where("status = ?", status);
        }
        if (createdStartTime != null) {
            queryWrapper.where("created_at >= ?", createdStartTime);
        }
        if (createdEndTime != null) {
            queryWrapper.where("created_at <= ?", createdEndTime);
        }
        
        queryWrapper.orderBy("created_at DESC");

        return productMapper.paginate(pageNum, pageSize, queryWrapper);
    }

    @Override
    public Page<Product> findActiveByKeyword(String keyword, int pageNum, int pageSize) {
        String trimmed = keyword == null ? "" : keyword.trim();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("status = ?", 1);
        if (!trimmed.isEmpty()) {
            String like = "%" + trimmed + "%";
            queryWrapper.and("(code LIKE ? OR name LIKE ? OR COALESCE(barcode,'') LIKE ?)", like, like, like);
        }
        queryWrapper.orderBy("created_at DESC");
        return productMapper.paginate(pageNum, pageSize, queryWrapper);
    }
    
    @Override
    public long countByCategoryId(Long categoryId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("category_id = ?", categoryId);
        return productMapper.selectCountByQuery(queryWrapper);
    }
    
    @Override
    public void updateOnlineStatus(Long id, Integer isOnline) {
        Product product = new Product();
        product.setId(id);
        product.setIsOnline(isOnline);
        productMapper.update(product);
    }
    
    @Override
    public void updateSort(Long id, Integer sort) {
        // 注意：product表没有sort字段，如果需要排序功能，需要添加该字段
        // 这里暂时留空，根据实际需求实现
    }
    
    @Override
    public void delete(Long id) {
        productMapper.deleteById(id);
    }

    @Override
    public java.util.List<Product> findAllWithMinStock() {
        QueryWrapper query = QueryWrapper.create()
                .where("min_stock IS NOT NULL")
                .and("min_stock > 0")
                .and("(deleted IS NULL OR deleted = 0)");
        return productMapper.selectListByQuery(query);
    }

    @Override
    public java.util.List<Product> findAllWithMaxStock() {
        QueryWrapper query = QueryWrapper.create()
                .where("max_stock IS NOT NULL")
                .and("max_stock > 0")
                .and("(deleted IS NULL OR deleted = 0)");
        return productMapper.selectListByQuery(query);
    }

    @Override
    public java.util.List<Product> findByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.List.of();
        }
        QueryWrapper query = QueryWrapper.create().in("id", ids);
        return productMapper.selectListByQuery(query);
    }
}

