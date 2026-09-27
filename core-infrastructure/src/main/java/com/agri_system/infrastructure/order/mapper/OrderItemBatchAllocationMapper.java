package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单行批次归属事实 Mapper。
 *
 * <p>order_item_batch_allocation 只保留 order_item_id，订单维度查询需 JOIN order_item。</p>
 */
@Mapper
public interface OrderItemBatchAllocationMapper extends BaseMapper<OrderItemBatchAllocation> {

    @Select("SELECT a.* FROM order_item_batch_allocation a "
            + "JOIN order_item i ON i.id = a.order_item_id "
            + "WHERE i.order_id = #{orderId} ORDER BY a.id")
    List<OrderItemBatchAllocation> findByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM order_item_batch_allocation WHERE pickup_request_id = #{pickupRequestId} ORDER BY id")
    List<OrderItemBatchAllocation> findByRequestId(@Param("pickupRequestId") Long pickupRequestId);

    @Select("SELECT * FROM order_item_batch_allocation WHERE order_item_id = #{orderItemId} ORDER BY id")
    List<OrderItemBatchAllocation> findByOrderItemId(@Param("orderItemId") Long orderItemId);
}
