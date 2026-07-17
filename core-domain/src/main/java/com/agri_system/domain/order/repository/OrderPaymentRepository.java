package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderPayment;

import java.util.List;
import java.util.Optional;

/**
 * 订单支付明细仓储接口
 * 
 * @author AI Generated
 */
public interface OrderPaymentRepository {
    
    /**
     * 保存支付明细
     */
    OrderPayment save(OrderPayment payment);
    
    /**
     * 批量保存支付明细
     */
    void saveAll(List<OrderPayment> payments);
    
    /**
     * 根据订单ID查询所有支付明细
     */
    List<OrderPayment> findByOrderId(Long orderId);

    /**
     * 根据订单ID和外部流水号查询支付明细
     */
    Optional<OrderPayment> findByOrderIdAndOuterTransNo(Long orderId, String outerTransNo);
}







