package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderPaymentIntent;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单外部支付意图 Mapper
 */
@Mapper
public interface OrderPaymentIntentMapper extends BaseMapper<OrderPaymentIntent> {

    @Update("""
            UPDATE order_payment_intent
               SET status = 3,
                   updated_at = NOW()
             WHERE order_id = #{orderId}
               AND status = 1
            """)
    int expirePendingByOrderId(@Param("orderId") Long orderId);

    /**
     * 确认前条件锁定：仅当意图仍为 PENDING(1) 时触碰 updated_at 并持有行锁至事务提交，
     * 串行化并发确认/过期/替代失效；返回影响行数，0 表示状态已不再是 PENDING。
     */
    @Update("""
            UPDATE order_payment_intent
               SET updated_at = NOW()
             WHERE intent_no = #{intentNo}
               AND status = 1
            """)
    int claimPendingByIntentNo(@Param("intentNo") String intentNo);

    /**
     * 条件过期：仅当意图仍为 PENDING(1) 时置为已失效(3)；返回影响行数。
     * 用于独立事务过期标记，避免整行覆盖并发事务刚写入的新状态。
     */
    @Update("""
            UPDATE order_payment_intent
               SET status = 3,
                   updated_at = NOW()
             WHERE intent_no = #{intentNo}
               AND status = 1
            """)
    int expirePendingByIntentNo(@Param("intentNo") String intentNo);
}
