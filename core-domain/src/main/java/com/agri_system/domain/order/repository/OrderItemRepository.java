package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderItem;

import java.util.List;
import java.util.Optional;

/**
 * 订单明细仓储接口
 * 
 * @author AI Generated
 */
public interface OrderItemRepository {
    
    /**
     * 保存订单明细
     */
    OrderItem save(OrderItem item);
    
    /**
     * 批量保存订单明细
     */
    void saveAll(List<OrderItem> items);
    
    /**
     * 根据订单ID查询所有明细
     */
    List<OrderItem> findByOrderId(Long orderId);
    
    /**
     * 根据ID查询明细
     */
    Optional<OrderItem> findById(Long id);

    Optional<OrderItem> findByIdForUpdate(Long id);

    /**
     * 统计商品被订单明细引用次数
     */
    long countByProductId(Long productId);

    /**
     * 统计套餐被订单明细引用次数
     */
    long countByPackageId(Long packageId);
}






