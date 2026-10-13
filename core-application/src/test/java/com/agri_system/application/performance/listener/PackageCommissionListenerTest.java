package com.agri_system.application.performance.listener;

import com.agri_system.application.customer.service.CustomerSettlementService;
import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.repository.*;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 检验实际成交金额、实际成本和数量，外部仓储替身不替代提成计算。 */
class PackageCommissionListenerTest {
    private final CommissionRuleRepository rules = mock(CommissionRuleRepository.class);
    private final StaffPerformanceLogRepository logs = mock(StaffPerformanceLogRepository.class);
    private final PerformanceEventRecordRepository events = mock(PerformanceEventRecordRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final AfterSalesRepository afterSales = mock(AfterSalesRepository.class);
    private final AfterSalesItemRepository afterSalesItems = mock(AfterSalesItemRepository.class);
    private final List<AfterSales> finishedReturns = new ArrayList<>();
    private final List<StaffPerformanceLog> saved = new ArrayList<>();
    private PerformanceEventListener listener;

    @BeforeEach
    void setup() {
        PerformanceProperties properties = new PerformanceProperties();
        properties.setMaxCommissionRatio(BigDecimal.ONE);
        listener = new PerformanceEventListener(properties, new PerformanceDomainService(), rules, logs, events,
                orders, items, products, mock(StaffCustomerRelationRepository.class),
                mock(ApplicationEventPublisher.class), mock(CustomerSettlementService.class), afterSales, afterSalesItems);
        Set<String> eventKeys = new HashSet<>();
        when(events.existsByEventKey(anyString())).thenAnswer(call -> eventKeys.contains(call.getArgument(0)));
        when(events.save(any())).thenAnswer(call -> {
            com.agri_system.domain.performance.model.PerformanceEventRecord record = call.getArgument(0);
            eventKeys.add(record.getEventKey());
            return record;
        });
        doAnswer(call -> { saved.addAll(call.getArgument(0)); return null; }).when(logs).saveBatch(anyList());
        when(afterSales.findByOrderId(1L)).thenAnswer(call -> List.copyOf(finishedReturns));
        Order order = new Order(); order.setId(1L); order.setStaffId(9L); order.setStoreId(2L);
        order.setPayAmount(new BigDecimal("200"));
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    }

    @Test
    void ordinaryFixedCommissionUsesActualSoldQuantityInsteadOfSaleCostRatio() {
        OrderItem item = line(11L, 21L, "7", "100", "50");
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        when(rules.findBestSalesRule(21L, null, 9L)).thenReturn(Optional.of(rule("FIXED", "2", 1)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("14.00"), total());
    }

    @Test
    void percentageCommissionUsesAllocatedLineAmountWithoutRebuildingRoundedUnitPrice() {
        OrderItem item = line(11L, 21L, "3", "1.00", "0.50");
        item.setUnitPrice(new BigDecimal("0.333333333333"));
        item.setCostPriceSnapshot(new BigDecimal("0.166666666667"));
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        when(rules.findBestSalesRule(21L, null, 9L)).thenReturn(Optional.of(rule("PROFIT_PERCENT", "0.01", 1)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("0.01"), total(), "分摊成交额1元、实际成本0.5元，毛利1%应为1分");
    }

    @Test
    void profitCommissionUsesActualBatchCostRatherThanStaleCatalogCost() {
        OrderItem item = line(11L, 21L, "2", "100", "20");
        item.setCostPriceSnapshot(new BigDecimal("99"));
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        when(rules.findBestSalesRule(21L, null, 9L)).thenReturn(Optional.of(rule("PROFIT_PERCENT", "0.10", 1)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("8.00"), total());
    }

    @Test
    void legacyPackageLinesWithoutGroupDoNotInventPackageCountOrCombineMaterials() {
        OrderItem first = line(11L, 21L, "3", "100", "30"); first.setPackageId(8L);
        OrderItem second = line(12L, 22L, "2", "100", "30"); second.setPackageId(8L);
        when(items.findByOrderId(1L)).thenReturn(List.of(first, second));
        when(rules.findBestSalesRule(21L, null, 9L)).thenReturn(Optional.of(rule("FIXED", "2", 1)));
        when(rules.findBestSalesRule(22L, null, 9L)).thenReturn(Optional.of(rule("FIXED", "2", 1)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("10.00"), total(), "缺少历史套餐组与套数，只按已知商品数量计单品提成");
        verify(rules, never()).findBestSalesRule(isNull(), eq(8L), anyLong());
    }

    @Test
    void packageFixedCommissionCountsActualSetsOnceThenAllocatesToMaterialLogs() {
        OrderItem first = grouped(line(11L, 21L, "6", "80", "20"), 1, "2");
        OrderItem second = grouped(line(12L, 22L, "10", "120", "30"), 1, "2");
        first.setCostPriceSnapshot(new BigDecimal("1000"));
        when(items.findByOrderId(1L)).thenReturn(List.of(first, second));
        when(rules.findBestSalesRule(nullable(Long.class), eq(8L), eq(9L)))
                .thenReturn(Optional.of(rule("FIXED", "10", 5)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("20.00"), total(), "两套固定10元，只计20元，不按物料数量重复计提");
        assertEquals(new BigDecimal("8.00"), amountOf(11L));
        assertEquals(new BigDecimal("12.00"), amountOf(12L));
        when(logs.findByOrderId(1L)).thenReturn(List.copyOf(saved));
        saved.clear();
        listener.onOrderRefunded(new OrderRefundedEvent(1L, new BigDecimal("40"), Map.of(11L, new BigDecimal("3")), "PACKAGE-PARTIAL"));
        assertEquals(new BigDecimal("-4.00"), total(), "退回第一物料一半只回冲该物料分得提成的一半");
        assertEquals(11L, saved.get(0).getOrderItemId());
    }

    @Test
    void samePackageInDifferentGroupsHasIndependentActualSetQuantities() {
        List<OrderItem> lines = List.of(grouped(line(11L,21L,"6","80","20"),1,"2"),
                grouped(line(12L,22L,"10","120","30"),1,"2"),
                grouped(line(13L,21L,"3","40","10"),2,"1"),
                grouped(line(14L,22L,"5","60","15"),2,"1"));
        when(items.findByOrderId(1L)).thenReturn(lines);
        when(rules.findBestSalesRule(nullable(Long.class), eq(8L), eq(9L)))
                .thenReturn(Optional.of(rule("FIXED", "10", 5)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("30.00"), total(), "相同套餐2套与1套分别计提，不能把套餐ID当作唯一组");
        assertEquals(new BigDecimal("4.00"), amountOf(13L));
        assertEquals(new BigDecimal("6.00"), amountOf(14L));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("30.00"), total(), "重复完成事件不能再发提成");
    }

    @Test
    void packageProfitPercentUsesEntireGroupSaleAndActualCosts() {
        when(items.findByOrderId(1L)).thenReturn(List.of(grouped(line(11L,21L,"6","80","20"),1,"2"),
                grouped(line(12L,22L,"10","120","30"),1,"2")));
        when(rules.findBestSalesRule(nullable(Long.class), eq(8L), eq(9L)))
                .thenReturn(Optional.of(rule("PROFIT_PERCENT", "0.10", 5)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("15.00"), total(), "全组毛利150元，提成10%为15元");
        assertEquals(new BigDecimal("6.00"), amountOf(11L));
        assertEquals(new BigDecimal("9.00"), amountOf(12L));
    }

    @Test
    void packageSalesPercentAppliesGroupCapOnceAndPreservesEveryCent() {
        when(items.findByOrderId(1L)).thenReturn(List.of(grouped(line(11L,21L,"1","1","0.1"),1,"1"),
                grouped(line(12L,22L,"1","1","0.1"),1,"1"),grouped(line(13L,23L,"1","1","0.1"),1,"1")));
        CommissionRule rule = rule("SALES_PERCENT", "0.50", 5); rule.setMaxPerItem(new BigDecimal("0.10"));
        when(rules.findBestSalesRule(nullable(Long.class), eq(8L), eq(9L))).thenReturn(Optional.of(rule));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("0.10"), total(), "整组封顶10分，不是每个物料封顶10分");
        assertEquals(new BigDecimal("0.03"), amountOf(11L));
        assertEquals(new BigDecimal("0.03"), amountOf(12L));
        assertEquals(new BigDecimal("0.04"), amountOf(13L));
    }

    @Test
    void groupedPackageWithoutPackageRuleFallsBackToActualProductRules() {
        when(items.findByOrderId(1L)).thenReturn(List.of(grouped(line(11L,21L,"3","100","30"),1,"1"),
                grouped(line(12L,22L,"2","100","30"),1,"1")));
        when(rules.findBestSalesRule(21L,null,9L)).thenReturn(Optional.of(rule("FIXED","2",1)));
        when(rules.findBestSalesRule(22L,null,9L)).thenReturn(Optional.of(rule("FIXED","2",1)));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertEquals(new BigDecimal("10.00"), total());
    }

    @Test
    void splitReturnsFinallyDeductOriginalCommissionEvenWhenEarlyRefundsRoundToZero() {
        assertSplitRefunds("0.01");
    }

    @Test
    void splitReturnsNeverDeductMoreThanOriginalCommissionAfterRounding() {
        assertSplitRefunds("0.02");
    }

    @Test
    void pendingReturnQuantityDoesNotCountAsCompletedRefundFact() {
        when(items.findByOrderId(1L)).thenReturn(List.of(line(11L,21L,"3","1","0.10")));
        StaffPerformanceLog positive = new StaffPerformanceLog(); positive.setOrderItemId(11L);
        positive.setAmount(new BigDecimal("0.02")); positive.setStaffId(9L); positive.setStoreId(2L); positive.markImmediate();
        when(logs.findByOrderId(1L)).thenReturn(List.of(positive));
        AfterSales done = new AfterSales(); done.setId(1L); done.setStatus(AfterSales.STATUS_FINISHED);
        AfterSales pending = new AfterSales(); pending.setId(2L); pending.setStatus(AfterSales.STATUS_WAIT_REFUND);
        finishedReturns.addAll(List.of(done,pending));
        AfterSalesItem actual = new AfterSalesItem(); actual.setOrderItemId(11L); actual.setQuantity(BigDecimal.ONE);
        AfterSalesItem notRefunded = new AfterSalesItem(); notRefunded.setOrderItemId(11L); notRefunded.setQuantity(new BigDecimal("2"));
        when(afterSalesItems.findByAfterSalesId(1L)).thenReturn(List.of(actual));
        when(afterSalesItems.findByAfterSalesId(2L)).thenReturn(List.of(notRefunded));
        listener.onOrderRefunded(new OrderRefundedEvent(1L,new BigDecimal("0.33"),Map.of(11L,BigDecimal.ONE),"AFTER_SALES:1"));
        assertEquals(new BigDecimal("-0.01"),total(),"待退款的另外两件不能提前算作已完成退款并冲掉全部提成");
    }

    private void assertSplitRefunds(String originalCommission) {
        OrderItem material = line(11L,21L,"3","1","0.10");
        when(items.findByOrderId(1L)).thenReturn(List.of(material));
        StaffPerformanceLog positive = new StaffPerformanceLog();
        positive.setOrderItemId(11L); positive.setAmount(new BigDecimal(originalCommission));
        positive.setStaffId(9L); positive.setStoreId(2L); positive.markImmediate();
        when(logs.findByOrderId(1L)).thenReturn(List.of(positive));
        when(logs.findByBiz(eq(1L), eq(com.agri_system.domain.performance.model.PerformanceBizType.REFUND_DEDUCT.getCode())))
                .thenAnswer(call -> List.copyOf(saved));
        for (long index = 1; index <= 3; index++) {
            AfterSales completed = new AfterSales(); completed.setId(index); completed.setOrderId(1L);
            completed.setStatus(AfterSales.STATUS_FINISHED); finishedReturns.add(completed);
            AfterSalesItem returned = new AfterSalesItem(); returned.setAfterSalesId(index);
            returned.setOrderItemId(11L); returned.setQuantity(BigDecimal.ONE);
            when(afterSalesItems.findByAfterSalesId(index)).thenReturn(List.of(returned));
            listener.onOrderRefunded(new OrderRefundedEvent(1L, new BigDecimal("0.33"),
                    Map.of(11L,BigDecimal.ONE),"AFTER_SALES:" + index));
        }
        assertEquals(new BigDecimal(originalCommission).negate().setScale(2), total(),
                "累计退完三件，应精确冲回原提成，不能留尾分或超扣");
        listener.onOrderRefunded(new OrderRefundedEvent(1L, new BigDecimal("0.33"),
                Map.of(11L,BigDecimal.ONE),"AFTER_SALES:3"));
        assertEquals(new BigDecimal(originalCommission).negate().setScale(2), total(), "重复退款事件不能重复冲回");
    }

    private OrderItem grouped(OrderItem item, int groupNo, String packageQuantity) {
        item.setPackageId(8L); item.setPackageGroupNo(groupNo); item.setPackageQuantity(new BigDecimal(packageQuantity));
        item.setPackageName("田间套餐"); item.setPackageUnitPrice(new BigDecimal("100")); return item;
    }

    private BigDecimal amountOf(long orderItemId) {
        return saved.stream().filter(log -> Long.valueOf(orderItemId).equals(log.getOrderItemId()))
                .map(StaffPerformanceLog::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
    }

    private OrderItem line(long id, long productId, String quantity, String sale, String cost) {
        OrderItem item = new OrderItem(); item.setId(id); item.setOrderId(1L); item.setProductId(productId);
        item.setQuantity(new BigDecimal(quantity)); item.setTotalPrice(new BigDecimal(sale));
        item.setUnitPrice(new BigDecimal(sale).divide(item.getQuantity(), 12, java.math.RoundingMode.HALF_UP));
        item.setBaseQuantity(BigDecimal.ONE); item.setUnitCost(new BigDecimal(cost));
        item.setCostPriceSnapshot(new BigDecimal(cost).divide(item.getQuantity(), 12, java.math.RoundingMode.HALF_UP));
        return item;
    }

    private CommissionRule rule(String mode, String value, int category) {
        CommissionRule rule = new CommissionRule(); rule.setId(1L); rule.setRuleCategory(category);
        rule.setCalcMode(mode); rule.setCalcValue(new BigDecimal(value)); return rule;
    }

    private BigDecimal total() {
        return saved.stream().map(StaffPerformanceLog::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }
}
