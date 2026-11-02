package com.agri_system.application.report.service;

import com.agri_system.application.order.dto.vo.OrderMarginVO;
import com.agri_system.application.order.service.RefundAmountPolicy;
import com.agri_system.application.report.dto.*;
import com.agri_system.domain.customer.query.CustomerReportQuery;
import com.agri_system.domain.customer.query.CustomerReportRow;
import com.agri_system.domain.inventory.query.InventoryReportBatch;
import com.agri_system.domain.inventory.query.InventoryReportQuery;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.query.SalesReportQuery;
import com.agri_system.domain.order.query.SalesTrendReturnFacts;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.credit.query.CreditReportQuery;
import com.agri_system.domain.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计报表聚合查询服务（纯读，不新增任何报表表）
 */
@Service
public class ReportAnalysisService {

    private final SalesReportQuery salesReportQuery;
    private final InventoryReportQuery inventoryReportQuery;
    private final CreditReportQuery creditReportQuery;
    private final CustomerReportQuery customerReportQuery;
    private final ProductRepository productRepository;
    /** 批次归属事实仓储，销售趋势按实际/估算/成本缺失三口径分别归集。 */
    private final OrderItemBatchAllocationRepository allocationRepository;

    @Autowired
    public ReportAnalysisService(SalesReportQuery salesReportQuery,
                                 InventoryReportQuery inventoryReportQuery,
                                 CreditReportQuery creditReportQuery,
                                 CustomerReportQuery customerReportQuery,
                                 ProductRepository productRepository,
                                 OrderItemBatchAllocationRepository allocationRepository) {
        this.salesReportQuery = salesReportQuery;
        this.inventoryReportQuery = inventoryReportQuery;
        this.creditReportQuery = creditReportQuery;
        this.customerReportQuery = customerReportQuery;
        this.productRepository = productRepository;
        this.allocationRepository = allocationRepository;
    }

    /**
     * 今日经营驾驶舱
     * @param storeId 门店ID（可选，为null时查询全部门店）
     */
    @Transactional(readOnly = true)
    public DashboardVO dashboardToday(Long storeId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        var sales = salesReportQuery.dashboardFacts(storeId,start,end);
        BigDecimal gmv=sales.gmv(),netSales=sales.netSales();
        Long orderCount=sales.orderCount();

        // 今日新增原始赊销额，按应收账户所属门店统计。
        BigDecimal newCreditAmount = creditReportQuery.newCreditAmount(storeId, start, end);

        // 今日新增客户数（customer 表无 store_id 字段）
        Long newCustomerCount = customerReportQuery.countCreatedBetween(start,end);

        Long pendingAfterSales=sales.pendingAfterSalesCount(),pendingDelivery=sales.pendingDeliveryCount();

        // 库存预警数（简单用库龄 > 180 天的批次数量代表）
        LocalDate threshold = today.minusDays(180);
        Long inventoryAlertCount = inventoryReportQuery.findCurrentBatches(storeId).stream()
                .filter(b -> b.productionDate() != null && !b.productionDate().isAfter(threshold)).count();

        DashboardVO vo = new DashboardVO();
        vo.setGmv(defaultZero(gmv));
        vo.setNetSales(defaultZero(netSales));
        vo.setOrderCount(defaultLong(orderCount));
        vo.setNewCreditAmount(defaultZero(newCreditAmount));
        vo.setNewCustomerCount(defaultLong(newCustomerCount));
        vo.setPendingAfterSalesCount(pendingAfterSales);
        vo.setPendingDeliveryCount(defaultLong(pendingDelivery));
        vo.setInventoryAlertCount(defaultLong(inventoryAlertCount));
        return vo;
    }

    /**
     * 大户排行榜
     * @param limit 返回条数
     * @param storeId 门店ID（可选，按门店下单记录筛选客户）
     */
    @Transactional(readOnly = true)
    public List<CustomerRankVO> topCustomers(int limit, Long storeId) {
        List<CustomerReportRow> customers = customerReportQuery.rankedCustomers(limit,storeId);
        return customers.stream()
                .map(c -> {
                    CustomerRankVO vo = new CustomerRankVO();
                    vo.setCustomerId(c.customerId());
                    vo.setCustomerName(c.name());
                    vo.setPhone(c.phone());
                    vo.setTotalOrderAmount(defaultZero(c.totalOrderAmount()));
                    vo.setLastOrderTime(c.lastOrderTime());
                    vo.setStaffName(null);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 流失预警：90天未下单且历史消费>1000的客户
     * @param storeId 门店ID（可选，按门店下单记录筛选客户）
     */
    @Transactional(readOnly = true)
    public List<ChurnRiskVO> churnRisks(Long storeId) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(90);
        List<CustomerReportRow> customers = customerReportQuery.churnCandidates(storeId,deadline);
        LocalDateTime now = LocalDateTime.now();
        return customers.stream()
                .map(c -> {
                    ChurnRiskVO vo = new ChurnRiskVO();
                    vo.setCustomerId(c.customerId());
                    vo.setCustomerName(c.name());
                    vo.setPhone(c.phone());
                    vo.setLastOrderTime(c.lastOrderTime());
                    if (c.lastOrderTime() != null) {
                        long days = java.time.Duration.between(c.lastOrderTime(), now).toDays();
                        vo.setDaysSinceLastOrder(days);
                    } else {
                        vo.setDaysSinceLastOrder(null);
                    }
                    vo.setTotalOrderAmount(defaultZero(c.totalOrderAmount()));
                    return vo;
                })
                .sorted(Comparator.comparing(ChurnRiskVO::getDaysSinceLastOrder, Comparator.nullsLast(Long::compareTo)).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 库存资金占用
     * @param storeId 门店ID（可选）
     */
    @Transactional(readOnly = true)
    public InventoryValueVO inventoryValue(Long storeId) {
        // 有效批次资金占用
        List<InventoryReportBatch> batches = inventoryReportQuery.findCurrentBatches(storeId);
        BigDecimal total = batches.stream()
                .map(b -> defaultZero(b.quantityInMainUnit()).multiply(defaultZero(b.unitCostInMainUnit())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 库龄 > 180 天：简单按 created_at 维度统计
        LocalDateTime threshold = LocalDateTime.now().minusDays(180);
        List<InventoryReportBatch> aged = batches.stream()
                .filter(b -> b.receivedAt() != null && b.receivedAt().isBefore(threshold))
                .collect(Collectors.toList());
        BigDecimal over180 = aged.stream()
                .map(b -> defaultZero(b.quantityInMainUnit()).multiply(defaultZero(b.unitCostInMainUnit())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        InventoryValueVO vo = new InventoryValueVO();
        vo.setTotalInventoryValue(total);
        vo.setOver180DaysValue(over180);
        return vo;
    }

    private BigDecimal defaultZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long defaultLong(Long v) {
        return v == null ? 0L : v;
    }

    /** 从按订单批量加载的归属事实中筛出当前订单行的事实；无来源或订单行ID缺失时返回空。 */
    private List<OrderItemBatchAllocation> itemAllocations(
            OrderItem item, Map<Long, List<OrderItemBatchAllocation>> allocationsByOrder) {
        List<OrderItemBatchAllocation> orderAllocations = allocationsByOrder.get(item.getOrderId());
        if (orderAllocations == null || orderAllocations.isEmpty() || item.getId() == null) {
            return List.of();
        }
        return orderAllocations.stream()
                .filter(allocation -> allocation != null && item.getId().equals(allocation.getOrderItemId()))
                .collect(Collectors.toList());
    }

    /**
     * 销售与毛利趋势分析
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param storeId 门店ID（可选）
     * @return 按日期聚合的销售趋势列表
     */
    @Transactional(readOnly = true)
    public List<SalesTrendVO> salesTrend(LocalDateTime startTime, LocalDateTime endTime, Long storeId) {
        var source=salesReportQuery.trendSource(startTime,endTime,storeId);
        List<Order> orders=source.orders();
        if(orders.isEmpty())return List.of();
        List<Long> orderIds=orders.stream().map(Order::getId).toList();
        List<OrderItem> items=source.items();
        Map<Long,BigDecimal> refundsByOrder=source.refundsByOrder();
        Map<Long,SalesTrendReturnFacts> returnsByItem=source.returnsByItem();
        java.util.Set<Long> invalidRefundOrders=source.invalidRefundOrders();
        Map<Long, BigDecimal> netLineAmounts = new LinkedHashMap<>();
        Map<Long, BigDecimal> unavailableOrderSales = new LinkedHashMap<>();
        Map<Long, String> costWarnings = new LinkedHashMap<>();
        for (Order order : orders) {
            List<OrderItem> orderItems = items.stream().filter(i -> order.getId().equals(i.getOrderId())).toList();
            BigDecimal parentRefund = refundsByOrder.getOrDefault(order.getId(), BigDecimal.ZERO);
            BigDecimal childRefund = orderItems.stream().map(i -> returnsByItem.get(i.getId()))
                    .filter(java.util.Objects::nonNull).map(SalesTrendReturnFacts::getRefundAmount)
                    .map(this::defaultZero).reduce(BigDecimal.ZERO, BigDecimal::add);
            // 旧数据若缺明细或净额不可对账，保留父单权威净销，整单成本标未知，不能猜分摊。
            Map<Long, BigDecimal> originalNet = Map.of();
            String warning = null;
            try {
                originalNet = RefundAmountPolicy.refundableBaseAmounts(order, orderItems);
                if (order.getPayAmount() == null || originalNet.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(order.getPayAmount()) != 0) {
                    warning = "原订单净应结算与物料成交金额无法核对";
                }
            } catch (IllegalArgumentException missingHistory) {
                warning = "原订单缺少可核对的物料成交快照";
            }
            if (parentRefund.compareTo(childRefund) != 0) warning = "已完成售后金额与物料明细不一致";
            if (parentRefund.signum() < 0 || order.getPayAmount() != null
                    && parentRefund.compareTo(order.getPayAmount()) > 0) {
                warning = "已完成售后累计退款超出原净应结算范围";
            }
            if (invalidRefundOrders.contains(order.getId())) warning = "已完成售后存在负数或缺失事实";
            if (warning == null) {
                for (OrderItem item : orderItems) {
                    SalesTrendReturnFacts fact = returnsByItem.get(item.getId());
                    BigDecimal quantity = fact == null ? BigDecimal.ZERO : defaultZero(fact.getReturnedQuantity());
                    BigDecimal refund = fact == null ? BigDecimal.ZERO : defaultZero(fact.getRefundAmount());
                    if (quantity.signum() < 0 || item.getQuantity() == null
                            || quantity.compareTo(item.getQuantity()) > 0
                            || refund.signum() < 0 || refund.compareTo(originalNet.get(item.getId())) > 0) {
                        warning = "已完成售后累计退量或退款超出原物料净成交范围";
                        break;
                    }
                }
            }
            if (warning != null) {
                unavailableOrderSales.put(order.getId(), defaultZero(order.getPayAmount()).subtract(parentRefund));
                costWarnings.put(order.getId(), "订单" + order.getId() + "：" + warning + "，成本及毛利待核对");
            } else {
                netLineAmounts.putAll(originalNet);
            }
        }

        // 获取订单创建时间映射
        Map<Long, LocalDateTime> orderTimeMap = orders.stream()
                .collect(Collectors.toMap(Order::getId, Order::getCreatedAt));

        // 批次归属事实按订单批量加载（每单一次查询），避免逐行 N+1
        Map<Long, List<OrderItemBatchAllocation>> allocationsByOrder = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            List<OrderItemBatchAllocation> orderAllocations = allocationRepository.findByOrderId(orderId);
            allocationsByOrder.put(orderId, orderAllocations == null ? List.of() : orderAllocations);
        }

        // 按日期聚合：实际/估算/成本缺失三口径严格隔离，估算绝不并入实际；
        // costAmount = 实际 + 估算，均冲减已完成退货，缺失成本不猜补
        Map<LocalDate, SalesTrendVO> trendMap = new LinkedHashMap<>();
        for (Order order : orders) {
            if (!unavailableOrderSales.containsKey(order.getId())) continue;
            SalesTrendVO vo = trendMap.computeIfAbsent(order.getCreatedAt().toLocalDate(), this::newSalesTrend);
            BigDecimal sale = unavailableOrderSales.get(order.getId());
            vo.setSaleAmount(vo.getSaleAmount().add(sale));
            vo.setCostUnavailableSaleAmount(vo.getCostUnavailableSaleAmount().add(sale));
            vo.getCostWarnings().add(costWarnings.get(order.getId()));
        }
        for (OrderItem item : items) {
            LocalDateTime orderTime = orderTimeMap.get(item.getOrderId());
            if (orderTime == null || unavailableOrderSales.containsKey(item.getOrderId())) continue;
            SalesTrendVO vo = trendMap.computeIfAbsent(orderTime.toLocalDate(), this::newSalesTrend);

            List<OrderItemBatchAllocation> itemFacts = itemAllocations(item, allocationsByOrder);
            OrderMarginVO margin = OrderMarginVO.from(item, itemFacts);
            SalesTrendReturnFacts returned = returnsByItem.get(item.getId());
            BigDecimal refunded = returned == null ? BigDecimal.ZERO : defaultZero(returned.getRefundAmount());
            BigDecimal returnedQuantity = returned == null ? BigDecimal.ZERO : defaultZero(returned.getReturnedQuantity());
            BigDecimal sale = netLineAmounts.get(item.getId()).subtract(refunded);
            BigDecimal returnedCost = returned == null || returnedQuantity.signum() <= 0
                    ? BigDecimal.ZERO : defaultZero(returned.getReturnedCost());
            BigDecimal actualCost = margin.actualCost() == null ? null
                    : margin.actualCost().subtract(returnedCost).max(BigDecimal.ZERO);
            // 完成售后数量与合法原SALE/RETURN回库证据必须相符；缺链不能假装实际净成本。
            BigDecimal allocatedBase = itemFacts.stream().map(OrderItemBatchAllocation::getAllocatedBaseQuantity)
                    .map(this::defaultZero).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (actualCost != null && returnedQuantity.signum() > 0
                    && (returned == null || defaultZero(returned.getReturnedBaseQuantity())
                        .multiply(defaultZero(item.getQuantity())).compareTo(allocatedBase.multiply(returnedQuantity)) != 0)) {
                actualCost = null;
            }
            BigDecimal estimatedCost = margin.estimatedCost() == null ? null
                    : defaultZero(item.getCostPriceSnapshot()).multiply(defaultZero(item.getQuantity())
                            .subtract(returnedQuantity).max(BigDecimal.ZERO));
            vo.setSaleAmount(vo.getSaleAmount().add(sale));
            if (actualCost != null) {
                vo.setActualSaleAmount(vo.getActualSaleAmount().add(sale));
                vo.setActualCostAmount(vo.getActualCostAmount().add(actualCost));
                vo.setCostAmount(vo.getCostAmount().add(actualCost));
            } else if (margin.estimatedCost() != null) {
                vo.setCostStatus("估算");
                vo.setEstimatedSaleAmount(vo.getEstimatedSaleAmount().add(sale));
                vo.setEstimatedCostAmount(vo.getEstimatedCostAmount().add(estimatedCost));
                vo.setCostAmount(vo.getCostAmount().add(estimatedCost));
            } else {
                vo.setCostUnavailableSaleAmount(vo.getCostUnavailableSaleAmount().add(sale));
                vo.getCostWarnings().add("订单" + item.getOrderId() + "物料" + item.getId() + "：缺少原成本证据，成本及毛利待核对");
                // 缺证据不使用当前商品成本或旧快照猜补实际净成本。
            }
        }

        // 计算毛利和毛利率
        return trendMap.values().stream()
                .peek(vo -> {
                    if (!vo.getCostWarnings().isEmpty()) vo.setCostStatus("待核对");
                    vo.setProfit(vo.getSaleAmount().subtract(vo.getCostAmount()));
                    vo.setActualProfit(vo.getActualSaleAmount().subtract(vo.getActualCostAmount()));
                    vo.setEstimatedProfit(vo.getEstimatedSaleAmount().subtract(vo.getEstimatedCostAmount()));
                    if (vo.getSaleAmount().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal rate = vo.getProfit()
                                .divide(vo.getSaleAmount(), 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        vo.setProfitRate(rate);
                    } else {
                        vo.setProfitRate(BigDecimal.ZERO);
                    }
                })
                .sorted(Comparator.comparing(SalesTrendVO::getDate))
                .collect(Collectors.toList());
    }

    private SalesTrendVO newSalesTrend(LocalDate date) {
        SalesTrendVO v = new SalesTrendVO();
        v.setDate(date);
        v.setCostStatus("已核对");
        v.setSaleAmount(BigDecimal.ZERO);
        v.setCostAmount(BigDecimal.ZERO);
        v.setActualSaleAmount(BigDecimal.ZERO);
        v.setActualCostAmount(BigDecimal.ZERO);
        v.setActualProfit(BigDecimal.ZERO);
        v.setEstimatedSaleAmount(BigDecimal.ZERO);
        v.setEstimatedCostAmount(BigDecimal.ZERO);
        v.setEstimatedProfit(BigDecimal.ZERO);
        v.setCostUnavailableSaleAmount(BigDecimal.ZERO);
        return v;
    }

    /**
     * 滞销商品列表（库龄 > 180天）
     * @param storeId 门店ID（可选）
     */
    @Transactional(readOnly = true)
    public List<SlowMovingInventoryVO> slowMovingInventory(Long storeId) {
        LocalDate threshold = LocalDate.now().minusDays(180);
        List<InventoryReportBatch> batches = inventoryReportQuery.findCurrentBatches(storeId).stream()
                .filter(b -> b.productionDate() != null && !b.productionDate().isAfter(threshold)).toList();
        
        // 获取商品ID集合，批量查询商品名称
        List<Long> productIds = batches.stream()
                .map(InventoryReportBatch::productId)
                .distinct()
                .collect(Collectors.toList());
        
        // 复用既有批量仓储；空列表不触发目录全表读取，缺失名称保持原展示兜底。
        Map<Long, String> productNameMap = productIds.isEmpty() ? Map.of()
                : productRepository.findByIds(productIds).stream()
                        .filter(product -> product.getName() != null)
                        .collect(Collectors.toMap(product -> product.getId(), product -> product.getName()));

        return batches.stream()
                .map(batch -> {
                    SlowMovingInventoryVO vo = new SlowMovingInventoryVO();
                    vo.setProductId(batch.productId());
                    vo.setProductName(productNameMap.getOrDefault(batch.productId(), "商品" + batch.productId()));
                    vo.setBatchNo(batch.supplierBatchNo());
                    vo.setQuantity(defaultZero(batch.quantityInMainUnit()));
                    vo.setValue(defaultZero(batch.quantityInMainUnit()).multiply(defaultZero(batch.unitCostInMainUnit())));
                    vo.setProductionDate(batch.productionDate());
                    vo.setCreatedAt(batch.receivedAt());
                    if (batch.productionDate() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(batch.productionDate(), LocalDate.now());
                        vo.setAgeDays(days);
                    }
                    return vo;
                })
                .sorted(Comparator.comparing(SlowMovingInventoryVO::getAgeDays).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 财务风控分析
     * @param storeId 门店ID（可选，应收和还款均按所属门店筛选）
     */
    @Transactional(readOnly = true)
    public CreditRiskVO creditRisk(Long storeId) {
        var facts = creditReportQuery.riskFacts(storeId, LocalDate.now());
        BigDecimal totalDebt = defaultZero(facts.totalDebt());
        BigDecimal overdueDebt = defaultZero(facts.overdueDebt());
        BigDecimal monthlyNewCredit = defaultZero(facts.monthlyNewCredit());
        BigDecimal monthlyRepayment = defaultZero(facts.monthlyRepayment());
        BigDecimal unknown = facts.accountsWithUnknownSources();
        BigDecimal netPaid = facts.netMonthlyPaid();
        BigDecimal cohortRemaining = facts.monthlyRemainingCredit();
        BigDecimal denominator = defaultZero(netPaid).add(defaultZero(cohortRemaining));
        BigDecimal repaymentRate = null;
        if (defaultZero(unknown).signum() == 0 && denominator.signum() > 0) {
            repaymentRate = defaultZero(netPaid).multiply(BigDecimal.valueOf(100))
                    .divide(denominator, 2, java.math.RoundingMode.HALF_UP);
        }
        
        CreditRiskVO vo = new CreditRiskVO();
        vo.setTotalDebt(totalDebt);
        vo.setOverdueDebt(overdueDebt);
        vo.setRepaymentRate(repaymentRate);
        vo.setMonthlyNewCredit(monthlyNewCredit);
        vo.setMonthlyRepayment(monthlyRepayment);
        return vo;
    }

}
