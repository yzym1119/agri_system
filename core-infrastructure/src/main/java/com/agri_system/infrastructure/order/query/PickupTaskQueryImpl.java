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
    public List<Task> findTasks(Long storeId, String state, Long after) {
        var args=new ArrayList<Object>();args.add(storeId);args.add(state);
        String cursor="";
        if(after!=null){cursor=" AND r.id<?";args.add(after);}
        return List.copyOf(jdbc.query("""
                SELECT r.id,o.order_no,p.name,r.status,r.created_at,r.fulfilled_at
                FROM order_pickup_request r JOIN `order` o ON o.id=r.order_id AND o.store_id=r.store_id
                LEFT JOIN product p ON p.id=r.product_id
                WHERE r.store_id=? AND r.status=? AND o.deleted=0
                """+cursor+" ORDER BY r.id DESC LIMIT 21",(r,n)->new Task(r.getLong("id"),r.getString("order_no"),
                r.getString("name"),r.getString("status"),time(r,"created_at"),time(r,"fulfilled_at")),args.toArray()));
    }

    private static LocalDateTime time(ResultSet row,String name)throws SQLException {
        var value=row.getTimestamp(name);return value==null?null:value.toLocalDateTime();
    }
}
