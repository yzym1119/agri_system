package com.agri_system.application.order.service;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 库管查看本店既有提货事实；不按请求创建人限制，也不读取客户、金额和成本。 */
@Service
@RequiredArgsConstructor
public class MobilePickupTaskService {
    private final JdbcTemplate jdbc;
    private final StaffRepository staff;
    private final PickupFulfillmentService pickup;

    public record Task(String requestId,String orderNo,String productName,String status,String createdAt,String fulfilledAt) {}
    public record Tasks(String storeId,String state,List<Task> tasks,String nextCursor) {}
    public record Allocation(String id,String batchId,String internalBatchNo,String locationId,String locationName,String quantity,String unit) {}
    public record Preview(String storeId,String requestId,String orderId,String orderNo,String productName,String status,
                          boolean canConfirm,String blockReason,String confirmationKey,List<Allocation> allocations,String fulfilledAt) {}

    public Tasks listTasks(Long employeeId,String state,Long after) {
        var actor=actor(employeeId);
        if (!List.of("PENDING","FULFILLED").contains(state)) throw new IllegalArgumentException("提货状态无效");
        if (after!=null && after<=0) throw new IllegalArgumentException("提货游标无效");
        var args=new ArrayList<Object>();args.add(actor.getStoreId());args.add(state);
        String cursor="";
        if(after!=null){cursor=" AND r.id<?";args.add(after);}
        var rows=jdbc.query("""
                SELECT r.id,o.order_no,p.name,r.status,r.created_at,r.fulfilled_at
                FROM order_pickup_request r JOIN `order` o ON o.id=r.order_id AND o.store_id=r.store_id
                LEFT JOIN product p ON p.id=r.product_id
                WHERE r.store_id=? AND r.status=? AND o.deleted=0
                """+cursor+" ORDER BY r.id DESC LIMIT 21",(r,n)->new Task(Long.toString(r.getLong("id")),
                r.getString("order_no"),r.getString("name"),r.getString("status"),time(r,"created_at"),time(r,"fulfilled_at")),args.toArray());
        var page=List.copyOf(rows.subList(0,Math.min(rows.size(),20)));
        return new Tasks(actor.getStoreId().toString(),state,page,rows.size()>20?page.get(page.size()-1).requestId():null);
    }

    public Preview preview(Long requestId,Long employeeId) {
        var actor=actor(employeeId);
        var source=pickup.previewPickupRequest(requestId,employeeId);
        if(!Objects.equals(actor.getStoreId(),source.getStoreId())) throw new IllegalArgumentException("不能读取其他门店的提货请求");
        boolean completed="FULFILLED".equals(source.getStatus());
        boolean ready=!completed && "PENDING".equals(source.getStatus()) && source.isCanFulfill();
        String blocked=completed?null:source.getFulfillmentBlockReason();
        List<Allocation> lines=List.of();
        if(ready) {
            var originals=source.getAllocations();
            if(originals==null || originals.isEmpty() || originals.stream().anyMatch(a->a.getRemainingBaseQuantity()==null || a.getRemainingBaseQuantity().signum()<0)) {
                ready=false;blocked="原出库数量证据缺失，请联系店长核对";
            } else {
                var remaining=originals.stream().filter(a->a.getRemainingBaseQuantity().signum()>0).toList();
                if(remaining.isEmpty() || !StringUtils.hasText(source.getProductName()) || source.getConfirmationKey()==null
                        || !source.getConfirmationKey().matches("[a-f0-9]{64}") || remaining.stream().anyMatch(a->a.getId()==null || a.getBatchId()==null || a.getLocationId()==null
                        || !StringUtils.hasText(a.getBaseUnitName()) || !StringUtils.hasText(a.getInternalReceiptBatchNo()) || !StringUtils.hasText(a.getLocationName()))) {
                    ready=false;blocked="原批次、货位或计量单位缺失，请联系店长核对";
                } else lines=remaining.stream().map(a->new Allocation(a.getId().toString(),a.getBatchId().toString(),a.getInternalReceiptBatchNo(),
                        a.getLocationId().toString(),a.getLocationName(),a.getRemainingBaseQuantity().stripTrailingZeros().toPlainString(),a.getBaseUnitName())).toList();
            }
        }
        if(!completed && !ready && !StringUtils.hasText(blocked)) blocked="当前请求不能交货，请重新核对";
        return new Preview(source.getStoreId().toString(),source.getId().toString(),source.getOrderId().toString(),source.getOrderNo(),source.getProductName(),source.getStatus(),
                ready,blocked,ready?source.getConfirmationKey():null,lines,source.getFulfilledAt()==null?null:source.getFulfilledAt().toString());
    }

    private Staff actor(Long id) {
        var actor=id==null?null:staff.findIdentityById(id).orElse(null);
        if(actor==null || !actor.isActive() || actor.hasBeenDeleted() || actor.getStoreId()==null || actor.getStoreId()<=0)
            throw new IllegalArgumentException("需要当前有效门店员工身份");
        return actor;
    }
    private static String time(ResultSet row,String name)throws SQLException {
        var value=row.getTimestamp(name);return value==null?null:value.toLocalDateTime().toString();
    }
}
