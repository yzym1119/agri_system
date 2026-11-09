package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.MobileOwnOrderVO;
import com.agri_system.application.order.dto.vo.OrderPageVO;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 只查原staff_id的本店POS单，不承载任何钱货或履约写入。 */
@Service
@RequiredArgsConstructor
public class MobileOwnOrderService {
    private final StaffRepository staff;
    private final OrderRepository orders;
    private final OrderQueryService query;

    public MobileOwnOrderVO.Orders list(Long staffId,Long after,LocalDate from,LocalDate to,Integer state) {
        var actor=actor(staffId);
        if(after!=null&&after<=0)throw new IllegalArgumentException("订单游标无效");
        if(state!=null&&(state<1||state>9))throw new IllegalArgumentException("订单状态无效");
        if((from!=null&&from.getYear()<1900)||(to!=null&&(to.getYear()<1900||to.getYear()>9998))
                ||(from!=null&&from.getYear()>9998)||(from!=null&&to!=null&&from.isAfter(to)))
            throw new IllegalArgumentException("查询日期范围无效");
        var raw=orders.findOwnSales(actor.getStoreId(),actor.getId(),after,
                from==null?null:from.atStartOfDay(),to==null?null:to.plusDays(1).atStartOfDay(),state);
        for(var order:raw)check(order,actor);
        boolean more=raw.size()>20;
        var page=List.copyOf(raw.subList(0,Math.min(raw.size(),20)));
        var rows=query.summarizeOrders(page).stream().map(this::summary).toList();
        return new MobileOwnOrderVO.Orders(actor.getStoreId().toString(),rows,
                more?page.get(page.size()-1).getId().toString():null);
    }

    public MobileOwnOrderVO.Detail detail(Long staffId,Long orderId) {
        var actor=actor(staffId);
        if(orderId==null||orderId<=0)throw new IllegalArgumentException("订单编号无效");
        owned(orderId,actor);
        var d=query.getOrderDetail(orderId);
        owned(orderId,actor);
        if(!Objects.equals(d.getId(),orderId)||!Objects.equals(d.getStoreId(),actor.getStoreId())
                ||!Objects.equals(d.getOperatorStaffId(),actor.getId())||!Integer.valueOf(1).equals(d.getOrderType()))
            throw new IllegalArgumentException("订单归属已变化，请重新查询");
        var delivery=d.getDelivery();
        var summary=new MobileOwnOrderVO.Summary(d.getId().toString(),d.getOrderNo(),time(d.getCreatedAt()),d.getOrderStatus(),label(d.getCustomerId(),d.getCustomerName()),
                finance(d.getPayAmount(),d.getReceivedAmount(),d.getCreditAmount(),d.getCreditReductionAmount(),d.getCashRefundedAmount(),d.getRemainingCreditAmount(),d.getRepaidAmount()),
                delivery==null?null:delivery.getDeliveryType(),delivery==null?null:delivery.getDeliveryStatus());
        var items=d.getItems()==null?List.<MobileOwnOrderVO.Item>of():d.getItems().stream().map(i->new MobileOwnOrderVO.Item(i.getId().toString(),i.getProductName(),decimal(i.getQuantity()),i.getUnit(),decimal(i.getTotalPrice()),
                decimal(i.getRefundedQuantity()),decimal(i.getRefundedAmount()),i.getPackageGroupNo(),i.getPackageName(),decimal(i.getPackageQuantity()),decimal(i.getPackageUnitPrice()))).toList();
        return new MobileOwnOrderVO.Detail(actor.getStoreId().toString(),summary,items);
    }

    private Staff actor(Long id) {
        var actor=id==null?null:staff.findIdentityById(id).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted()||actor.getStoreId()==null||actor.getStoreId()<=0)
            throw new IllegalArgumentException("需要当前有效门店员工身份");
        return actor;
    }
    private void owned(Long id,Staff actor){check(orders.findById(id).orElseThrow(()->new IllegalArgumentException("订单不存在或无权查看")),actor);}
    private void check(Order order,Staff actor) {
        if(order==null||!Objects.equals(order.getStaffId(),actor.getId())||!Objects.equals(order.getStoreId(),actor.getStoreId())
                ||!Integer.valueOf(1).equals(order.getOrderType())||Integer.valueOf(1).equals(order.getDeleted()))
            throw new IllegalArgumentException("订单不存在或无权查看");
    }
    private MobileOwnOrderVO.Summary summary(OrderPageVO v) {
        return new MobileOwnOrderVO.Summary(v.getId().toString(),v.getOrderNo(),time(v.getCreatedAt()),v.getOrderStatus(),label(v.getCustomerId(),v.getCustomerName()),
                finance(v.getPayAmount(),v.getReceivedAmount(),v.getCreditAmount(),v.getCreditReductionAmount(),v.getCashRefundedAmount(),v.getRemainingCreditAmount(),v.getRepaidAmount()),v.getDeliveryType(),v.getDeliveryStatus());
    }
    private static MobileOwnOrderVO.Finance finance(BigDecimal net,BigDecimal received,BigDecimal credit,BigDecimal reduction,BigDecimal refunded,BigDecimal remaining,BigDecimal repaid) {
        return new MobileOwnOrderVO.Finance(decimal(net),decimal(received),decimal(credit),decimal(reduction),decimal(refunded),decimal(remaining),decimal(repaid));
    }
    private static String decimal(BigDecimal v){return v==null?null:v.stripTrailingZeros().toPlainString();}
    private static String time(LocalDateTime v){return v==null?null:v.toString();}
    private static String label(Long customer,String name){return customer==null?"散客":name==null||name.isBlank()?"客户资料待核对":name.substring(0,name.offsetByCodePoints(0,1))+"**";}
}
