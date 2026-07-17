package com.agri_system.application.performance;

import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.event.CustomerFirstEffectiveEvent;
import com.agri_system.application.performance.event.CustomerRechargeEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.performance.job.PerformanceSettlementJob;
import com.agri_system.application.performance.listener.PerformanceEventListener;
import com.agri_system.application.customer.service.CustomerSettlementService;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.model.StaffCustomerRelation;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.repository.*;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PerformanceModuleTest {

    private PerformanceDomainService performanceDomainService;

    @BeforeEach
    void setup() {
        performanceDomainService = new PerformanceDomainService();
    }

    @Test
    void calculationTest() {
        CommissionRule rule = new CommissionRule();
        rule.setCalcMode("PROFIT_PERCENT");
        rule.setCalcValue(new BigDecimal("0.5"));
        var result = performanceDomainService.calculateSalesCommission(
                new BigDecimal("100"), new BigDecimal("20"), rule, true, new BigDecimal("0.2"));
        assertEquals(new BigDecimal("16.00"), result.amount());
    }

    @Test
    void rechargeRewardTest() {
        CommissionRule rule = new CommissionRule();
        rule.setCalcMode("FIXED");
        rule.setCalcValue(new BigDecimal("10"));
        CommissionRuleRepository commissionRuleRepository = mock(CommissionRuleRepository.class);
        when(commissionRuleRepository.findByCategory(CommissionRule.Category.RECHARGE.getCode()))
                .thenReturn(List.of(rule));
        StaffPerformanceLogRepository logRepository = mock(StaffPerformanceLogRepository.class);
        PerformanceEventListener listener = buildListener(commissionRuleRepository, logRepository);
        listener.onCustomerRecharge(new CustomerRechargeEvent(1L, new BigDecimal("200"), 9L));
        verify(logRepository).save(any(StaffPerformanceLog.class));
    }

    @Test
    void newCustomerTest() {
        CommissionRule rule = new CommissionRule();
        rule.setCalcMode("FIXED");
        rule.setCalcValue(new BigDecimal("20"));
        CommissionRuleRepository commissionRuleRepository = mock(CommissionRuleRepository.class);
        when(commissionRuleRepository.findByCategory(CommissionRule.Category.NEW_CUSTOMER.getCode()))
                .thenReturn(List.of(rule));
        StaffPerformanceLogRepository logRepository = mock(StaffPerformanceLogRepository.class);
        StaffCustomerRelation relation = new StaffCustomerRelation();
        relation.setStaffId(8L);
        relation.setStatus(StaffCustomerRelation.STATUS_APPROVED);
        StaffCustomerRelationRepository relationRepository = mock(StaffCustomerRelationRepository.class);
        when(relationRepository.findByCustomer(3L)).thenReturn(Optional.of(relation));
        PerformanceEventListener listener = new PerformanceEventListener(
                new PerformanceProperties(),
                new PerformanceDomainService(),
                commissionRuleRepository,
                logRepository,
                prepareEventRepo(),
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                mock(ProductRepository.class),
                relationRepository,
                mock(ApplicationEventPublisher.class),
                mock(CustomerSettlementService.class)
        );
        listener.onCustomerFirstEffective(new CustomerFirstEffectiveEvent(3L, 8L, 11L));
        verify(logRepository).save(any(StaffPerformanceLog.class));
    }

    @Test
    void refundDeductTest() {
        StaffPerformanceLogRepository logRepository = mock(StaffPerformanceLogRepository.class);
        StaffPerformanceLog log = new StaffPerformanceLog();
        log.setAmount(new BigDecimal("50"));
        log.setStaffId(1L);
        log.setStoreId(1L);
        log.setOrderItemId(101L);
        when(logRepository.findByOrderId(9L)).thenReturn(List.of(log));
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = new Order();
        order.setId(9L);
        order.setPayAmount(new BigDecimal("100"));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        OrderItem orderItem = new OrderItem();
        orderItem.setId(101L);
        orderItem.setQuantity(new BigDecimal("4"));
        when(orderItemRepository.findByOrderId(9L)).thenReturn(List.of(orderItem));
        PerformanceEventListener listener = new PerformanceEventListener(
                new PerformanceProperties(),
                new PerformanceDomainService(),
                mock(CommissionRuleRepository.class),
                logRepository,
                prepareEventRepo(),
                orderRepository,
                orderItemRepository,
                mock(ProductRepository.class),
                mock(StaffCustomerRelationRepository.class),
                mock(ApplicationEventPublisher.class),
                mock(CustomerSettlementService.class));
        listener.onOrderRefunded(new OrderRefundedEvent(
                9L,
                new BigDecimal("50"),
                Map.of(101L, new BigDecimal("2")),
                "AFTER_SALES:88"));
        verify(logRepository).saveBatch(any());
    }

    @Test
    void monthlySettlementTest() {
        StaffPerformanceLogRepository logRepository = mock(StaffPerformanceLogRepository.class);
        when(logRepository.findStaffIdsByMonth(any())).thenReturn(List.of(5L));
        when(logRepository.sumAmountByStaffAndPeriod(any(), any(), any())).thenReturn(new BigDecimal("80"));
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.sumPayAmountByStaffAndMonth(any(), any())).thenReturn(new BigDecimal("500"));
        StaffCustomerRelationRepository relationRepository = mock(StaffCustomerRelationRepository.class);
        when(relationRepository.countFirstEffectiveByMonth(any(), any())).thenReturn(2L);
        StaffSalaryBillRepository billRepository = mock(StaffSalaryBillRepository.class);
        when(billRepository.findByStaffAndMonth(any(), any())).thenReturn(Optional.empty());
        PerformanceSettlementJob job = new PerformanceSettlementJob(
                logRepository, billRepository, orderRepository, relationRepository);
        job.settleLastMonth();
        verify(billRepository).save(any());
    }

    private PerformanceEventListener buildListener(CommissionRuleRepository commissionRuleRepository,
                                                   StaffPerformanceLogRepository logRepository) {
        PerformanceEventRecordRepository eventRecordRepository = prepareEventRepo();
        return new PerformanceEventListener(
                new PerformanceProperties(),
                new PerformanceDomainService(),
                commissionRuleRepository,
                logRepository,
                eventRecordRepository,
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                mock(ProductRepository.class),
                mock(StaffCustomerRelationRepository.class),
                mock(ApplicationEventPublisher.class),
                mock(CustomerSettlementService.class)
        );
    }

    private PerformanceEventRecordRepository prepareEventRepo() {
        PerformanceEventRecordRepository repository = mock(PerformanceEventRecordRepository.class);
        when(repository.existsByEventKey(any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }
}
