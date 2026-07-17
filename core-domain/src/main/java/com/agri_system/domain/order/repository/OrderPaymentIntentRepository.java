package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderPaymentIntent;

import java.util.Optional;

/**
 * 订单外部支付意图仓储
 */
public interface OrderPaymentIntentRepository {

    OrderPaymentIntent save(OrderPaymentIntent intent);

    Optional<OrderPaymentIntent> findByIntentNo(String intentNo);

    int expirePendingByOrderId(Long orderId);
}
