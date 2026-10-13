package com.agri_system.application.performance.listener;

import com.agri_system.application.customer.service.CustomerSettlementService;
import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.event.CustomerRechargeEvent;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.model.PerformanceBizType;
import com.agri_system.domain.performance.model.PerformanceLogStatus;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.repository.*;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommissionFreezeListenerTest {

    @Mock PerformanceProperties properties;
    @Mock PerformanceDomainService performanceDomainService;
    @Mock CommissionRuleRepository commissionRuleRepository;
    @Mock StaffPerformanceLogRepository logRepository;
    @Mock PerformanceEventRecordRepository eventRecordRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductRepository productRepository;
    @Mock StaffCustomerRelationRepository relationRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CustomerSettlementService customerSettlementService;
    @Mock AfterSalesRepository afterSalesRepository;
    @Mock AfterSalesItemRepository afterSalesItemRepository;

    @InjectMocks PerformanceEventListener listener;

    private void stubFinishedCommon(Order order, OrderItem item, Product product) {
        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(item));
        when(productRepository.findById(item.getProductId())).thenReturn(Optional.of(product));
        // 计算结果给出正提成,触发写 log
        when(performanceDomainService.calculateSalesCommission(any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new PerformanceDomainService.CommissionResult(
                        new BigDecimal("10.00"), "desc", false));
        // 监听器 onOrderFinished 调 findBestSalesRule;空则 continue 不写 log
        CommissionRule rule = new CommissionRule();
        rule.setCalcMode("PROFIT_PERCENT");
        rule.setCalcValue(new BigDecimal("0.5"));
        when(commissionRuleRepository.findBestSalesRule(any(), any(), any())).thenReturn(Optional.of(rule));
    }

    @Test
    void finishedWritesFrozenWhenEnabled() {
        when(properties.isFreezeEnabled()).thenReturn(true);
        when(properties.getFreezeDays()).thenReturn(15);
        Order order = orderWithStaff();
        OrderItem item = itemWithProduct();
        stubFinishedCommon(order, item, product());

        listener.onOrderFinished(new OrderFinishedEvent(order.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog saved = cap.getValue().get(0);
        assertEquals(PerformanceLogStatus.FROZEN.getCode(), saved.getStatus());
        assertNotNull(saved.getFreezeUntil());
        assertTrue(saved.getFreezeUntil().isAfter(LocalDateTime.now().plusDays(14)));
    }

    @Test
    void finishedWritesImmediateWhenDisabled() {
        when(properties.isFreezeEnabled()).thenReturn(false);
        Order order = orderWithStaff();
        OrderItem item = itemWithProduct();
        stubFinishedCommon(order, item, product());

        listener.onOrderFinished(new OrderFinishedEvent(order.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog saved = cap.getValue().get(0);
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), saved.getStatus());
        assertNotNull(saved.getFreezeUntil());
    }

    @Test
    void refundNegativeInheritsFrozenPositive() {
        // 原正数仍冻结
        StaffPerformanceLog positive = new StaffPerformanceLog();
        positive.setStatus(PerformanceLogStatus.FROZEN.getCode());
        LocalDateTime until = LocalDateTime.now().plusDays(10);
        positive.setFreezeUntil(until);
        positive.setStaffId(2L);
        positive.setOrderItemId(50L);
        positive.setAmount(new BigDecimal("100"));

        Order order = new Order();
        order.setId(7L);
        order.setStaffId(2L);
        order.setPayAmount(new BigDecimal("100"));

        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(logRepository.findByOrderId(7L)).thenReturn(List.of(positive));

        listener.onOrderRefunded(new OrderRefundedEvent(7L, new BigDecimal("100"), null, "rk1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog neg = cap.getValue().get(0);
        assertEquals(PerformanceBizType.REFUND_DEDUCT.getCode(), neg.getBizType());
        // 关键断言:负数继承原正数冻结态
        assertEquals(PerformanceLogStatus.FROZEN.getCode(), neg.getStatus());
        assertEquals(until, neg.getFreezeUntil());
    }

    @Test
    void refundNegativeInheritsUnfrozenPositive() {
        StaffPerformanceLog positive = new StaffPerformanceLog();
        positive.setStatus(PerformanceLogStatus.UNFROZEN.getCode());
        LocalDateTime until = LocalDateTime.now().minusDays(1);
        positive.setFreezeUntil(until);
        positive.setStaffId(2L);
        positive.setOrderItemId(50L);
        positive.setAmount(new BigDecimal("100"));

        Order order = new Order();
        order.setId(7L);
        order.setStaffId(2L);
        order.setPayAmount(new BigDecimal("100"));

        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(logRepository.findByOrderId(7L)).thenReturn(List.of(positive));

        listener.onOrderRefunded(new OrderRefundedEvent(7L, new BigDecimal("100"), null, "rk2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StaffPerformanceLog>> cap = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveBatch(cap.capture());
        StaffPerformanceLog neg = cap.getValue().get(0);
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), neg.getStatus());
        // C2 关键断言:已解冻正数的退款负数 freeze_until 必须落在当期(now),不能沿用原正数旧 freeze_until
        assertNotNull(neg.getFreezeUntil());
        assertNotEquals(until, neg.getFreezeUntil());
        assertTrue(neg.getFreezeUntil().isAfter(until));
    }

    @Test
    void rechargeRewardLogMarkedImmediate() {
        // C1: 非销售提成(充值引导)写 log 时必须显式置 status=UNFROZEN + freeze_until=now,
        // 否则被结算的 freeze_until 过滤器静默丢弃 → 员工少算提成
        when(eventRecordRepository.existsByEventKey(any())).thenReturn(false);
        when(eventRecordRepository.save(any())).thenReturn(null);
        CommissionRule rule = new CommissionRule();
        rule.setStaffId(null); // 通配,selectRule 放行
        rule.setCalcMode("FIXED");
        rule.setCalcValue(new BigDecimal("10"));
        when(commissionRuleRepository.findByCategory(CommissionRule.Category.RECHARGE.getCode()))
                .thenReturn(List.of(rule));

        listener.onCustomerRecharge(new CustomerRechargeEvent(99L, new BigDecimal("100"), 5L));

        ArgumentCaptor<StaffPerformanceLog> cap = ArgumentCaptor.forClass(StaffPerformanceLog.class);
        verify(logRepository).save(cap.capture());
        StaffPerformanceLog saved = cap.getValue();
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), saved.getStatus());
        assertNotNull(saved.getFreezeUntil());
    }

    // —— 工厂方法 ——
    private Order orderWithStaff() {
        Order o = new Order();
        o.setId(1L);
        o.setStaffId(2L);
        o.setStoreId(3L);
        o.setCustomerId(null); // 跳过新客分支
        o.setPayAmount(new BigDecimal("100"));
        return o;
    }
    private OrderItem itemWithProduct() {
        OrderItem i = new OrderItem();
        i.setId(11L);
        i.setProductId(20L);
        i.setUnitPrice(new BigDecimal("100"));
        i.setQuantity(BigDecimal.ONE);
        i.setCostPriceSnapshot(new BigDecimal("20"));
        return i;
    }
    private Product product() {
        Product p = new Product();
        p.setId(20L);
        p.setAvgCostPrice(new BigDecimal("20"));
        return p;
    }
}
