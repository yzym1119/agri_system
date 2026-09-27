package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /**
     * 观察窗口内按商品的批次成本口径毛利汇总（一次分组查询）。
     *
     * <p>订单行可能拆到多个批次（多行归属），行级销售额在派生表内用 MAX 去重，
     * 避免销售额随归属行数被重复累计；批次成本仍按归属行求和。</p>
     */
    @Select("<script>"
            + "SELECT t.productId AS productId, SUM(t.itemRevenue) AS revenue, "
            + "SUM(t.itemCost) AS totalCost, COUNT(DISTINCT t.orderId) AS orderCount "
            + "FROM ("
            + "SELECT a.product_id AS productId, a.order_item_id AS orderItemId, i.order_id AS orderId, "
            + "MAX(i.quantity * i.unit_price) AS itemRevenue, SUM(a.allocated_cost) AS itemCost "
            + "FROM order_item_batch_allocation a "
            + "JOIN order_item i ON i.id = a.order_item_id "
            + "JOIN `order` o ON o.id = i.order_id "
            + "WHERE a.allocated_cost IS NOT NULL "
            + "AND o.order_status IN (2,3,4,5,6,9) "
            + "AND o.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "<if test='storeId != null'> AND a.store_id = #{storeId} </if>"
            + "GROUP BY a.product_id, a.order_item_id, i.order_id"
            + ") t GROUP BY t.productId"
            + "</script>")
    List<Map<String, Object>> sumWindowMarginByProduct(@Param("days") int days, @Param("storeId") Long storeId);
}
