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

    @Test void customerAndStaffIdCollisionMustNotInventOnlineOperatorName() {
        for (int type : new int[]{2, 3, 99}) {
            StaffRepository staff = mock(StaffRepository.class);
            var collision = new com.agri_system.domain.store.model.Staff();collision.setId(101L);collision.setName("同编号员工");
            when(staff.findById(101L)).thenReturn(Optional.of(collision));
            var result = detail(null, 101L, type, staff);
            assertNull(result.getOperatorStaffId());
            assertNull(result.getOperatorName(), "非POS订单不能按客户createdBy查员工姓名");
            verifyNoInteractions(staff);
        }
    }

    @Test void posNameUsesPersistedStaffFirstAndHistoricalCreatedByOnlyAsFallback() {
        for (boolean historical : new boolean[]{false, true}) {
            StaffRepository staff = mock(StaffRepository.class);
            long expected = historical ? 99L : 8L;
            var employee = new com.agri_system.domain.store.model.Staff();employee.setId(expected);employee.setName("原收银员");
            when(staff.findById(expected)).thenReturn(Optional.of(employee));
            var result = detail(historical ? null : 8L, 99L, 1, staff);
            assertEquals(expected, result.getOperatorStaffId());assertEquals("原收银员", result.getOperatorName());
            verify(staff, times(1)).findById(expected);
        }
    }

    private Long operator(Long staffId, Long createdBy, int type) throws Exception {
        return detail(staffId, createdBy, type, mock(StaffRepository.class)).getOperatorStaffId();
    }

    private com.agri_system.application.order.dto.vo.OrderDetailVO detail(Long staffId, Long createdBy, int type, StaffRepository staff) {
        OrderApplicationService service = mock(OrderApplicationService.class, CALLS_REAL_METHODS);
        OrderRepository orders = mock(OrderRepository.class);
        OrderPaymentService payments = mock(OrderPaymentService.class);
        ReflectionTestUtils.setField(service, "orderRepository", orders);
        ReflectionTestUtils.setField(service, "itemRepository", mock(OrderItemRepository.class));
        ReflectionTestUtils.setField(service, "paymentRepository", mock(OrderPaymentRepository.class));
        ReflectionTestUtils.setField(service, "afterSalesRepository", mock(AfterSalesRepository.class));
        ReflectionTestUtils.setField(service, "paymentService", payments);
        ReflectionTestUtils.setField(service, "storeRepository", mock(StoreRepository.class));
        ReflectionTestUtils.setField(service, "staffRepository", staff);
        ReflectionTestUtils.setField(service, "deliveryRepository", mock(OrderDeliveryRepository.class));
        Order order = new Order(); order.setId(77L); order.setStaffId(staffId); order.setCreatedBy(createdBy);
        order.setOrderType(type); order.setOrderStatus(5); order.setTotalAmount(BigDecimal.TEN); order.setPayAmount(BigDecimal.TEN);
        when(orders.findById(77L)).thenReturn(Optional.of(order));
        when(payments.financialSummary(order)).thenReturn(new OrderPaymentService.FinancialSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        return service.getOrderDetail(77L);
    }
}
