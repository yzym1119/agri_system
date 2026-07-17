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
}
