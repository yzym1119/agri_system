package com.agri_system.application.report.service;

import com.agri_system.application.order.dto.vo.OrderMarginVO;
import com.agri_system.application.report.dto.*;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.infrastructure.product.mapper.ProductMapper;
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
    private final InventoryBatchMapper inventoryBatchMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditRepaymentLogMapper repaymentLogMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final AfterSalesMapper afterSalesMapper;
    /** 批次归属事实仓储；为 null 时等同全部订单走历史估算口径（兼容既有测试构造）。 */
    private final OrderItemBatchAllocationRepository allocationRepository;

    /** 生产构造：销售趋势按实际/估算/成本缺失三口径分别归集。 */
    @Autowired
    public ReportAnalysisService(OrderMapper orderMapper,
                                 OrderItemMapper orderItemMapper,
                                 InventoryBatchMapper inventoryBatchMapper,
                                 CreditAccountMapper creditAccountMapper,
                                 CreditRepaymentLogMapper repaymentLogMapper,
                                 CustomerMapper customerMapper,
                                 ProductMapper productMapper,
                                 AfterSalesMapper afterSalesMapper,
                                 OrderItemBatchAllocationRepository allocationRepository) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryBatchMapper = inventoryBatchMapper;
        this.creditAccountMapper = creditAccountMapper;
        this.repaymentLogMapper = repaymentLogMapper;
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.afterSalesMapper = afterSalesMapper;
        this.allocationRepository = allocationRepository;
    }

    /**
     * 兼容既有测试的构造：无批次归属事实来源，等价于全部订单为历史估算口径；
     * legacy 字段（costAmount/profit/profitRate）与既有实现数值一致。
     */
    ReportAnalysisService(OrderMapper orderMapper,
                          OrderItemMapper orderItemMapper,
                          InventoryBatchMapper inventoryBatchMapper,
                          CreditAccountMapper creditAccountMapper,
                          CreditRepaymentLogMapper repaymentLogMapper,
                          CustomerMapper customerMapper,
                          ProductMapper productMapper,
                          AfterSalesMapper afterSalesMapper) {
        this(orderMapper, orderItemMapper, inventoryBatchMapper, creditAccountMapper, repaymentLogMapper,
                customerMapper, productMapper, afterSalesMapper, null);
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
        BigDecimal netSales = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COALESCE(SUM(pay_amount),0) AS total"), BigDecimal.class);
        Long orderCount = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COUNT(1) AS cnt"), Long.class);

        // 今日新增赊销额（来自 credit_account，该表无 store_id 字段）
        QueryWrapper creditQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end);
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

        // 待审核同城配送数（order.delivery_status=1 WAIT_AUDIT）
        QueryWrapper deliveryQ = QueryWrapper.create()
                .where("order_type = 3")
                .and("delivery_status = 1");
        if (storeId != null) {
            deliveryQ.and("store_id = ?", storeId);
        }
        Long pendingDelivery = orderMapper.selectOneByQueryAs(deliveryQ, Long.class);

        // 库存预警数（简单用库龄 > 180 天的批次数量代表）
        LocalDate threshold = today.minusDays(180);
        QueryWrapper inventoryAlertQ = QueryWrapper.create()
                .where("is_active = 1")
                .and("production_date is not null")
                .and("production_date <= ?", threshold);
        if (storeId != null) {
            inventoryAlertQ.and("store_id = ?", storeId);
        }
        Long inventoryAlertCount = inventoryBatchMapper.selectCountByQuery(inventoryAlertQ);

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
        QueryWrapper batchQ = QueryWrapper.create().where("is_active = 1");
        if (storeId != null) {
            batchQ.and("store_id = ?", storeId);
        }
        List<InventoryBatch> batches = inventoryBatchMapper.selectListByQuery(batchQ);
        BigDecimal total = batches.stream()
                .map(b -> defaultZero(b.getQuantity()).multiply(defaultZero(b.getCostPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 库龄 > 180 天：简单按 created_at 维度统计
        LocalDateTime threshold = LocalDateTime.now().minusDays(180);
        List<InventoryBatch> aged = batches.stream()
                .filter(b -> b.getCreatedAt() != null && b.getCreatedAt().isBefore(threshold))
                .collect(Collectors.toList());
        BigDecimal over180 = aged.stream()
                .map(b -> defaultZero(b.getQuantity()).multiply(defaultZero(b.getCostPrice())))
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
                .and("order_status in (2,3,4,5,6,9)"); // 已支付及之后状态
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

        // 获取订单创建时间映射
        Map<Long, LocalDateTime> orderTimeMap = orders.stream()
                .collect(Collectors.toMap(Order::getId, Order::getCreatedAt));

        // 批次归属事实按订单批量加载（每单一次查询），避免逐行 N+1
        Map<Long, List<OrderItemBatchAllocation>> allocationsByOrder = new LinkedHashMap<>();
        if (allocationRepository != null) {
            for (Long orderId : orderIds) {
                List<OrderItemBatchAllocation> orderAllocations = allocationRepository.findByOrderId(orderId);
                allocationsByOrder.put(orderId, orderAllocations == null ? List.of() : orderAllocations);
            }
        }

        // 按日期聚合：实际/估算/成本缺失三口径严格隔离，估算绝不并入实际；
        // legacy 口径不变：costAmount = 实际 + 估算，profit/profitRate 公式不变
        Map<LocalDate, SalesTrendVO> trendMap = new LinkedHashMap<>();
        for (OrderItem item : items) {
            LocalDateTime orderTime = orderTimeMap.get(item.getOrderId());
            if (orderTime == null) continue;
            LocalDate date = orderTime.toLocalDate();

            SalesTrendVO vo = trendMap.computeIfAbsent(date, d -> {
                SalesTrendVO v = new SalesTrendVO();
                v.setDate(d);
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
            });

            OrderMarginVO margin = OrderMarginVO.from(item, itemAllocations(item, allocationsByOrder));
            BigDecimal sale = margin.saleAmount();
            vo.setSaleAmount(vo.getSaleAmount().add(sale));
            if (margin.actualCost() != null) {
                vo.setActualSaleAmount(vo.getActualSaleAmount().add(sale));
                vo.setActualCostAmount(vo.getActualCostAmount().add(margin.actualCost()));
                vo.setCostAmount(vo.getCostAmount().add(margin.actualCost()));
            } else if (margin.estimatedCost() != null) {
                vo.setEstimatedSaleAmount(vo.getEstimatedSaleAmount().add(sale));
                vo.setEstimatedCostAmount(vo.getEstimatedCostAmount().add(margin.estimatedCost()));
                vo.setCostAmount(vo.getCostAmount().add(margin.estimatedCost()));
            } else {
                vo.setCostUnavailableSaleAmount(vo.getCostUnavailableSaleAmount().add(sale));
            }
        }

        // 计算毛利和毛利率
        return trendMap.values().stream()
                .peek(vo -> {
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

    /**
     * 滞销商品列表（库龄 > 180天）
     * @param storeId 门店ID（可选）
     */
    @Transactional(readOnly = true)
    public List<SlowMovingInventoryVO> slowMovingInventory(Long storeId) {
        LocalDate threshold = LocalDate.now().minusDays(180);
        QueryWrapper batchQ = QueryWrapper.create()
                .where("is_active = 1")
                .and("quantity > 0")
                .and("production_date is not null")
                .and("production_date <= ?", threshold);
        if (storeId != null) {
            batchQ.and("store_id = ?", storeId);
        }
        List<InventoryBatch> batches = inventoryBatchMapper.selectListByQuery(batchQ);
        
        // 获取商品ID集合，批量查询商品名称
        List<Long> productIds = batches.stream()
                .map(InventoryBatch::getProductId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<Long, String> productNameMap = productIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            com.agri_system.domain.product.model.Product product = 
                                    productMapper.selectOneById(id);
                            return product != null && product.getName() != null 
                                    ? product.getName() 
                                    : "商品" + id;
                        }));
        
        return batches.stream()
                .map(batch -> {
                    SlowMovingInventoryVO vo = new SlowMovingInventoryVO();
                    vo.setProductId(batch.getProductId());
                    vo.setProductName(productNameMap.getOrDefault(batch.getProductId(), "商品" + batch.getProductId()));
                    vo.setBatchNo(batch.getBatchNo());
                    vo.setQuantity(defaultZero(batch.getQuantity()));
                    vo.setValue(defaultZero(batch.getQuantity()).multiply(defaultZero(batch.getCostPrice())));
                    vo.setProductionDate(batch.getProductionDate());
                    vo.setCreatedAt(batch.getCreatedAt());
                    if (batch.getProductionDate() != null) {
                        long days = java.time.temporal.ChronoUnit.DAYS.between(batch.getProductionDate(), LocalDate.now());
                        vo.setAgeDays(days);
                    }
                    return vo;
                })
                .sorted(Comparator.comparing(SlowMovingInventoryVO::getAgeDays).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 财务风控分析
     * @param storeId 门店ID（可选，仅影响还款记录筛选；赊销账户无门店字段）
     */
    @Transactional(readOnly = true)
    public CreditRiskVO creditRisk(Long storeId) {
        // 总欠款
        BigDecimal totalDebt = creditAccountMapper.selectOneByQueryAs(
                QueryWrapper.create()
                        .select("COALESCE(SUM(remaining_amount),0) AS total")
                        .where("status = 1"), // 未还清
                BigDecimal.class);
        totalDebt = defaultZero(totalDebt);
        
        // 逾期欠款（due_date < NOW）
        BigDecimal overdueDebt = creditAccountMapper.selectOneByQueryAs(
                QueryWrapper.create()
                        .select("COALESCE(SUM(remaining_amount),0) AS total")
                        .where("status = 1")
                        .and("due_date is not null")
                        .and("due_date < CURDATE()"),
                BigDecimal.class);
        overdueDebt = defaultZero(overdueDebt);
        
        // 本月新增赊销额（credit_account.created_at在本月）
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        LocalDateTime monthStartTime = monthStart.atStartOfDay();
        LocalDateTime monthEndTime = monthEnd.atTime(LocalTime.MAX);
        
        BigDecimal monthlyNewCredit = creditAccountMapper.selectOneByQueryAs(
                QueryWrapper.create()
                        .select("COALESCE(SUM(credit_amount),0) AS total")
                        .where("created_at between ? and ?", monthStartTime, monthEndTime),
                BigDecimal.class);
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
        
        // 回款率 = 本月实收 / (本月实收 + 本月新增) * 100
        BigDecimal denominator = monthlyRepayment.add(monthlyNewCredit);
        BigDecimal repaymentRate;
        if (denominator.compareTo(BigDecimal.ZERO) > 0) {
            repaymentRate = monthlyRepayment
                    .divide(denominator, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            repaymentRate = BigDecimal.valueOf(100); // 分母为0，返回100%
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


