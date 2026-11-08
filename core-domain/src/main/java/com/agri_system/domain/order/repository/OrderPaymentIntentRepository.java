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

    /**
     * 确认前条件锁定：仅当意图仍为 PENDING(1) 时持有行锁至事务提交；返回影响行数。
     * 行数为 0 表示意图已被替代失效(3)/确认(2)/过期(3)，调用方必须重新查询状态分流。
     */
    int claimPendingByIntentNo(String intentNo);

    /** 条件过期：仅当意图仍为 PENDING(1) 时置为已失效(3)；返回影响行数。 */
    int expirePendingByIntentNo(String intentNo);
}
