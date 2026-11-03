package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.AssignedDeliveryVO;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import com.agri_system.domain.order.query.AssignedDeliveryTaskQuery;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/** 仅列当前有效员工本店指派任务。列表不读取收货联系人、金额或商品成本。 */
@Service
@RequiredArgsConstructor
public class DeliveryTaskService {
    private final AssignedDeliveryTaskQuery taskQuery;
    private final StaffRepository staff;
    public AssignedDeliveryVO.Tasks listAssignedTasks(Long staffId,String state,Long after) {
        var actor=staffId==null?null:staff.findIdentityById(staffId).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted()||actor.getStoreId()==null||actor.getStoreId()<=0)throw new IllegalArgumentException("需要当前有效门店员工身份");
        if(!"PENDING".equals(state)&&!"COMPLETED".equals(state))throw new IllegalArgumentException("配送任务状态无效");
        if(after!=null&&after<=0)throw new IllegalArgumentException("任务游标无效");
        var tasks=taskQuery.findAssigned(actor.getStoreId(),staffId,"PENDING".equals(state)?3:4,after).stream()
                .map(row -> new AssignedDeliveryVO.Task(row.orderId().toString(),row.orderNo(),state,
                        time(row.deliveryAt()),time(row.receivedAt()))).toList();
        boolean more=tasks.size()>20;var page=java.util.List.copyOf(tasks.subList(0,Math.min(tasks.size(),20)));
        return new AssignedDeliveryVO.Tasks(actor.getStoreId().toString(),state,page,more?page.get(page.size()-1).orderId():null);
    }
    private static String time(LocalDateTime value) { return value == null ? null : value.toString(); }
}
