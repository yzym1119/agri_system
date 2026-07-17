package com.agri_system.domain.purchase.repository;

import com.agri_system.domain.purchase.model.PurchaseOrderItem;

import java.util.List;

/**
 * 采购订单明细仓储接口
 * 
 * @author AI Generated
 */
public interface PurchaseItemRepository {
    
    /**
     * 保存采购明细
     */
    void save(PurchaseOrderItem item);
    
    /**
     * 批量保存采购明细
     */
    void saveBatch(List<PurchaseOrderItem> items);
    
    /**
     * 根据采购订单ID查找所有明细
     */
    List<PurchaseOrderItem> findByOrderId(Long orderId);
    
    /**
     * 根据ID查找明细
     */
    PurchaseOrderItem findById(Long id);
    
    /**
     * 删除采购订单的所有明细
     */
    void deleteByOrderId(Long orderId);

    /**
     * 统计商品被采购明细引用次数
     */
    long countByProductId(Long productId);
}







