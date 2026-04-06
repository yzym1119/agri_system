package com.agri_system.infrastructure.productpackage.repository;

import com.agri_system.domain.productpackage.model.ProductPackageItem;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import com.agri_system.infrastructure.productpackage.mapper.ProductPackageItemMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 套餐明细仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class ProductPackageItemRepositoryImpl implements ProductPackageItemRepository {
    
    private final ProductPackageItemMapper itemMapper;
    
    @Override
    public void save(ProductPackageItem item) {
        if (item.getId() == null) {
            itemMapper.insert(item);
        } else {
            itemMapper.update(item);
        }
    }
    
    @Override
    public void saveBatch(List<ProductPackageItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ProductPackageItem item : items) {
            save(item);
        }
    }
    
    @Override
    public List<ProductPackageItem> findItemsByPackageId(Long packageId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("package_id = ?", packageId)
                .orderBy("sort_order ASC, id ASC");
        return itemMapper.selectListByQuery(queryWrapper);
    }
    
    @Override
    public void deleteByPackageId(Long packageId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("package_id = ?", packageId);
        itemMapper.deleteByQuery(queryWrapper);
    }
    
    @Override
    public void delete(Long id) {
        itemMapper.deleteById(id);
    }
}










