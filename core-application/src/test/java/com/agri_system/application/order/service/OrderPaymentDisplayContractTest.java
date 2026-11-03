package com.agri_system.application.order.service;

import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 从真实列表方法观察展示规则，不mock待提取汇总方法。 */
class OrderPaymentDisplayContractTest {
    private OrderApplicationService service;
    private Order order;
    private OrderPaymentRepository payments;
    @BeforeEach void setup() {
        var orders=mock(OrderRepository.class);payments=mock(OrderPaymentRepository.class);
        var finance=mock(OrderPaymentService.class);
        order=new Order();order.setId(1L);order.setOrderStatus(6);order.setOrderType(2);
        order.setTotalAmount(new BigDecimal("100"));order.setDiscountAmount(new BigDecimal("10"));order.setCouponAmount(new BigDecimal("5"));order.setPayAmount(new BigDecimal("55"));
        var page=new Page<Order>();page.setRecords(List.of(order));page.setTotalRow(1);
        when(orders.findPage(anyInt(),anyInt(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(page);
        when(finance.financialSummary(order)).thenReturn(new OrderPaymentService.FinancialSummary(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        service=new OrderApplicationService(new OrderQueryService(orders,
                mock(OrderItemRepository.class),payments,mock(OrderDeliveryRepository.class),
                null,null,null,finance,null,null,null,null),null,null,null,null,null);
    }
    private com.agri_system.application.order.dto.vo.OrderPageVO view() {return service.queryOrderPage(new com.agri_system.application.order.dto.query.OrderPageQuery()).getRecords().get(0);}
    private OrderPayment payment(int type,String amount) {var p=new OrderPayment();p.setPaymentType(type);p.setAmount(new BigDecimal(amount));return p;}
    @Test void 流水汇总排除券额并分开赊销及专用金实收() {
        when(payments.findByOrderId(1L)).thenReturn(List.of(payment(8,"10"),payment(4,"20"),payment(5,"5"),payment(6,"30")));
        var row=view();assertEquals(0,new BigDecimal("30").compareTo(row.getReceivedAmount()));assertEquals(0,new BigDecimal("30").compareTo(row.getCreditAmount()));
        assertEquals(8,row.getPaymentMethod());assertEquals("专用金",row.getPaymentMethodName());
    }
    @Test void 无流水沿原单净应收区分实收和赊销且不重加优惠() {
        when(payments.findByOrderId(1L)).thenReturn(List.of());order.setPaymentMethod(4);
        var row=view();assertEquals(0,new BigDecimal("55").compareTo(row.getReceivedAmount()));assertEquals(0,new BigDecimal("30").compareTo(row.getCreditAmount()));assertEquals("本金余额",row.getPaymentMethodName());
        order.setPaymentMethod(6);row=view();assertEquals(0,BigDecimal.ZERO.compareTo(row.getReceivedAmount()));assertEquals(0,new BigDecimal("85").compareTo(row.getCreditAmount()));assertEquals("赊销",row.getPaymentMethodName());
    }
}
