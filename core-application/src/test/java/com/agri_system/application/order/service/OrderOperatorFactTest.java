package com.agri_system.application.order.service;

import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 原订单只读操作员工事实，不能从姓名或当前登录身份猜测。 */
class OrderOperatorFactTest {
    @Test void detailExposesPersistedStaffIdEvenWhenNameLookupHasNoResult() throws Exception {
        assertEquals(8L, operator(8L, 99L, 1));
    }

    @Test void posHistoricalCreatedByIsFallbackButMissingFactsRemainUnknown() throws Exception {
        assertEquals(99L, operator(null, 99L, 1));
        assertNull(operator(null, null, 1));
    }

    @Test void onlineCreatedByCustomerMustNotBecomeOperatorStaffId() throws Exception {
        assertNull(operator(null, 101L, 2));
    }

    private Long operator(Long staffId, Long createdBy, int type) throws Exception {
        OrderApplicationService service = mock(OrderApplicationService.class, CALLS_REAL_METHODS);
        OrderRepository orders = mock(OrderRepository.class);
        OrderPaymentService payments = mock(OrderPaymentService.class);
        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "itemRepository", mock(OrderItemRepository.class));
        ReflectionTestUtils.setField(service, "paymentRepository", mock(OrderPaymentRepository.class));
        ReflectionTestUtils.setField(service, "afterSalesRepository", mock(AfterSalesRepository.class));
        ReflectionTestUtils.setField(service, "paymentService", payments);
        ReflectionTestUtils.setField(service, "storeRepository", mock(StoreRepository.class));
        ReflectionTestUtils.setField(service, "staffRepository", mock(StaffRepository.class));
        ReflectionTestUtils.setField(service, "deliveryRepository", mock(OrderDeliveryRepository.class));
        Order order = new Order(); order.setId(77L); order.setStaffId(staffId); order.setCreatedBy(createdBy);
        order.setOrderType(type); order.setOrderStatus(5); order.setTotalAmount(BigDecimal.TEN); order.setPayAmount(BigDecimal.TEN);
        when(orders.findById(77L)).thenReturn(Optional.of(order));
        when(payments.financialSummary(order)).thenReturn(new OrderPaymentService.FinancialSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        var detail = service.getOrderDetail(77L);
        return (Long) detail.getClass().getMethod("getOperatorStaffId").invoke(detail);
    }
}
