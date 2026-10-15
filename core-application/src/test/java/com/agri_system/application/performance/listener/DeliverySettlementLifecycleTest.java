package com.agri_system.application.performance.listener;

import com.agri_system.application.customer.service.CustomerSettlementService;
import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.performance.event.CustomerFirstEffectiveEvent;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.model.PerformanceBizType;
import com.agri_system.domain.performance.model.PerformanceEventRecord;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.model.StaffCustomerRelation;
import com.agri_system.domain.performance.repository.*;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用真实结算和提成计算验证“先退货、后首次签收”的时序，仓储只保存本次事实。 */
class DeliverySettlementLifecycleTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final AfterSalesRepository returns = mock(AfterSalesRepository.class);
    private final AfterSalesItemRepository returnItems = mock(AfterSalesItemRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerWalletRepository wallets = mock(CustomerWalletRepository.class);
    private final WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
    private final PerformanceEventRecordRepository events = mock(PerformanceEventRecordRepository.class);
    private final StaffPerformanceLogRepository logs = mock(StaffPerformanceLogRepository.class);
    private final CommissionRuleRepository rules = mock(CommissionRuleRepository.class);
    private final StaffCustomerRelationRepository relations = mock(StaffCustomerRelationRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final PerformanceProperties properties = new PerformanceProperties();
    private final Set<String> eventKeys = new HashSet<>();
    private final List<StaffPerformanceLog> savedLogs = new ArrayList<>();
    private final List<WalletTransaction> savedTransactions = new ArrayList<>();
    private final List<AfterSales> completedReturns = new ArrayList<>();
    private final Customer customer = new Customer();
    private final CustomerWallet points = new CustomerWallet();
    private final Order order = new Order();
    private PerformanceEventListener listener;

    @BeforeEach void setup() {
        customer.setId(2L); customer.setTotalOrderAmount(new BigDecimal("500")); customer.setTotalOrderCount(5);
        points.setCustomerId(2L); points.setWalletType(3); points.setBalance(BigDecimal.ZERO);
        points.setFrozenBalance(BigDecimal.ZERO); points.setVersion(0);
        order.setId(1L); order.setCustomerId(2L); order.setStoreId(3L); order.setStaffId(4L);
        order.setOrderType(3); order.setOrderStatus(9); order.setPayAmount(new BigDecimal("100")); order.setPaymentMethod(2);
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(customers.findById(2L)).thenReturn(Optional.of(customer));
        when(customers.findByIdWithLock(2L)).thenReturn(Optional.of(customer));
        when(wallets.findByCustomerIdAndWalletTypeWithLock(2L,3)).thenReturn(Optional.of(points));
        when(events.existsByEventKey(anyString())).thenAnswer(c -> eventKeys.contains(c.getArgument(0)));
        when(events.save(any())).thenAnswer(c -> { PerformanceEventRecord r=c.getArgument(0);eventKeys.add(r.getEventKey());return r; });
        doAnswer(c -> { savedLogs.addAll(c.getArgument(0)); return null; }).when(logs).saveBatch(anyList());
        when(logs.findByOrderId(1L)).thenAnswer(c -> savedLogs.stream().filter(l->l.getAmount().signum()>0).toList());
        when(logs.findByBiz(eq(1L),eq(PerformanceBizType.REFUND_DEDUCT.getCode())))
                .thenAnswer(c -> savedLogs.stream().filter(l->l.getAmount().signum()<0).toList());
        when(transactions.save(any())).thenAnswer(c -> { WalletTransaction t=c.getArgument(0);savedTransactions.add(t);return t; });
        when(transactions.findByReference(eq(2L),eq(3),anyString(),eq(1L))).thenAnswer(c ->
                savedTransactions.stream().filter(t->t.getReferenceType().equals(c.getArgument(2))).toList());
        when(returns.findByOrderId(1L)).thenAnswer(c -> List.copyOf(completedReturns));
        var line=new OrderItem();line.setId(10L);line.setOrderId(1L);line.setProductId(20L);
        line.setQuantity(new BigDecimal("10"));line.setTotalPrice(new BigDecimal("100"));
        line.setBaseQuantity(new BigDecimal("10"));line.setUnitCost(BigDecimal.ONE);
        when(items.findByOrderId(1L)).thenReturn(List.of(line));
        var rule=new CommissionRule();rule.setCalcMode("SALES_PERCENT");rule.setCalcValue(new BigDecimal("0.1"));
        when(rules.findBestSalesRule(20L,null,4L)).thenReturn(Optional.of(rule));
        properties.setMaxCommissionRatio(BigDecimal.ONE);
        var customerService=new CustomerSettlementService(new CustomerDomainService(wallets,mock(CreditAccountRepository.class),
                orders,returns,mock(PointsExchangeOrderRepository.class)),customers,wallets,transactions);
        listener=new PerformanceEventListener(properties,new PerformanceDomainService(),rules,logs,events,orders,items,
                products,relations,publisher,
                customerService,returns,returnItems);
    }

    @Test void refundBeforeFirstOnlineReceiptDoesNotSubtractOtherOrdersOrConsumeSettlement() {
        var fact=completedReturn(7L,"30","3");
        listener.onOrderRefunded(refundEvent(fact,"3"));
        assertMoney("500",customer.getTotalOrderAmount());assertEquals(5,customer.getTotalOrderCount());
        assertTrue(savedLogs.isEmpty());assertTrue(savedTransactions.isEmpty());
        assertFalse(eventKeys.contains("ORDER_REFUNDED:AFTER_SALES:7"),"先保存售后事实，首次结算再处理副作用");
    }

    @Test void firstReceiptReconcilesPreviouslyCompletedReturnThenReplayChangesNothing() {
        var fact=completedReturn(7L,"30","3");
        listener.onOrderRefunded(refundEvent(fact,"3"));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertMoney("570",customer.getTotalOrderAmount());assertEquals(6,customer.getTotalOrderCount());
        assertMoney("70",points.getBalance());assertMoney("7",commission());
        int transactionCount=savedTransactions.size(),logCount=savedLogs.size();
        listener.onOrderFinished(new OrderFinishedEvent(1L));listener.onOrderRefunded(refundEvent(fact,"3"));
        assertMoney("570",customer.getTotalOrderAmount());assertMoney("70",points.getBalance());
        assertEquals(transactionCount,savedTransactions.size());assertEquals(logCount,savedLogs.size());
    }

    @Test void firstReceiptUsesPersistedReturnsEvenWhenEarlierEventWasNotDelivered() {
        completedReturn(7L,"30","3");
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertMoney("570",customer.getTotalOrderAmount());assertMoney("70",points.getBalance());assertMoney("7",commission());
    }

    @Test void fullyRefundedBeforeReceiptDoesNotReducePreviousCustomerOrders() {
        order.setOrderStatus(8);var fact=completedReturn(7L,"100","10");
        listener.onOrderRefunded(refundEvent(fact,"10"));
        assertMoney("500",customer.getTotalOrderAmount());assertEquals(5,customer.getTotalOrderCount());
        assertMoney("0",points.getBalance());assertTrue(savedLogs.isEmpty());
    }

    @Test void noSalesStaffStillReconcilesCustomerStatisticsAtFirstReceipt() {
        order.setStaffId(null);completedReturn(7L,"30","3");
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertMoney("570",customer.getTotalOrderAmount());assertMoney("70",points.getBalance());assertTrue(savedLogs.isEmpty());
    }

    @Test void firstReceiptUsesCurrentCustomerStatisticsRatherThanUnlockedOlderSaleSnapshot() {
        var stale=new Customer();stale.setId(2L);stale.setTotalOrderAmount(new BigDecimal("100"));stale.setTotalOrderCount(1);
        when(customers.findById(2L)).thenReturn(Optional.of(stale));
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertMoney("600",customer.getTotalOrderAmount());assertEquals(6,customer.getTotalOrderCount());
    }

    @Test void posAlreadySettledAtCreationRefundsOnceAndReceiptDoesNotRepeatIt() {
        order.setOrderType(1);order.setOrderStatus(4);listener.onOrderFinished(new OrderFinishedEvent(1L));
        order.setOrderStatus(9);var fact=completedReturn(7L,"30","3");
        listener.onOrderRefunded(refundEvent(fact,"3"));listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertMoney("570",customer.getTotalOrderAmount());assertMoney("70",points.getBalance());assertMoney("7",commission());
    }

    @Test void returnBeforeFirstReceiptCannotQualifyNewCustomerUsingReturnedProfit() {
        approvedQrRelation();completedReturn(7L,"80","8");
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        assertMoney("520",customer.getTotalOrderAmount());assertMoney("20",points.getBalance());assertMoney("2",commission());
        verify(publisher,never()).publishEvent(any(CustomerFirstEffectiveEvent.class));
    }

    @Test void fullyReturnedWhitelistMaterialCannotQualifyRemainingOtherGoods() {
        approvedQrRelation();properties.setNewCustomerCategoryWhitelist(List.of(7L));properties.setNewCustomerProfitThreshold(new BigDecimal("1000"));
        order.setPayAmount(new BigDecimal("250"));
        var whitelisted=line(10L,20L,"3","30","3");var other=line(11L,21L,"10","220","220");
        when(items.findByOrderId(1L)).thenReturn(List.of(whitelisted,other));
        var first=new Product();first.setId(20L);first.setCategoryId(7L);
        var second=new Product();second.setId(21L);second.setCategoryId(8L);
        when(products.findById(20L)).thenReturn(Optional.of(first));when(products.findById(21L)).thenReturn(Optional.of(second));
        completedReturn(7L,"30","3");
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        verify(publisher,never()).publishEvent(any(CustomerFirstEffectiveEvent.class));
    }

    @Test void customerStillMeetingProfitThresholdAfterReturnCanQualify() {
        approvedQrRelation();completedReturn(7L,"30","3");
        listener.onOrderFinished(new OrderFinishedEvent(1L));
        verify(publisher).publishEvent(any(CustomerFirstEffectiveEvent.class));
    }

    private void approvedQrRelation() {
        var relation=new StaffCustomerRelation();relation.setCustomerId(2L);relation.setStaffId(4L);
        relation.setStatus(StaffCustomerRelation.STATUS_APPROVED);relation.setBindSource("QR");
        when(relations.findByCustomer(2L)).thenReturn(Optional.of(relation));
    }
    private OrderItem line(long id,long product,String quantity,String amount,String cost) {
        var line=new OrderItem();line.setId(id);line.setOrderId(1L);line.setProductId(product);
        line.setQuantity(new BigDecimal(quantity));line.setTotalPrice(new BigDecimal(amount));
        line.setBaseQuantity(BigDecimal.ONE);line.setUnitCost(new BigDecimal(cost));return line;
    }

    private AfterSales completedReturn(long id,String amount,String quantity) {
        var fact=new AfterSales();fact.setId(id);fact.setOrderId(1L);fact.setStatus(AfterSales.STATUS_FINISHED);
        fact.setRefundAmount(new BigDecimal(amount));completedReturns.add(fact);
        var returned=new AfterSalesItem();returned.setAfterSalesId(id);returned.setOrderItemId(10L);returned.setQuantity(new BigDecimal(quantity));
        when(returnItems.findByAfterSalesId(id)).thenReturn(List.of(returned));return fact;
    }
    private OrderRefundedEvent refundEvent(AfterSales fact,String quantity) {
        return new OrderRefundedEvent(1L,fact.getRefundAmount(),Map.of(10L,new BigDecimal(quantity)),"AFTER_SALES:"+fact.getId());
    }
    private BigDecimal commission(){return savedLogs.stream().map(StaffPerformanceLog::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void assertMoney(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
}
