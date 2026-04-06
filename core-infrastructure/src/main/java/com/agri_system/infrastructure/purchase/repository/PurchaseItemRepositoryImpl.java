package com.agri_system.infrastructure.purchase.repository;

import com.agri_system.domain.purchase.model.PurchaseOrderItem;
import com.agri_system.domain.purchase.repository.PurchaseItemRepository;
import com.agri_system.infrastructure.purchase.mapper.PurchaseItemMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 采购订单明细仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class PurchaseItemRepositoryImpl implements PurchaseItemRepository {
    
    private final PurchaseItemMapper itemMapper;
    
    @Override
    public void save(PurchaseOrderItem item) {
        if (item.getId() == null) {
            itemMapper.insert(item);
        } else {
            itemMapper.update(item);
        }
    }
    
    @Override
    public void saveBatch(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PurchaseOrderItem item : items) {
            save(item);
        }
    }
    
    @Override
    public List<PurchaseOrderItem> findByOrderId(Long orderId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("purchase_order_id = ?", orderId)
                .orderBy("id ASC");
        return itemMapper.selectListByQuery(queryWrapper);
    }
    
    @Override
    public PurchaseOrderItem findById(Long id) {
        return itemMapper.selectOneById(id);
    }
    
    @Override
    public void deleteByOrderId(Long orderId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("purchase_order_id = ?", orderId);
        itemMapper.deleteByQuery(queryWrapper);
    }
}








