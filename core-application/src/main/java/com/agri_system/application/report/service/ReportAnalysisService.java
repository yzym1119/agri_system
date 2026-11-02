package com.agri_system.application.report.service;

import com.agri_system.application.order.dto.vo.OrderMarginVO;
import com.agri_system.application.order.service.RefundAmountPolicy;
import com.agri_system.application.report.dto.*;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.inventory.query.InventoryReportBatch;
import com.agri_system.domain.inventory.query.InventoryReportQuery;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.domain.product.repository.ProductRepository;
import com.mybatisflex.core.query.QueryWrapper;
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

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final InventoryReportQuery inventoryReportQuery;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditRepaymentLogMapper repaymentLogMapper;
    private final CustomerMapper customerMapper;
    private final ProductRepository productRepository;
    private final AfterSalesMapper afterSalesMapper;
    /** 批次归属事实仓储，销售趋势按实际/估算/成本缺失三口径分别归集。 */
    private final OrderItemBatchAllocationRepository allocationRepository;

    @Autowired
    public ReportAnalysisService(OrderMapper orderMapper,
                                 OrderItemMapper orderItemMapper,
                                 InventoryReportQuery inventoryReportQuery,
                                 CreditAccountMapper creditAccountMapper,
                                 CreditRepaymentLogMapper repaymentLogMapper,
                                 CustomerMapper customerMapper,
                                 ProductRepository productRepository,
                                 AfterSalesMapper afterSalesMapper,
                                 OrderItemBatchAllocationRepository allocationRepository) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryReportQuery = inventoryReportQuery;
        this.creditAccountMapper = creditAccountMapper;
        this.repaymentLogMapper = repaymentLogMapper;
        this.customerMapper = customerMapper;
        this.productRepository = productRepository;
        this.afterSalesMapper = afterSalesMapper;
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

        // GMV 与订单数
        QueryWrapper orderQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end)
                .and("order_status in (2,3,4,5,6,9)");
        if (storeId != null) {
            orderQ.and("store_id = ?", storeId);
        }
        BigDecimal gmv = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COALESCE(SUM(total_amount),0) AS total"), BigDecimal.class);
        // 今天创建的销售单原净应结算 N（含赊销）减累计已完成退货 R，不是今日现金流。
        QueryWrapper netSalesQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end)
                .and("order_status in (2,3,4,5,6,8,9)");
        if (storeId != null) netSalesQ.and("store_id = ?", storeId);
        BigDecimal netSales = orderMapper.selectOneByQueryAs(netSalesQ.select("""
                COALESCE(SUM(CASE WHEN order_status = 8 THEN 0 ELSE COALESCE(pay_amount,0) -
                    COALESCE((SELECT SUM(af.refund_amount) FROM after_sales af
                        WHERE af.order_id = `order`.id AND af.store_id = `order`.store_id
                          AND af.status = 50),0) END),0) AS total
                """), BigDecimal.class);
        Long orderCount = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COUNT(1) AS cnt"), Long.class);

        // 今日新增原始赊销额，按应收账户所属门店统计。
        QueryWrapper creditQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end);
        if (storeId != null) creditQ.and("store_id = ?", storeId);
        BigDecimal newCreditAmount = creditAccountMapper.selectOneByQueryAs(
                creditQ.select("COALESCE(SUM(credit_amount),0) AS total"), BigDecimal.class);

        // 今日新增客户数（customer 表无 store_id 字段）
        QueryWrapper customerQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end);
        Long newCustomerCount = customerMapper.selectOneByQueryAs(
                customerQ.select("COUNT(1) AS cnt"), Long.class);

        // 待审核售后数（after_sales.status = 10 STATUS_APPLIED）
        QueryWrapper afterSalesQ = QueryWrapper.create()
                .where("status = ?", 10);
        if (storeId != null) {
            afterSalesQ.and("store_id = ?", storeId);
        }
        Long pendingAfterSales = afterSalesMapper.selectCountByQuery(afterSalesQ);

        // 按真实配送单统计可派订单；POS及部分退款单共用实际出库/回库事实。
        QueryWrapper deliveryQ = QueryWrapper.create()
                .where("order_status in (4,9)")
                .and("EXISTS (SELECT 1 FROM order_delivery d WHERE d.order_id = `order`.id AND d.delivery_status = 2 AND d.delivery_type in (2,3))")
                .and("NOT EXISTS (SELECT 1 FROM after_sales af WHERE af.order_id = `order`.id AND af.status in (10,40))")
                .and("""
                        EXISTS (SELECT 1 FROM order_item_batch_allocation a
                            JOIN order_item i ON i.id = a.order_item_id
                            JOIN inventory_movement s ON s.id = a.source_movement_id
                            WHERE i.order_id = `order`.id AND a.store_id = `order`.store_id
                              AND s.movement_type = 'SALE' AND s.base_quantity > 0
                              AND s.base_quantity = a.allocated_base_quantity
                              AND s.store_id = a.store_id AND s.product_id = a.product_id
                              AND s.batch_id = a.batch_id AND s.from_location_id = a.location_id
                              AND s.reference_type = 'ORDER' AND s.reference_id = `order`.id
                              AND a.allocated_base_quantity > COALESCE((
                                  SELECT SUM(r.base_quantity) FROM inventory_movement r
                                  WHERE r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION'
                                    AND r.reference_id = a.id AND r.base_quantity > 0
                                    AND r.store_id = a.store_id AND r.product_id = a.product_id AND r.batch_id = a.batch_id
                              ),0))
                        """);
        if (storeId != null) {
            deliveryQ.and("store_id = ?", storeId);
        }
        Long pendingDelivery = orderMapper.selectCountByQuery(deliveryQ);

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
        QueryWrapper qw = QueryWrapper.create()
                .where("total_order_amount is not null");

        if (storeId != null) {
            List<Long> customerIds = getCustomerIdsByStore(storeId);
            if (customerIds.isEmpty()) {
                return List.of();
            }
            qw.and(buildInPlaceholders("id", customerIds), customerIds.toArray());
        }

        qw.orderBy("total_order_amount desc").limit(limit);
        List<Customer> customers = customerMapper.selectListByQuery(qw);
        return customers.stream()
                .map(c -> {
                    CustomerRankVO vo = new CustomerRankVO();
                    vo.setCustomerId(c.getId());
                    vo.setCustomerName(c.getName());
                    vo.setPhone(c.getPhone());
                    vo.setTotalOrderAmount(defaultZero(c.getTotalOrderAmount()));
                    vo.setLastOrderTime(c.getLastOrderTime());
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
        QueryWrapper qw = QueryWrapper.create()
                .where("last_order_time is not null")
                .and("last_order_time < ?", deadline)
                .and("total_order_amount > 1000");

        if (storeId != null) {
            List<Long> customerIds = getCustomerIdsByStore(storeId);
            if (customerIds.isEmpty()) {
                return List.of();
            }
            qw.and(buildInPlaceholders("id", customerIds), customerIds.toArray());
        }

        List<Customer> customers = customerMapper.selectListByQuery(qw);
        LocalDateTime now = LocalDateTime.now();
        return customers.stream()
                .map(c -> {
                    ChurnRiskVO vo = new ChurnRiskVO();
                    vo.setCustomerId(c.getId());
                    vo.setCustomerName(c.getName());
                    vo.setPhone(c.getPhone());
                    vo.setLastOrderTime(c.getLastOrderTime());
                    if (c.getLastOrderTime() != null) {
                        long days = java.time.Duration.between(c.getLastOrderTime(), now).toDays();
                        vo.setDaysSinceLastOrder(days);
                    } else {
                        vo.setDaysSinceLastOrder(null);
                    }
                    vo.setTotalOrderAmount(defaultZero(c.getTotalOrderAmount()));
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
        // 先查询订单ID列表（用于关联订单明细）
        QueryWrapper orderQw = QueryWrapper.create()
                .where("created_at between ? and ?", startTime, endTime)
                .and("order_status in (2,3,4,5,6,8,9)"); // 已成交，保留全部退货的原单日期
        if (storeId != null) {
            orderQw.and("store_id = ?", storeId);
        }
        List<Order> orders = orderMapper.selectListByQuery(orderQw);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());

        // 简化实现：直接查询所有明细，在内存中按日期聚合
        // （MyBatis-Flex的QueryWrapper对复杂聚合支持有限，如需高性能可改用@Select注解的SQL）
        QueryWrapper itemQw = QueryWrapper.create();
        if (orderIds.size() == 1) {
            itemQw.where("order_id = ?", orderIds.get(0));
        } else {
            String placeholders = orderIds.stream().map(id -> "?").collect(Collectors.joining(","));
            itemQw.where("order_id in (" + placeholders + ")", orderIds.toArray());
        }
        List<OrderItem> items = orderItemMapper.selectListByQuery(itemQw);

        // 完成售后按原订单创建日冲减；退款发生日期不改变该趋势的归属。
        Map<Long, BigDecimal> refundsByOrder = orderMapper.selectListByQueryAs(orderQw.clone().select("""
                `order`.id AS order_id,
                (SELECT COALESCE(SUM(af.refund_amount),0) FROM after_sales af
                 WHERE af.order_id = `order`.id AND af.store_id = `order`.store_id AND af.status = 50) AS order_refund_amount
                """), SalesTrendReturnFacts.class).stream()
                .collect(Collectors.toMap(SalesTrendReturnFacts::getOrderId, fact -> defaultZero(fact.getOrderRefundAmount())));
        Map<Long, SalesTrendReturnFacts> returnsByItem = orderItemMapper.selectListByQueryAs(
                itemQw.clone().select("""
                    order_item.id AS item_id,
                    (SELECT COALESCE(SUM(ai.refund_amount),0) FROM after_sales_item ai
                     JOIN after_sales af ON af.id = ai.after_sales_id
                     JOIN `order` o ON o.id = af.order_id AND o.store_id = af.store_id
                     WHERE ai.order_item_id = order_item.id AND ai.product_id = order_item.product_id
                       AND af.order_id = order_item.order_id AND af.status = 50) AS refund_amount,
                    (SELECT COALESCE(SUM(ai.quantity),0) FROM after_sales_item ai
                     JOIN after_sales af ON af.id = ai.after_sales_id
                     JOIN `order` o ON o.id = af.order_id AND o.store_id = af.store_id
                     WHERE ai.order_item_id = order_item.id AND ai.product_id = order_item.product_id
                       AND af.order_id = order_item.order_id AND af.status = 50) AS returned_quantity,
                    (SELECT COALESCE(SUM(r.base_quantity),0)
                     FROM order_item_batch_allocation a
                     JOIN inventory_movement s ON s.id = a.source_movement_id
                     JOIN `order` o ON o.id = order_item.order_id AND o.store_id = a.store_id
                     JOIN inventory_movement r ON r.reference_id = a.id
                     WHERE a.order_item_id = order_item.id AND a.product_id = order_item.product_id
                       AND s.movement_type = 'SALE' AND s.reference_type = 'ORDER' AND s.reference_id = o.id
                       AND s.store_id = a.store_id AND s.product_id = a.product_id AND s.batch_id = a.batch_id
                       AND s.from_location_id = a.location_id AND s.base_quantity = a.allocated_base_quantity AND s.base_quantity > 0
                       AND r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION' AND r.base_quantity > 0
                       AND r.store_id = a.store_id AND r.product_id = a.product_id
                       AND r.batch_id = a.batch_id AND r.to_location_id = a.location_id) AS returned_base_quantity,
                    (SELECT COALESCE(SUM(LEAST(a.allocated_cost, ROUND(a.batch_unit_cost * LEAST(a.allocated_base_quantity,
                        COALESCE((SELECT SUM(r.base_quantity) FROM inventory_movement r
                            WHERE r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION'
                              AND r.reference_id = a.id AND r.base_quantity > 0
                              AND r.store_id = a.store_id AND r.product_id = a.product_id
                              AND r.batch_id = a.batch_id AND r.to_location_id = a.location_id),0)),8))),0)
                     FROM order_item_batch_allocation a
                     JOIN inventory_movement s ON s.id = a.source_movement_id
                     JOIN `order` o ON o.id = order_item.order_id AND o.store_id = a.store_id
                     WHERE a.order_item_id = order_item.id AND a.product_id = order_item.product_id
                       AND s.movement_type = 'SALE' AND s.reference_type = 'ORDER' AND s.reference_id = o.id
                       AND s.store_id = a.store_id AND s.product_id = a.product_id AND s.batch_id = a.batch_id
                       AND s.from_location_id = a.location_id AND s.base_quantity = a.allocated_base_quantity
                       AND s.base_quantity > 0) AS returned_cost
                    """), SalesTrendReturnFacts.class).stream()
                .collect(Collectors.toMap(SalesTrendReturnFacts::getItemId, fact -> fact));
        // 先检查逐笔事实，不能让SUM把负数与正数抵消后伪装成有效累计量。
        java.util.Set<Long> invalidRefundOrders = orderMapper.selectListByQueryAs(orderQw.clone()
                .select("`order`.id AS order_id")
                .and("""
                    EXISTS (SELECT 1 FROM after_sales af
                        LEFT JOIN after_sales_item ai ON ai.after_sales_id = af.id
                        WHERE af.order_id = `order`.id AND af.store_id = `order`.store_id AND af.status = 50
                          AND (af.refund_amount IS NULL OR af.refund_amount < 0 OR ai.id IS NULL
                            OR ai.quantity IS NULL OR ai.quantity < 0
                            OR ai.refund_amount IS NULL OR ai.refund_amount < 0))
                    """), SalesTrendReturnFacts.class).stream()
                .map(SalesTrendReturnFacts::getOrderId).collect(Collectors.toSet());
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
        // 总欠款
        QueryWrapper debtQ = QueryWrapper.create()
                .where("status in (1,3)").and("remaining_amount > 0");
        if (storeId != null) debtQ.and("store_id = ?", storeId);
        BigDecimal totalDebt = creditAccountMapper.selectOneByQueryAs(
                debtQ.clone().select("COALESCE(SUM(remaining_amount),0) AS total"), BigDecimal.class);
        totalDebt = defaultZero(totalDebt);
        
        // 已明确逾期的历史账户即使到期日未知，也保留其逾期事实。
        BigDecimal overdueDebt = creditAccountMapper.selectOneByQueryAs(
                debtQ.clone()
                        .select("COALESCE(SUM(remaining_amount),0) AS total")
                        .and("(status = 3 OR due_date < ?)", LocalDate.now()),
                BigDecimal.class);
        overdueDebt = defaultZero(overdueDebt);
        
        // 本月新增赊销额（credit_account.created_at在本月）
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        LocalDateTime monthStartTime = monthStart.atStartOfDay();
        LocalDateTime monthEndTime = monthEnd.atTime(LocalTime.MAX);
        
        QueryWrapper monthlyCreditQ = QueryWrapper.create()
                .where("created_at between ? and ?", monthStartTime, monthEndTime);
        if (storeId != null) monthlyCreditQ.and("store_id = ?", storeId);
        BigDecimal monthlyNewCredit = creditAccountMapper.selectOneByQueryAs(
                monthlyCreditQ.clone().select("COALESCE(SUM(credit_amount),0) AS total"), BigDecimal.class);
        monthlyNewCredit = defaultZero(monthlyNewCredit);
        
        // 本月实收还款额（credit_repayment_log.created_at在本月）
        QueryWrapper repaymentQ = QueryWrapper.create()
                .select("COALESCE(SUM(amount),0) AS total")
                .where("created_at between ? and ?", monthStartTime, monthEndTime);
        if (storeId != null) {
            repaymentQ.and("store_id = ?", storeId);
        }
        BigDecimal monthlyRepayment = repaymentLogMapper.selectOneByQueryAs(
                repaymentQ, BigDecimal.class);
        monthlyRepayment = defaultZero(monthlyRepayment);
        
        // 同一批本月新建应收：净实还 / (净实还 + 剩余欠款)。
        // 本月偿还旧债仍计入上面的本月还款额，但不能混入本月赊销的回款率。
        // paid_amount 保留原实还事实，必须减去明确归属该账户的已退还款；冲欠不算现金。
        String sources = " FROM credit_repayment_log rp WHERE rp.credit_account_id = credit_account.id"
                + " AND rp.store_id = credit_account.store_id";
        String refunded = "COALESCE((SELECT SUM(rp.refunded_amount)" + sources + "),0)";
        String sourceAmount = "COALESCE((SELECT SUM(rp.amount)" + sources + "),0)";
        BigDecimal unknown = creditAccountMapper.selectOneByQueryAs(monthlyCreditQ.clone().select(
                "COALESCE(SUM(CASE WHEN paid_amount IS NULL OR remaining_amount IS NULL"
                        + " OR paid_amount < 0 OR remaining_amount < 0 OR paid_amount <> " + sourceAmount
                        + " OR EXISTS (SELECT 1" + sources + " AND (rp.refunded_amount IS NULL"
                        + " OR rp.refunded_amount < 0 OR rp.refunded_amount > rp.amount))"
                        + " THEN 1 ELSE 0 END),0) AS total"), BigDecimal.class);
        BigDecimal netPaid = creditAccountMapper.selectOneByQueryAs(monthlyCreditQ.clone().select(
                "COALESCE(SUM(paid_amount - " + refunded + "),0) AS total"), BigDecimal.class);
        BigDecimal cohortRemaining = creditAccountMapper.selectOneByQueryAs(monthlyCreditQ.clone().select(
                "COALESCE(SUM(remaining_amount),0) AS total"), BigDecimal.class);
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

    /**
     * 查询在某门店下过单的客户ID列表
     */
    private List<Long> getCustomerIdsByStore(Long storeId) {
        QueryWrapper orderQ = QueryWrapper.create()
                .select("DISTINCT customer_id")
                .where("store_id = ?", storeId)
                .and("customer_id is not null");
        List<Order> orders = orderMapper.selectListByQuery(orderQ);
        return orders.stream()
                .map(Order::getCustomerId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 构造 IN 子句条件字符串（占位符形式）
     */
    private String buildInPlaceholders(String column, List<Long> ids) {
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        return column + " in (" + placeholders + ")";
    }
}


