package com.agri_system.application.order.service;

import com.agri_system.domain.order.query.PickupTaskQuery;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/** 整单个人备货只读视图；不认领、不预留、不确认交货，revision不能用于履约。 */
@Service
@RequiredArgsConstructor
public class MobilePickupPreparationService {
    private final PickupTaskQuery query;
    private final StaffRepository staff;
    private final PickupFulfillmentService pickup;
    private final MobilePickupTaskService tasks;
    private final AfterSalesRepository afterSales;
    public record Line(String requestId,String productName,String productCode,String status,boolean canPrepare,String blockReason,
                       String revision,List<MobilePickupTaskService.Allocation> allocations,String fulfilledAt) {}
    public record Preparation(String storeId,String orderId,String orderNo,String customerLabel,int pendingCount,int fulfilledCount,List<Line> lines) {}

    public Preparation read(Long orderId,Long employeeId) {
        var actor=employeeId==null?null:staff.findIdentityById(employeeId).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted()||actor.getStoreId()==null||actor.getStoreId()<=0)
            throw new IllegalArgumentException("需要当前有效门店员工身份");
        if(orderId==null||orderId<=0)throw new IllegalArgumentException("订单编号无效");
        var raw=query.findOrderLinesForOrder(actor.getStoreId(),orderId);
        if(raw.isEmpty())throw new IllegalArgumentException("订单不存在、无提货请求或不属于本店");
        var ids=raw.stream().map(PickupTaskQuery.OrderLine::requestId).collect(Collectors.toSet());
        var sources=pickup.listPickupRequestsForOrders(actor.getStoreId(),List.of(orderId));
        if(ids.size()!=raw.size()||sources.size()!=raw.size()||!ids.equals(sources.stream().map(PickupWorkItemVO::getId).collect(Collectors.toSet()))
                ||sources.stream().anyMatch(p->!Objects.equals(p.getStoreId(),actor.getStoreId())||!Objects.equals(p.getOrderId(),orderId)
                ||!("PENDING".equals(p.getStatus())||"FULFILLED".equals(p.getStatus()))))
            throw new IllegalStateException("原提货清单已变化或证据不完整，请重新读取");
        // 包含已拒绝的售后历史，使暂停后撤销也不能悄悄复用暂停前的本机勾选。
        var history=afterSales.findByOrderId(orderId).stream().sorted(Comparator.comparing(AfterSales::getId,Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(a->String.valueOf(a.getId())+":"+a.getStatus()+":"+a.getUpdatedAt()).toList();
        var sorted=sources.stream().sorted(Comparator.comparing(PickupWorkItemVO::getId)).toList();
        var lines=sorted.stream().map(p->{
            var v=tasks.view(p);
            return new Line(v.requestId(),v.productName(),StringUtils.hasText(p.getProductCode())?p.getProductCode():null,v.status(),v.canConfirm(),v.blockReason(),
                    v.canConfirm()?revision(v,history):null,v.allocations(),v.fulfilledAt());
        }).toList();
        int pending=(int)lines.stream().filter(l->"PENDING".equals(l.status())).count();
        return new Preparation(actor.getStoreId().toString(),orderId.toString(),raw.get(0).orderNo(),MobilePickupTaskService.customer(raw),pending,lines.size()-pending,lines);
    }
    private static String revision(MobilePickupTaskService.Preview preview,List<String> history) {
        var parts=new ArrayList<String>();parts.add("PICKUP_PREPARATION_V1");parts.add(preview.requestId());parts.add(preview.confirmationKey());
        parts.add(String.valueOf(preview.productName()));parts.addAll(history);
        preview.allocations().stream().sorted(Comparator.comparing(MobilePickupTaskService.Allocation::id)).forEach(a->{
            parts.add(a.id());parts.add(a.batchId());parts.add(a.internalBatchNo());parts.add(a.locationId());parts.add(a.locationName());parts.add(a.quantity());parts.add(a.unit());
        });
        String raw=parts.stream().map(s->s.length()+":"+s).collect(Collectors.joining());
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException("无法生成备货核对摘要",error);}
    }
}
