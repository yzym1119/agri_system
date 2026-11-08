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

    /** 锁定原收款来源的当前累计已退。 */
    List<OrderPayment> findByOrderIdForUpdate(Long orderId);

    /**
     * 根据订单ID和外部流水号查询支付明细
     */
    Optional<OrderPayment> findByOrderIdAndOuterTransNo(Long orderId, String outerTransNo);

    /**
     * 全局按「支付平台 + 外部流水号」查收款：同一平台流水只允许归属一笔有效业务收款（DB 唯一索引兜底），
     * 用于回调/确认前冲突预检，命中即拒绝入账并进入异常对账。
     */
    Optional<OrderPayment> findByPaymentTypeAndOuterTransNo(Integer paymentType, String outerTransNo);
}







