package com.agri_system.infrastructure.order.query;

import com.agri_system.domain.order.query.PickupTaskQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PickupTaskQueryImpl implements PickupTaskQuery {
    private final JdbcTemplate jdbc;

    @Override
    public List<OrderLine> findOrderLinesForOrder(Long storeId,Long orderId) {
        if(storeId==null||storeId<=0||orderId==null||orderId<=0)throw new IllegalArgumentException("需要明确门店和原订单");
        return List.copyOf(jdbc.query("""
                SELECT r.id AS request_id,r.order_id,o.order_no,c.name AS customer_name,p.name AS product_name,
                       r.status,r.created_at,r.fulfilled_at
                FROM order_pickup_request r
                JOIN `order` o ON o.id=r.order_id AND o.store_id=r.store_id AND o.deleted=0
                LEFT JOIN product p ON p.id=r.product_id
                LEFT JOIN customer c ON c.id=o.customer_id
                WHERE r.store_id=? AND r.order_id=? ORDER BY r.id ASC
                """,(r,n)->new OrderLine(r.getLong("request_id"),r.getLong("order_id"),r.getString("order_no"),r.getString("customer_name"),
                r.getString("product_name"),r.getString("status"),time(r,"created_at"),time(r,"fulfilled_at")),storeId,orderId));
    }

    @Override
    public List<OrderLine> findOrderLines(Long storeId, String state, Long after) {
        // 分页子表只按状态定位订单与游标，外层再取这些订单的全部请求行；两层各自校验门店与未删除订单。
        // 分页必须放在派生表里：MySQL 不接受带 LIMIT 的 IN 子查询（ERROR 1235）。
        var args = new ArrayList<Object>();
        args.add(storeId); args.add(state);
        String cursor = "";
        if (after != null) { cursor = " AND r2.order_id<?"; args.add(after); }
        args.add(storeId);
        String paged = """
                SELECT DISTINCT r2.order_id FROM order_pickup_request r2
                JOIN `order` o2 ON o2.id=r2.order_id AND o2.store_id=r2.store_id AND o2.deleted=0
                WHERE r2.store_id=? AND r2.status=?
                """ + cursor + " ORDER BY r2.order_id DESC LIMIT 21";
        return List.copyOf(jdbc.query("""
                SELECT r.id AS request_id,r.order_id,o.order_no,c.name AS customer_name,p.name AS product_name,
                       r.status,r.created_at,r.fulfilled_at
                FROM (""" + paged + """
                ) paged JOIN order_pickup_request r ON r.order_id=paged.order_id
                JOIN `order` o ON o.id=r.order_id AND o.store_id=r.store_id AND o.deleted=0
                LEFT JOIN product p ON p.id=r.product_id
                LEFT JOIN customer c ON c.id=o.customer_id
                WHERE r.store_id=?
                ORDER BY r.order_id DESC,r.id ASC""", (r, n) -> new OrderLine(r.getLong("request_id"),
                r.getLong("order_id"), r.getString("order_no"), r.getString("customer_name"),
                r.getString("product_name"), r.getString("status"), time(r, "created_at"), time(r, "fulfilled_at")),
                args.toArray()));
    }

    private static LocalDateTime time(ResultSet row, String name) throws SQLException {
        var value = row.getTimestamp(name); return value == null ? null : value.toLocalDateTime();
    }
}
