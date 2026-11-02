package com.agri_system.infrastructure.order.query;

import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.query.*;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 沿既有Flex Mapper读取原成交与合法退货证据；金额分摊和毛利解释仍由应用层负责。 */
@Repository
@RequiredArgsConstructor
public class SalesReportQueryImpl implements SalesReportQuery {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AfterSalesMapper afterSalesMapper;

    @Override public SalesDashboardFacts dashboardFacts(Long storeId, LocalDateTime start, LocalDateTime end) {
        // GMV 与订单数
        QueryWrapper orderQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end)
                .and("order_status in (2,3,4,5,6,9)");
        if (storeId != null) {
            orderQ.and("store_id = ?", storeId);
        }
        BigDecimal gmv = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COALESCE(SUM(total_amount),0) AS total"), BigDecimal.class);
        // 今天创建的销售单原净应结算 N（含赊销）减累计已完成退货 R，不是今日现金流。
        QueryWrapper netSalesQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end)
                .and("order_status in (2,3,4,5,6,8,9)");
        if (storeId != null) netSalesQ.and("store_id = ?", storeId);
        BigDecimal netSales = orderMapper.selectOneByQueryAs(netSalesQ.select("""
                COALESCE(SUM(CASE WHEN order_status = 8 THEN 0 ELSE COALESCE(pay_amount,0) -
                    COALESCE((SELECT SUM(af.refund_amount) FROM after_sales af
                        WHERE af.order_id = `order`.id AND af.store_id = `order`.store_id
                          AND af.status = 50),0) END),0) AS total
                """), BigDecimal.class);
        Long orderCount = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COUNT(1) AS cnt"), Long.class);

        // 待审核售后数（after_sales.status = 10 STATUS_APPLIED）
        QueryWrapper afterSalesQ = QueryWrapper.create()
                .where("status = ?", 10);
        if (storeId != null) {
            afterSalesQ.and("store_id = ?", storeId);
        }
        Long pendingAfterSales = afterSalesMapper.selectCountByQuery(afterSalesQ);

        // 按真实配送单统计可派订单；POS及部分退款单共用实际出库/回库事实。
        QueryWrapper deliveryQ = QueryWrapper.create()
                .where("order_status in (4,9)")
                .and("EXISTS (SELECT 1 FROM order_delivery d WHERE d.order_id = `order`.id AND d.delivery_status = 2 AND d.delivery_type in (2,3))")
                .and("NOT EXISTS (SELECT 1 FROM after_sales af WHERE af.order_id = `order`.id AND af.status in (10,40))")
                .and("""
                        EXISTS (SELECT 1 FROM order_item_batch_allocation a
                            JOIN order_item i ON i.id = a.order_item_id
                            JOIN inventory_movement s ON s.id = a.source_movement_id
                            WHERE i.order_id = `order`.id AND a.store_id = `order`.store_id
                              AND s.movement_type = 'SALE' AND s.base_quantity > 0
                              AND s.base_quantity = a.allocated_base_quantity
                              AND s.store_id = a.store_id AND s.product_id = a.product_id
                              AND s.batch_id = a.batch_id AND s.from_location_id = a.location_id
                              AND s.reference_type = 'ORDER' AND s.reference_id = `order`.id
                              AND a.allocated_base_quantity > COALESCE((
                                  SELECT SUM(r.base_quantity) FROM inventory_movement r
                                  WHERE r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION'
                                    AND r.reference_id = a.id AND r.base_quantity > 0
                                    AND r.store_id = a.store_id AND r.product_id = a.product_id AND r.batch_id = a.batch_id
                              ),0))
                        """);
        if (storeId != null) {
            deliveryQ.and("store_id = ?", storeId);
        }
        Long pendingDelivery = orderMapper.selectCountByQuery(deliveryQ);

        return new SalesDashboardFacts(gmv,netSales,orderCount,pendingAfterSales,pendingDelivery);
    }

    @Override public SalesTrendSource trendSource(LocalDateTime startTime, LocalDateTime endTime, Long storeId) {
        // 先查询订单ID列表（用于关联订单明细）
        QueryWrapper orderQw = QueryWrapper.create()
                .where("created_at between ? and ?", startTime, endTime)
                .and("order_status in (2,3,4,5,6,8,9)"); // 已成交，保留全部退货的原单日期
        if (storeId != null) {
            orderQw.and("store_id = ?", storeId);
        }
        List<Order> orders = orderMapper.selectListByQuery(orderQw);
        if (orders.isEmpty()) {
            return new SalesTrendSource(List.of(),List.of(),Map.of(),Map.of(),java.util.Set.of());
        }
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());

        // 简化实现：直接查询所有明细，在内存中按日期聚合
        // （MyBatis-Flex的QueryWrapper对复杂聚合支持有限，如需高性能可改用@Select注解的SQL）
        QueryWrapper itemQw = QueryWrapper.create();
        if (orderIds.size() == 1) {
            itemQw.where("order_id = ?", orderIds.get(0));
        } else {
            String placeholders = orderIds.stream().map(id -> "?").collect(Collectors.joining(","));
            itemQw.where("order_id in (" + placeholders + ")", orderIds.toArray());
        }
        List<OrderItem> items = orderItemMapper.selectListByQuery(itemQw);

        // 完成售后按原订单创建日冲减；退款发生日期不改变该趋势的归属。
        Map<Long, BigDecimal> refundsByOrder = orderMapper.selectListByQueryAs(orderQw.clone().select("""
                `order`.id AS order_id,
                (SELECT COALESCE(SUM(af.refund_amount),0) FROM after_sales af
                 WHERE af.order_id = `order`.id AND af.store_id = `order`.store_id AND af.status = 50) AS order_refund_amount
                """), SalesTrendReturnFacts.class).stream()
                .collect(Collectors.toMap(SalesTrendReturnFacts::getOrderId, fact -> zero(fact.getOrderRefundAmount())));
        Map<Long, SalesTrendReturnFacts> returnsByItem = orderItemMapper.selectListByQueryAs(
                itemQw.clone().select("""
                    order_item.id AS item_id,
                    (SELECT COALESCE(SUM(ai.refund_amount),0) FROM after_sales_item ai
                     JOIN after_sales af ON af.id = ai.after_sales_id
                     JOIN `order` o ON o.id = af.order_id AND o.store_id = af.store_id
                     WHERE ai.order_item_id = order_item.id AND ai.product_id = order_item.product_id
                       AND af.order_id = order_item.order_id AND af.status = 50) AS refund_amount,
                    (SELECT COALESCE(SUM(ai.quantity),0) FROM after_sales_item ai
                     JOIN after_sales af ON af.id = ai.after_sales_id
                     JOIN `order` o ON o.id = af.order_id AND o.store_id = af.store_id
                     WHERE ai.order_item_id = order_item.id AND ai.product_id = order_item.product_id
                       AND af.order_id = order_item.order_id AND af.status = 50) AS returned_quantity,
                    (SELECT COALESCE(SUM(r.base_quantity),0)
                     FROM order_item_batch_allocation a
                     JOIN inventory_movement s ON s.id = a.source_movement_id
                     JOIN `order` o ON o.id = order_item.order_id AND o.store_id = a.store_id
                     JOIN inventory_movement r ON r.reference_id = a.id
                     WHERE a.order_item_id = order_item.id AND a.product_id = order_item.product_id
                       AND s.movement_type = 'SALE' AND s.reference_type = 'ORDER' AND s.reference_id = o.id
                       AND s.store_id = a.store_id AND s.product_id = a.product_id AND s.batch_id = a.batch_id
                       AND s.from_location_id = a.location_id AND s.base_quantity = a.allocated_base_quantity AND s.base_quantity > 0
                       AND r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION' AND r.base_quantity > 0
                       AND r.store_id = a.store_id AND r.product_id = a.product_id
                       AND r.batch_id = a.batch_id AND r.to_location_id = a.location_id) AS returned_base_quantity,
                    (SELECT COALESCE(SUM(LEAST(a.allocated_cost, ROUND(a.batch_unit_cost * LEAST(a.allocated_base_quantity,
                        COALESCE((SELECT SUM(r.base_quantity) FROM inventory_movement r
                            WHERE r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION'
                              AND r.reference_id = a.id AND r.base_quantity > 0
                              AND r.store_id = a.store_id AND r.product_id = a.product_id
                              AND r.batch_id = a.batch_id AND r.to_location_id = a.location_id),0)),8))),0)
                     FROM order_item_batch_allocation a
                     JOIN inventory_movement s ON s.id = a.source_movement_id
                     JOIN `order` o ON o.id = order_item.order_id AND o.store_id = a.store_id
                     WHERE a.order_item_id = order_item.id AND a.product_id = order_item.product_id
                       AND s.movement_type = 'SALE' AND s.reference_type = 'ORDER' AND s.reference_id = o.id
                       AND s.store_id = a.store_id AND s.product_id = a.product_id AND s.batch_id = a.batch_id
                       AND s.from_location_id = a.location_id AND s.base_quantity = a.allocated_base_quantity
                       AND s.base_quantity > 0) AS returned_cost
                    """), SalesTrendReturnFacts.class).stream()
                .collect(Collectors.toMap(SalesTrendReturnFacts::getItemId, fact -> fact));
        // 先检查逐笔事实，不能让SUM把负数与正数抵消后伪装成有效累计量。
        java.util.Set<Long> invalidRefundOrders = orderMapper.selectListByQueryAs(orderQw.clone()
                .select("`order`.id AS order_id")
                .and("""
                    EXISTS (SELECT 1 FROM after_sales af
                        LEFT JOIN after_sales_item ai ON ai.after_sales_id = af.id
                        WHERE af.order_id = `order`.id AND af.store_id = `order`.store_id AND af.status = 50
                          AND (af.refund_amount IS NULL OR af.refund_amount < 0 OR ai.id IS NULL
                            OR ai.quantity IS NULL OR ai.quantity < 0
                            OR ai.refund_amount IS NULL OR ai.refund_amount < 0))
                    """), SalesTrendReturnFacts.class).stream()
                .map(SalesTrendReturnFacts::getOrderId).collect(Collectors.toSet());
        return new SalesTrendSource(orders,items,refundsByOrder,returnsByItem,invalidRefundOrders);
    }
    private BigDecimal zero(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }
}
