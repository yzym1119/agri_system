package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderDelivery;

import java.util.Optional;

/**
 * 订单配送仓储接口
 * 
 * @author AI Generated
 */
public interface OrderDeliveryRepository {
    
    /**
     * 保存配送信息
     */
    OrderDelivery save(OrderDelivery delivery);
    
    /**
     * 根据订单ID查询配送信息
     */
    Optional<OrderDelivery> findByOrderId(Long orderId);
    
    /**
     * 根据快递单号查询配送信息
     */
    Optional<OrderDelivery> findByTrackingNo(String trackingNo);
}








