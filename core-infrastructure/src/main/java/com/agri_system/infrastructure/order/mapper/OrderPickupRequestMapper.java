package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderPickupRequest;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库房取货请求 Mapper。
 */
@Mapper
public interface OrderPickupRequestMapper extends BaseMapper<OrderPickupRequest> {

    @Select("SELECT * FROM order_pickup_request WHERE id = #{id} FOR UPDATE")
    OrderPickupRequest findByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM order_pickup_request WHERE order_item_id = #{orderItemId} AND status = 'PENDING' "
            + "ORDER BY id LIMIT 1")
    OrderPickupRequest findPendingByOrderItemId(@Param("orderItemId") Long orderItemId);
}
