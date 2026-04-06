package com.agri_system.application.report.service;

import com.agri_system.application.report.dto.*;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.infrastructure.product.mapper.ProductMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ReportAnalysisService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final InventoryBatchMapper inventoryBatchMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditRepaymentLogMapper repaymentLogMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final AfterSalesMapper afterSalesMapper;

    /**
     * 今日经营驾驶舱
     */
    @Transactional(readOnly = true)
    public DashboardVO dashboardToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        // GMV 与订单数
        QueryWrapper orderQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end)
                .and("order_status in (2,3,4,5,6,9)");
        BigDecimal gmv = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COALESCE(SUM(total_amount),0) AS total"), BigDecimal.class);
        BigDecimal netSales = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COALESCE(SUM(pay_amount),0) AS total"), BigDecimal.class);
        Long orderCount = orderMapper.selectOneByQueryAs(
                orderQ.clone().select("COUNT(1) AS cnt"), Long.class);

        // 今日新增赊销额（来自 credit_account）
        QueryWrapper creditQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end);
        BigDecimal newCreditAmount = creditAccountMapper.selectOneByQueryAs(
                creditQ.select("COALESCE(SUM(credit_amount),0) AS total"), BigDecimal.class);

        // 今日新增客户数
        QueryWrapper customerQ = QueryWrapper.create()
                .where("created_at between ? and ?", start, end);
        Long newCustomerCount = customerMapper.selectOneByQueryAs(
                customerQ.select("COUNT(1) AS cnt"), Long.class);

        // 待审核售后数（after_sales.status = 10 STATUS_APPLIED）
        Long pendingAfterSales = afterSalesMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where("status = ?", 10));

        // 待审核同城配送数（order.delivery_status=1 WAIT_AUDIT）
        Long pendingDelivery = orderMapper.selectOneByQueryAs(
                QueryWrapper.create()
                        .where("order_type = 3")
                        .and("delivery_status = 1"),
                Long.class);

        // 库存预警数（简单用库龄 > 180 天的批次数量代表）
        LocalDate threshold = today.minusDays(180);
        Long inventoryAlertCount = inventoryBatchMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where("is_active = 1")
                        .and("production_date is not null")
                        .and("production_date <= ?", threshold));

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
     * 客户价值分析：大户排行榜 + 流失预警
     */
    @Transactional(readOnly = true)
    public List<CustomerRankVO> topCustomers(int limit) {
        QueryWrapper qw = QueryWrapper.create()
                .where("total_order_amount is not null")
                .orderBy("total_order_amount desc")
                .limit(limit);
        List<Customer> customers = customerMapper.selectListByQuery(qw);
        return customers.stream()
                .map(c -> {
                    CustomerRankVO vo = new CustomerRankVO();
                    vo.setCustomerId(c.getId());
                    vo.setCustomerName(c.getName());
                    vo.setPhone(c.getPhone());
                    vo.setTotalOrderAmount(defaultZero(c.getTotalOrderAmount()));
                    vo.setLastOrderTime(c.getLastOrderTime());
                    vo.setStaffName(null); // 如需展示员工姓名，可联表 staff 表做一次 join
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChurnRiskVO> churnRisks() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(90);
        QueryWrapper qw = QueryWrapper.create()
                .where("last_order_time is not null")
                .and("last_order_time < ?", deadline)
                .and("total_order_amount > 1000");
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
     */
    @Transactional(readOnly = true)
    public InventoryValueVO inventoryValue() {
        // 有效批次资金占用
        List<InventoryBatch> batches = inventoryBatchMapper.selectListByQuery(
                QueryWrapper.create().where("is_active = 1"));
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
                .select("id")
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
        List<OrderItem> items = orderItemMapper.selectListByQuery(
                QueryWrapper.create()
                        .where("order_id in (?)", orderIds));
        
        // 获取订单创建时间映射
        Map<Long, LocalDateTime> orderTimeMap = orders.stream()
                .collect(Collectors.toMap(Order::getId, Order::getCreatedAt));
        
        // 按日期聚合
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
                return v;
            });
            
            BigDecimal sale = defaultZero(item.getUnitPrice()).multiply(defaultZero(item.getQuantity()));
            BigDecimal cost = defaultZero(item.getCostPriceSnapshot()).multiply(defaultZero(item.getQuantity()));
            vo.setSaleAmount(vo.getSaleAmount().add(sale));
            vo.setCostAmount(vo.getCostAmount().add(cost));
        }
        
        // 计算毛利和毛利率
        return trendMap.values().stream()
                .peek(vo -> {
                    vo.setProfit(vo.getSaleAmount().subtract(vo.getCostAmount()));
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
     */
    @Transactional(readOnly = true)
    public List<SlowMovingInventoryVO> slowMovingInventory() {
        LocalDate threshold = LocalDate.now().minusDays(180);
        List<InventoryBatch> batches = inventoryBatchMapper.selectListByQuery(
                QueryWrapper.create()
                        .where("is_active = 1")
                        .and("quantity > 0")
                        .and("production_date is not null")
                        .and("production_date <= ?", threshold));
        
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
     */
    @Transactional(readOnly = true)
    public CreditRiskVO creditRisk() {
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
        BigDecimal monthlyRepayment = repaymentLogMapper.selectOneByQueryAs(
                QueryWrapper.create()
                        .select("COALESCE(SUM(amount),0) AS total")
                        .where("created_at between ? and ?", monthStartTime, monthEndTime),
                BigDecimal.class);
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
}


