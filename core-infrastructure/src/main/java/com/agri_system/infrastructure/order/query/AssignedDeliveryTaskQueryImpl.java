package com.agri_system.infrastructure.order.query;

import com.agri_system.domain.order.query.AssignedDeliveryTaskQuery;
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
public class AssignedDeliveryTaskQueryImpl implements AssignedDeliveryTaskQuery {
    private final JdbcTemplate jdbc;

    @Override
    public List<Task> findAssigned(Long storeId, Long staffId, int deliveryStatus, Long after) {
        var args = new ArrayList<Object>();
        args.add(storeId); args.add(staffId); args.add(deliveryStatus);
        String cursor = "";
        if (after != null) { cursor = " AND o.id<?"; args.add(after); }
        return List.copyOf(jdbc.query("""
                SELECT o.id,o.order_no,d.delivery_at,d.received_at
                FROM `order` o JOIN order_delivery d ON d.order_id=o.id
                WHERE o.store_id=? AND o.deleted=0 AND d.delivery_staff_id=? AND d.delivery_type=2 AND d.delivery_status=?
                """ + cursor + " ORDER BY o.id DESC LIMIT 21", (r,n) -> new Task(r.getLong("id"),r.getString("order_no"),
                time(r,"delivery_at"),time(r,"received_at")), args.toArray()));
    }

    private static LocalDateTime time(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
