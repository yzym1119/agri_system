package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import com.agri_system.domain.order.query.PickupTaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库管查看本店既有提货事实；不按请求创建人限制，也不读取金额和成本。
 *
 * <p>列表按原订单聚合展示：底层仍是按订单明细存在的提货请求，聚合只是视图，
 * 不新建订单、库存或第二套待交事实；客户只给脱敏称谓，便于交付时认人。</p>
 */
@Service
@RequiredArgsConstructor
public class MobilePickupTaskService {
    /** 一页最多 20 个原订单；SQL 取 21 个订单用于判断是否还有下一页。 */
    private static final int PAGE_ORDERS = 20;
    private static final Set<String> STATES = Set.of("PENDING", "FULFILLED");

    private final PickupTaskQuery taskQuery;
    private final StaffRepository staff;
    private final PickupFulfillmentService pickup;

    /** 整单下的一条提货请求；unit/remainingQuantity 只对待交项给出，历史项不反推已交数量。 */
    public record OrderLineView(String requestId,String productName,String unit,String remainingQuantity,
                                String status,boolean canConfirm,String blockReason,String fulfilledAt) {}
    /** 原订单聚合：pendingCount/fulfilledCount 覆盖该订单全部提货请求，不只本页筛选状态。 */
    public record OrderView(String orderId,String orderNo,String customerLabel,int pendingCount,int fulfilledCount,
                            List<OrderLineView> lines) {}
    public record Orders(String storeId,String state,List<OrderView> orders,String nextCursor) {}
    public record Preview(String storeId,String requestId,String orderId,String orderNo,String productName,String status,
                          boolean canConfirm,String blockReason,String confirmationKey,List<Allocation> allocations,String fulfilledAt) {}
    public record Allocation(String id,String batchId,String internalBatchNo,String locationId,String locationName,String quantity,String unit) {}

    public Orders listOrders(Long employeeId,String state,Long after) {
        var actor=actor(employeeId);
        if (!STATES.contains(state)) throw new IllegalArgumentException("提货状态无效");
        if (after!=null && after<=0) throw new IllegalArgumentException("提货游标无效");
        var grouped=new LinkedHashMap<Long,List<PickupTaskQuery.OrderLine>>();
        taskQuery.findOrderLines(actor.getStoreId(),state,after)
                .forEach(line->grouped.computeIfAbsent(line.orderId(),key->new ArrayList<>()).add(line));
        var pageIds=grouped.keySet().stream().limit(PAGE_ORDERS).toList();
        if (pageIds.isEmpty()) return new Orders(actor.getStoreId().toString(),state,List.of(),null);
        var items=pickup.listPickupRequestsForOrders(actor.getStoreId(),pageIds).stream()
                .collect(Collectors.toMap(PickupWorkItemVO::getId,Function.identity(),(left,right)->left));
        var orders=pageIds.stream().map(id->order(id,grouped.get(id),items)).toList();
        String cursor=grouped.size()>PAGE_ORDERS?pageIds.get(pageIds.size()-1).toString():null;
        return new Orders(actor.getStoreId().toString(),state,orders,cursor);
    }

    private OrderView order(Long orderId,List<PickupTaskQuery.OrderLine> lines,Map<Long,PickupWorkItemVO> items) {
        var views=lines.stream().map(line->line(line,items.get(line.requestId()))).toList();
        int pending=(int)views.stream().filter(view->"PENDING".equals(view.status())).count();
        return new OrderView(orderId.toString(),lines.get(0).orderNo(),customer(lines),pending,views.size()-pending,views);
    }

    private OrderLineView line(PickupTaskQuery.OrderLine source,PickupWorkItemVO item) {
        boolean pending="PENDING".equals(source.status());
        String blocked=item==null?null:item.getFulfillmentBlockReason();
        boolean canConfirm=item!=null&&item.isCanFulfill();
        if(item==null&&pending) blocked="原出库记录待核对，请联系店长";
        return new OrderLineView(source.requestId().toString(),source.productName(),
                pending&&item!=null?item.getBaseUnitName():null,
                pending&&item!=null?plain(item.getRemainingBaseQuantity()):null,
                source.status(),canConfirm,canConfirm?null:blocked,
                source.fulfilledAt()==null?null:source.fulfilledAt().toString());
    }

    public Preview preview(Long requestId,Long employeeId) {
        var actor=actor(employeeId);
        var source=pickup.previewPickupRequest(requestId,employeeId);
        if(!Objects.equals(actor.getStoreId(),source.getStoreId())) throw new IllegalArgumentException("不能读取其他门店的提货请求");
        return view(source);
    }

    /** 共用原工作项到移动批次视图的校验，不重新计算余量。 */
    Preview view(PickupWorkItemVO source) {
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

    /** 客户只给首字加星，不返回手机号、地址或客户编号。 */
    static String customer(List<PickupTaskQuery.OrderLine> lines) {
        return lines.stream().map(PickupTaskQuery.OrderLine::customerName).filter(StringUtils::hasText)
                .findFirst().map(name->{
                    String value=name.trim();
                    return value.length()<=1?"客户":value.substring(0,1)+"**";
                }).orElse(null);
    }

    private static String plain(BigDecimal value) { return value==null?null:value.stripTrailingZeros().toPlainString(); }

    private Staff actor(Long id) {
        var actor=id==null?null:staff.findIdentityById(id).orElse(null);
        if(actor==null || !actor.isActive() || actor.hasBeenDeleted() || actor.getStoreId()==null || actor.getStoreId()<=0)
            throw new IllegalArgumentException("需要当前有效门店员工身份");
        return actor;
    }
}
