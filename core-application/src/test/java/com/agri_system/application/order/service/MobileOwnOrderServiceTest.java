package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.*;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobileOwnOrderServiceTest {
    StaffRepository staff=mock(StaffRepository.class);OrderRepository orders=mock(OrderRepository.class);
    OrderQueryService query=mock(OrderQueryService.class);MobileOwnOrderService service=new MobileOwnOrderService(staff,orders,query);
    Staff actor;Order order;
    @BeforeEach void setup(){actor=new Staff();actor.setId(8L);actor.setStoreId(3L);actor.setStatus(1);actor.setDeleted(0);when(staff.findIdentityById(8L)).thenReturn(Optional.of(actor));order=order(50);when(orders.findById(50L)).thenReturn(Optional.of(order));}
    Order order(long id){var o=new Order();o.setId(id);o.setStoreId(3L);o.setStaffId(8L);o.setOrderType(1);o.setOrderStatus(6);return o;}
    @Test void listBindsActualStoreAndStaffAndReturnsTwentyWithOriginalCursor() {
        var raw=new ArrayList<Order>();for(long n=30;n>=10;n--)raw.add(order(n));
        when(orders.findOwnSales(eq(3L),eq(8L),isNull(),any(),any(),eq(6))).thenReturn(raw);
        when(query.summarizeOrders(anyList())).thenAnswer(call->((List<Order>)call.getArgument(0)).stream().map(o->{var v=new OrderPageVO();v.setId(o.getId());v.setOrderNo("O"+o.getId());v.setStoreId(3L);v.setOrderStatus(6);return v;}).toList());
        var result=service.list(8L,null,LocalDate.of(2026,11,9),LocalDate.of(2026,11,9),6);
        assertEquals("3",result.storeId());assertEquals(20,result.orders().size());assertEquals("11",result.nextCursor());
        verify(orders).findOwnSales(3L,8L,null,LocalDate.of(2026,11,9).atStartOfDay(),LocalDate.of(2026,11,10).atStartOfDay(),6);
    }
    @Test void rejectsOtherEmployeeOtherStoreOnlineAndMissingOriginalStaffBeforeDetailRead() {
        for(int n=0;n<4;n++){order=order(50);if(n==0)order.setStaffId(9L);if(n==1)order.setStoreId(4L);if(n==2)order.setOrderType(2);if(n==3){order.setStaffId(null);order.setCreatedBy(8L);}when(orders.findById(50L)).thenReturn(Optional.of(order));assertThrows(IllegalArgumentException.class,()->service.detail(8L,50L));}
        verifyNoInteractions(query);
    }
    @Test void disabledMissingStoreAndInvalidFiltersDoNotReadOrders() {
        actor.setStatus(0);assertThrows(IllegalArgumentException.class,()->service.list(8L,null,null,null,null));actor.setStatus(1);
        actor.setDeleted(1);assertThrows(IllegalArgumentException.class,()->service.list(8L,null,null,null,null));actor.setDeleted(0);
        actor.setStoreId(null);assertThrows(IllegalArgumentException.class,()->service.detail(8L,50L));actor.setStoreId(3L);
        assertThrows(IllegalArgumentException.class,()->service.list(8L,0L,null,null,null));assertThrows(IllegalArgumentException.class,()->service.list(8L,null,null,null,10));
        assertThrows(IllegalArgumentException.class,()->service.list(8L,null,LocalDate.of(2026,11,10),LocalDate.of(2026,11,9),null));verifyNoInteractions(orders,query);
    }
    @Test void detailPreservesMoneyAndOriginalPackageSnapshotsWithoutContactOrActionFields() {
        var d=new OrderDetailVO();d.setId(50L);d.setStoreId(3L);d.setOperatorStaffId(8L);d.setOrderType(1);d.setOrderStatus(9);d.setOrderNo("O50");d.setPayAmount(new BigDecimal("65.00"));d.setReceivedAmount(new BigDecimal("25.00"));d.setCashRefundedAmount(new BigDecimal("10.00"));d.setRemainingCreditAmount(null);
        d.setCustomerName("张三");d.setReceiverPhone("PRIVATE");d.setDeliveryConfirmationKey("SECRET");
        var line=new OrderItemVO();line.setId(Long.MAX_VALUE);line.setProductName("物料");line.setQuantity(new BigDecimal("2.0000"));line.setUnit("袋");line.setTotalPrice(new BigDecimal("25.00"));line.setRefundedQuantity(BigDecimal.ONE);line.setPackageGroupNo(1);line.setPackageName("成交套餐");line.setPackageQuantity(BigDecimal.ONE);line.setPackageUnitPrice(new BigDecimal("65.00"));d.setItems(List.of(line));when(query.getOrderDetail(50L)).thenReturn(d);
        var result=service.detail(8L,50L);assertEquals("65",result.order().finance().payAmount());assertEquals("10",result.order().finance().cashRefundedAmount());assertNull(result.order().finance().remainingCreditAmount());
        assertEquals("9223372036854775807",result.items().get(0).itemId());assertEquals("2",result.items().get(0).quantity());assertEquals("成交套餐",result.items().get(0).packageName());assertFalse(result.toString().contains("PRIVATE"));assertFalse(result.toString().contains("SECRET"));
    }
    @Test void secondReadRejectsChangedOwnershipAndDoesNotUseCreatedByFallback() {
        var changed=order(50);changed.setStaffId(null);changed.setCreatedBy(8L);when(orders.findById(50L)).thenReturn(Optional.of(order),Optional.of(changed));
        var d=new OrderDetailVO();d.setId(50L);d.setStoreId(3L);d.setOperatorStaffId(8L);d.setOrderType(1);when(query.getOrderDetail(50L)).thenReturn(d);
        assertThrows(IllegalArgumentException.class,()->service.detail(8L,50L));
    }
    @Test void mobileListAndDetailUseTheSameRealMoneyCalculationAsPc() {
        var lines=mock(com.agri_system.domain.order.repository.OrderItemRepository.class);
        var payments=mock(com.agri_system.domain.order.repository.OrderPaymentRepository.class);
        var delivery=mock(com.agri_system.domain.order.repository.OrderDeliveryRepository.class);
        var finance=mock(OrderPaymentService.class);
        order.setPayAmount(new BigDecimal("65"));order.setTotalAmount(new BigDecimal("65"));order.setDiscountAmount(BigDecimal.ZERO);order.setCouponAmount(BigDecimal.ZERO);
        order.setOrderNo("O50");order.setPaymentMethod(10);
        var cash=new com.agri_system.domain.order.model.OrderPayment();cash.setPaymentType(1);cash.setAmount(new BigDecimal("25"));
        var credit=new com.agri_system.domain.order.model.OrderPayment();credit.setPaymentType(6);credit.setAmount(new BigDecimal("40"));
        when(payments.findByOrderId(50L)).thenReturn(List.of(cash,credit));
        when(finance.financialSummary(order)).thenReturn(new OrderPaymentService.FinancialSummary(new BigDecimal("10"),new BigDecimal("5"),new BigDecimal("23"),new BigDecimal("7")));
        var real=new OrderQueryService(orders,lines,payments,delivery,mock(com.agri_system.domain.store.repository.StoreRepository.class),null,mock(com.agri_system.domain.after_sales.repository.AfterSalesRepository.class),finance,
                mock(com.agri_system.domain.product.repository.ProductRepository.class),staff,null,null);
        var mobile=new MobileOwnOrderService(staff,orders,real);
        when(orders.findOwnSales(3L,8L,null,null,null,null)).thenReturn(List.of(order));
        var page=new com.mybatisflex.core.paginate.Page<Order>();page.setRecords(List.of(order));
        when(orders.findPage(anyInt(),anyInt(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(page);
        var pc=real.queryOrderPage(new com.agri_system.application.order.dto.query.OrderPageQuery()).getRecords().get(0);
        var list=mobile.list(8L,null,null,null,null).orders().get(0).finance();var detail=mobile.detail(8L,50L).order().finance();
        assertEquals(list,detail);assertEquals(0,new BigDecimal(list.receivedAmount()).compareTo(pc.getReceivedAmount()));
        assertEquals("25",list.receivedAmount());assertEquals("40",list.creditAmount());assertEquals("23",list.remainingCreditAmount());assertEquals("7",list.repaidAmount());assertEquals("10",list.creditReductionAmount());assertEquals("5",list.cashRefundedAmount());
    }
}
