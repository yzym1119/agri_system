package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.AssignedDeliveryVO;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/** 仅列当前有效员工本店指派任务。列表不读取收货联系人、金额或商品成本。 */
@Service
@RequiredArgsConstructor
public class DeliveryTaskService {
    private final JdbcTemplate jdbc;
    private final StaffRepository staff;
    public AssignedDeliveryVO.Tasks listAssignedTasks(Long staffId,String state,Long after) {
        var actor=staffId==null?null:staff.findIdentityById(staffId).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted()||actor.getStoreId()==null||actor.getStoreId()<=0)throw new IllegalArgumentException("需要当前有效门店员工身份");
        if(!"PENDING".equals(state)&&!"COMPLETED".equals(state))throw new IllegalArgumentException("配送任务状态无效");
        if(after!=null&&after<=0)throw new IllegalArgumentException("任务游标无效");
        var args=new ArrayList<Object>();args.add(actor.getStoreId());args.add(staffId);args.add("PENDING".equals(state)?3:4);
        String cursor="";if(after!=null){cursor=" AND o.id<?";args.add(after);}
        var tasks=jdbc.query("""
                SELECT o.id,o.order_no,d.delivery_at,d.received_at
                FROM `order` o JOIN order_delivery d ON d.order_id=o.id
                WHERE o.store_id=? AND o.deleted=0 AND d.delivery_staff_id=? AND d.delivery_type=2 AND d.delivery_status=?
                """+cursor+" ORDER BY o.id DESC LIMIT 21",(r,n)->new AssignedDeliveryVO.Task(Long.toString(r.getLong("id")),r.getString("order_no"),state,
                time(r,"delivery_at"),time(r,"received_at")),args.toArray());
        boolean more=tasks.size()>20;var page=java.util.List.copyOf(tasks.subList(0,Math.min(tasks.size(),20)));
        return new AssignedDeliveryVO.Tasks(actor.getStoreId().toString(),state,page,more?page.get(page.size()-1).orderId():null);
    }
    private static String time(ResultSet row,String column)throws SQLException{var v=row.getTimestamp(column);return v==null?null:v.toLocalDateTime().toString();}
}
