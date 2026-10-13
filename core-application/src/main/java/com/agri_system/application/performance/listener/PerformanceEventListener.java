package com.agri_system.application.performance.listener;

import com.agri_system.application.customer.service.CustomerSettlementService;
import com.agri_system.application.performance.config.PerformanceProperties;
import com.agri_system.application.performance.event.*;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.performance.model.*;
import com.agri_system.domain.performance.repository.CommissionRuleRepository;
import com.agri_system.domain.performance.repository.PerformanceEventRecordRepository;
import com.agri_system.domain.performance.repository.StaffCustomerRelationRepository;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.service.PerformanceDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceEventListener {

    private final PerformanceProperties properties;
    private final PerformanceDomainService performanceDomainService;
    private final CommissionRuleRepository commissionRuleRepository;
    private final StaffPerformanceLogRepository logRepository;
    private final PerformanceEventRecordRepository eventRecordRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StaffCustomerRelationRepository relationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomerSettlementService customerSettlementService;
    private final AfterSalesRepository afterSalesRepository;
    private final AfterSalesItemRepository afterSalesItemRepository;

    private static final String EVENT_ORDER_FINISHED = "ORDER_FINISHED";
    private static final String EVENT_ORDER_REFUNDED = "ORDER_REFUNDED";
    private static final String EVENT_CUSTOMER_RECHARGE = "CUSTOMER_RECHARGE";
    private static final String EVENT_CUSTOMER_BIND_APPROVED = "CUSTOMER_BIND_APPROVED";
    private static final String EVENT_CUSTOMER_FIRST_EFFECTIVE = "CUSTOMER_FIRST_EFFECTIVE";

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onOrderFinished(OrderFinishedEvent event) {
        Long orderId = event.orderId();
        if (!markEvent(EVENT_ORDER_FINISHED, orderId)) {
            return;
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("订单不存在：" + orderId));
        
        // ⛔️ Critical Fix: 调用客户结算服务，更新客户统计和发放积分
        customerSettlementService.onOrderFinished(order);
        
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items == null || items.isEmpty() || order.getStaffId() == null) {
            return;
        }

        Map<Long, Product> productCache = new HashMap<>();
        BigDecimal totalProfit = BigDecimal.ZERO;
        List<StaffPerformanceLog> logs = new ArrayList<>();
        Map<OrderItem, SaleFact> saleFacts = new IdentityHashMap<>();
        Map<PackageGroup, List<OrderItem>> packageGroups = new LinkedHashMap<>();
        for (OrderItem item : items) {
            Product product = null;
            if (item.getProductId() != null) {
                product = productCache.computeIfAbsent(item.getProductId(), id ->
                        productRepository.findById(id).orElse(null));
            }
            SaleFact fact = saleFact(item, product);
            saleFacts.put(item, fact);
            totalProfit = totalProfit.add(fact.saleAmount().subtract(fact.costAmount()));
            if (item.getPackageId() != null && item.getPackageGroupNo() != null) {
                packageGroups.computeIfAbsent(new PackageGroup(item.getPackageId(), item.getPackageGroupNo()),
                        ignored -> new ArrayList<>()).add(item);
            } else {
                // 无组号的历史炸开行没有实际套数证据，只保留可核实的单品提成。
                appendProductCommission(order, item, fact, logs);
            }
        }
        for (var group : packageGroups.entrySet()) {
            Optional<CommissionRule> rule = commissionRuleRepository
                    .findBestSalesRule(null, group.getKey().packageId(), order.getStaffId());
            if (rule.isEmpty()) {
                for (OrderItem item : group.getValue()) appendProductCommission(order, item, saleFacts.get(item), logs);
                continue;
            }
            BigDecimal saleAmount = BigDecimal.ZERO;
            BigDecimal costAmount = BigDecimal.ZERO;
            BigDecimal packageQuantity = group.getValue().get(0).getPackageQuantity();
            for (OrderItem item : group.getValue()) {
                BigDecimal quantity = item.getPackageQuantity();
                if (packageQuantity == null || quantity == null || quantity.signum() <= 0
                        || packageQuantity.compareTo(quantity) != 0) {
                    throw new IllegalStateException("套餐提成缺少一致的实际套数快照：" + group.getKey().groupNo());
                }
                SaleFact fact = saleFacts.get(item);
                saleAmount = saleAmount.add(fact.saleAmount());
                costAmount = costAmount.add(fact.costAmount());
            }
            var result = performanceDomainService.calculateSalesCommission(saleAmount, costAmount,
                    packageQuantity, rule.get(), true, properties.getMaxCommissionRatio());
            allocatePackageCommission(order, group.getValue(), saleFacts, saleAmount, result, logs);
        }

        if (!logs.isEmpty()) {
            logRepository.saveBatch(logs);
        }

        handleNewCustomerIfNeeded(order, items, totalProfit);
    }

    private SaleFact saleFact(OrderItem item, Product product) {
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
        // 分摊金额是权威成交事实；12位单价仅供展示，不能再次相乘制造分摊尾差。
        BigDecimal sale = item.getTotalPrice();
        if (sale == null) sale = item.getUnitPrice() != null ? item.getUnitPrice().multiply(quantity) : BigDecimal.ZERO;
        BigDecimal cost;
        if (item.getUnitCost() != null && item.getBaseQuantity() != null) {
            cost = item.getUnitCost().multiply(item.getBaseQuantity());
        } else {
            BigDecimal unitCost = item.getCostPriceSnapshot();
            if (unitCost == null && product != null) unitCost = product.getAvgCostPrice();
            cost = unitCost != null ? unitCost.multiply(quantity) : BigDecimal.ZERO;
        }
        return new SaleFact(sale, cost);
    }

    private void appendProductCommission(Order order, OrderItem item, SaleFact fact, List<StaffPerformanceLog> logs) {
        Optional<CommissionRule> rule = commissionRuleRepository.findBestSalesRule(item.getProductId(), null, order.getStaffId());
        if (rule.isEmpty()) return;
        var result = performanceDomainService.calculateSalesCommission(fact.saleAmount(), fact.costAmount(),
                item.getQuantity(), rule.get(), true, properties.getMaxCommissionRatio());
        appendCommissionLog(order, item, result.amount(), result.desc(), result.needReview(), logs);
    }

    /** 全组只计算一次，将提成按子行成交额分配，末个有金额的子行承接分币尾差。 */
    private void allocatePackageCommission(Order order, List<OrderItem> items, Map<OrderItem, SaleFact> facts,
                                           BigDecimal saleAmount, PerformanceDomainService.CommissionResult result,
                                           List<StaffPerformanceLog> logs) {
        if (result.amount().signum() <= 0 || saleAmount.signum() <= 0) return;
        List<OrderItem> pricedItems = items.stream().filter(item -> facts.get(item).saleAmount().signum() > 0).toList();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < pricedItems.size(); i++) {
            OrderItem item = pricedItems.get(i);
            BigDecimal share = i == pricedItems.size() - 1 ? result.amount().subtract(allocated)
                    : result.amount().multiply(facts.get(item).saleAmount()).divide(saleAmount, 2, RoundingMode.DOWN);
            allocated = allocated.add(share);
            appendCommissionLog(order, item, share, "套餐组 " + item.getPackageGroupNo() + "，"
                    + result.desc() + "，按子行成交额分配", result.needReview(), logs);
        }
    }

    private void appendCommissionLog(Order order, OrderItem item, BigDecimal amount, String desc,
                                      boolean needReview, List<StaffPerformanceLog> logs) {
        if (amount.signum() <= 0) return;
        StaffPerformanceLog entry = new StaffPerformanceLog();
        entry.setStaffId(order.getStaffId()); entry.setStoreId(order.getStoreId());
        entry.setBizType(PerformanceBizType.SALES_COMMISSION.getCode()); entry.setBizId(order.getId());
        entry.setOrderItemId(item.getId()); entry.setAmount(amount); entry.setCalcDesc(desc);
        entry.setNeedReview(needReview ? 1 : 0);
        if (properties.isFreezeEnabled()) entry.markFrozen(properties.getFreezeDays()); else entry.markImmediate();
        logs.add(entry);
    }

    private record SaleFact(BigDecimal saleAmount, BigDecimal costAmount) {}
    private record PackageGroup(Long packageId, Integer groupNo) {}

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onOrderRefunded(OrderRefundedEvent event) {
        Long orderId = event.orderId();
        // 先锁订单再做一致性读，累计售后数量和已回冲提成不能取到并发退款之前的旧快照。
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("订单不存在：" + orderId));
        String refundEventKey = buildRefundEventKey(event);
        if (!markEvent(refundEventKey, EVENT_ORDER_REFUNDED, orderId)) {
            return;
        }
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // ⛔️ 回退客户积分 + 累计消费/订单数统计（与 onOrderFinished 对称，闭环退款副作用）
        customerSettlementService.onOrderRefunded(order, event.refundAmount());

        BigDecimal ratio = event.refundAmount().divide(payAmount, 4, RoundingMode.HALF_UP);
        List<StaffPerformanceLog> positives = logRepository.findByOrderId(orderId);
        if (positives == null || positives.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> refundItems = event.refundItems() != null ? event.refundItems() : Collections.emptyMap();
        Map<Long, BigDecimal> orderItemQuantityMap = refundItems.isEmpty() ? Collections.emptyMap() : loadOrderItemQuantityMap(orderId);
        Map<Long, BigDecimal> cumulativeQuantities = refundItems.isEmpty() ? Collections.emptyMap() : loadFinishedReturnQuantities(orderId);
        Map<Long, BigDecimal> deductedAmounts = new HashMap<>();
        List<StaffPerformanceLog> previousDeductions = logRepository.findByBiz(orderId, PerformanceBizType.REFUND_DEDUCT.getCode());
        if (previousDeductions != null) {
            for (StaffPerformanceLog previous : previousDeductions) {
                if (previous.getAmount() != null && previous.getAmount().signum() < 0) {
                    deductedAmounts.merge(previous.getOrderItemId(), previous.getAmount().negate(), BigDecimal::add);
                }
            }
        }
        List<StaffPerformanceLog> negatives = new ArrayList<>();
        for (StaffPerformanceLog logEntry : positives) {
            if (logEntry.getAmount() == null || logEntry.getAmount().signum() <= 0) continue;
            BigDecimal deductRatio = ratio;
            BigDecimal cumulativeQuantity = null;
            BigDecimal originalQuantity = null;
            if (!refundItems.isEmpty()) {
                Long orderItemId = logEntry.getOrderItemId();
                if (orderItemId == null || !refundItems.containsKey(orderItemId)) {
                    continue;
                }
                originalQuantity = orderItemQuantityMap.get(orderItemId);
                if (originalQuantity == null || originalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal refundedQuantity = refundItems.get(orderItemId);
                if (refundedQuantity == null || refundedQuantity.signum() <= 0) continue;
                deductRatio = refundedQuantity.divide(originalQuantity, 4, RoundingMode.HALF_UP);
                if (deductRatio.compareTo(BigDecimal.ONE) > 0) {
                    deductRatio = BigDecimal.ONE;
                }
                cumulativeQuantity = cumulativeQuantities.get(orderItemId);
            }
            BigDecimal alreadyDeducted = deductedAmounts.getOrDefault(logEntry.getOrderItemId(), BigDecimal.ZERO);
            BigDecimal remaining = logEntry.getAmount().subtract(alreadyDeducted).max(BigDecimal.ZERO);
            BigDecimal deductAmount;
            if (cumulativeQuantity != null) {
                // 从实退数量直接算累计应冲，再补与既有回冲的差额；退完最后一件必须精确归零。
                BigDecimal target = cumulativeQuantity.compareTo(originalQuantity) >= 0 ? logEntry.getAmount()
                        : logEntry.getAmount().multiply(cumulativeQuantity).divide(originalQuantity, 2, RoundingMode.HALF_UP);
                deductAmount = target.subtract(alreadyDeducted).max(BigDecimal.ZERO).min(remaining);
            } else {
                // 无历史实退数量证据，不从金额或提成猜数量；保留旧单次比例并限制累计不能超冲。
                deductAmount = logEntry.getAmount().multiply(deductRatio).setScale(2, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO).min(remaining);
            }
            if (deductAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            StaffPerformanceLog negative = new StaffPerformanceLog();
            negative.setStaffId(logEntry.getStaffId());
            negative.setStoreId(logEntry.getStoreId());
            negative.setBizType(PerformanceBizType.REFUND_DEDUCT.getCode());
            negative.setBizId(orderId);
            negative.setOrderItemId(logEntry.getOrderItemId());
            negative.setAmount(deductAmount.negate());
            negative.setCalcDesc(cumulativeQuantity != null ? "退款扣减，按累计实退数量 " + cumulativeQuantity + " 补足应冲差额"
                    : "退款扣减，按比例 " + deductRatio);
            negative.setNeedReview(0);
            negative.setStatus(logEntry.getStatus());
            if (logEntry.getStatus() != null
                    && logEntry.getStatus() == PerformanceLogStatus.FROZEN.getCode()) {
                negative.setFreezeUntil(logEntry.getFreezeUntil());   // 冻结正数:负数同日解冻,净额自动正确
            } else {
                negative.setFreezeUntil(LocalDateTime.now());          // 已解冻正数:负数落当期/下期账单(回冲进下期)
            }
            negatives.add(negative);
            deductedAmounts.merge(logEntry.getOrderItemId(), deductAmount, BigDecimal::add);
        }
        if (!negatives.isEmpty()) {
            logRepository.saveBatch(negatives);
        }
    }

    /** 只读取已完成售后中的实际数量；申请、待退款和驳回记录均不属于已退款事实。 */
    private Map<Long, BigDecimal> loadFinishedReturnQuantities(Long orderId) {
        Map<Long, BigDecimal> quantities = new HashMap<>();
        List<AfterSales> completed = afterSalesRepository.findByOrderId(orderId);
        if (completed == null) return quantities;
        for (AfterSales afterSales : completed) {
            if (!Integer.valueOf(AfterSales.STATUS_FINISHED).equals(afterSales.getStatus())) continue;
            var items = afterSalesItemRepository.findByAfterSalesId(afterSales.getId());
            if (items == null) continue;
            for (var item : items) {
                if (item.getOrderItemId() != null && item.getQuantity() != null && item.getQuantity().signum() > 0) {
                    quantities.merge(item.getOrderItemId(), item.getQuantity(), BigDecimal::add);
                }
            }
        }
        return quantities;
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onCustomerRecharge(CustomerRechargeEvent event) {
        if (event.guideStaffId() == null) {
            return;
        }
        String eventKey = EVENT_CUSTOMER_RECHARGE + ":" + event.customerId() + ":" + event.amount() + ":" + event.guideStaffId();
        if (!markEvent(eventKey, EVENT_CUSTOMER_RECHARGE, event.customerId())) {
            return;
        }
        List<CommissionRule> rules = commissionRuleRepository.findByCategory(
                CommissionRule.Category.RECHARGE.getCode());
        Optional<CommissionRule> ruleOptional = selectRule(rules, event.guideStaffId());
        if (ruleOptional.isEmpty()) {
            return;
        }
        CommissionRule rule = ruleOptional.get();
        BigDecimal amount = calculateAmountByRule(rule, event.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        StaffPerformanceLog logEntry = new StaffPerformanceLog();
        logEntry.setStaffId(event.guideStaffId());
        logEntry.setStoreId(0L);
        logEntry.setBizType(PerformanceBizType.RECHARGE_GUIDE_REWARD.getCode());
        logEntry.setBizId(event.customerId());
        logEntry.setAmount(amount);
        logEntry.setCalcDesc("充值引导奖励");
        logEntry.setNeedReview(0);
        logEntry.markImmediate();
        logRepository.save(logEntry);
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onCustomerBindApproved(CustomerBindApprovedEvent event) {
        String key = EVENT_CUSTOMER_BIND_APPROVED + ":" + event.customerId();
        if (!markEvent(key, EVENT_CUSTOMER_BIND_APPROVED, event.customerId())) {
            return;
        }
        StaffCustomerRelation relation = relationRepository.findByCustomer(event.customerId())
                .orElseGet(() -> {
                    StaffCustomerRelation newRelation = new StaffCustomerRelation();
                    newRelation.setCustomerId(event.customerId());
                    newRelation.setStaffId(event.staffId());
                    newRelation.setStatus(StaffCustomerRelation.STATUS_PENDING);
                    return newRelation;
                });
        relation.setBindSource(event.bindSource());
        relation.setBindOrderId(event.bindOrderId());
        relation.approve(event.bindOrderId());
        relationRepository.save(relation);
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onCustomerFirstEffective(CustomerFirstEffectiveEvent event) {
        String key = EVENT_CUSTOMER_FIRST_EFFECTIVE + ":" + event.customerId();
        if (!markEvent(key, EVENT_CUSTOMER_FIRST_EFFECTIVE, event.customerId())) {
            return;
        }
        StaffCustomerRelation relation = relationRepository.findByCustomer(event.customerId())
                .orElse(null);
        if (relation == null || relation.getFirstEffectiveAt() != null) {
            return;
        }
        relation.markFirstEffective();
        relationRepository.save(relation);

        List<CommissionRule> rules = commissionRuleRepository.findByCategory(
                CommissionRule.Category.NEW_CUSTOMER.getCode());
        Optional<CommissionRule> ruleOptional = selectRule(rules, relation.getStaffId());
        if (ruleOptional.isEmpty()) {
            return;
        }
        BigDecimal reward = calculateAmountByRule(ruleOptional.get(), BigDecimal.ONE);
        if (reward.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        StaffPerformanceLog logEntry = new StaffPerformanceLog();
        logEntry.setStaffId(relation.getStaffId());
        logEntry.setStoreId(0L);
        logEntry.setBizType(PerformanceBizType.NEW_CUSTOMER_REWARD.getCode());
        logEntry.setBizId(event.orderId());
        logEntry.setAmount(reward);
        logEntry.setCalcDesc("拉新奖金");
        logEntry.setNeedReview(0);
        logEntry.markImmediate();
        logRepository.save(logEntry);
    }

    private void handleNewCustomerIfNeeded(Order order, List<OrderItem> items, BigDecimal totalProfit) {
        if (order.getCustomerId() == null) {
            return;
        }
        StaffCustomerRelation relation = relationRepository.findByCustomer(order.getCustomerId())
                .orElse(null);
        if (relation == null || relation.getFirstEffectiveAt() != null
                || relation.getStaffId() == null || !relation.getStaffId().equals(order.getStaffId())
                || relation.getStatus() == null || relation.getStatus() != StaffCustomerRelation.STATUS_APPROVED) {
            return;
        }
        if (!"QR".equalsIgnoreCase(relation.getBindSource())) {
            return;
        }
        boolean hitCategory = false;
        if (!properties.getNewCustomerCategoryWhitelist().isEmpty()) {
            for (OrderItem item : items) {
                if (item.getProductId() == null) {
                    continue;
                }
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null && properties.getNewCustomerCategoryWhitelist().contains(product.getCategoryId())) {
                    hitCategory = true;
                    break;
                }
            }
        }
        BigDecimal amount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        boolean effective = amount.compareTo(properties.getNewCustomerAmountThreshold()) >= 0
                || totalProfit.compareTo(properties.getNewCustomerProfitThreshold()) >= 0
                || (hitCategory && amount.compareTo(properties.getNewCustomerMinCategoryAmount()) >= 0);
        if (effective) {
            eventPublisher.publishEvent(new CustomerFirstEffectiveEvent(order.getCustomerId(),
                    order.getStaffId(), order.getId()));
        }
    }

    private Optional<CommissionRule> selectRule(List<CommissionRule> rules, Long staffId) {
        if (rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(rule -> staffId == null || rule.getStaffId() == null || rule.getStaffId().equals(staffId))
                .sorted((a, b) -> {
                    int staffCompare = Boolean.compare(b.getStaffId() != null, a.getStaffId() != null);
                    if (staffCompare != 0) {
                        return staffCompare;
                    }
                    return Integer.compare(
                            b.getPriority() == null ? 0 : b.getPriority(),
                            a.getPriority() == null ? 0 : a.getPriority());
                })
                .findFirst();
    }

    private BigDecimal calculateAmountByRule(CommissionRule rule, BigDecimal baseAmount) {
        return switch (rule.getCalcMode()) {
            case "FIXED" -> rule.getCalcValue();
            case "SALES_PERCENT", "PROFIT_PERCENT" -> baseAmount.multiply(rule.getCalcValue())
                    .setScale(2, RoundingMode.HALF_UP);
            default -> BigDecimal.ZERO;
        };
    }

    private boolean markEvent(String type, Long bizId) {
        String key = type + ":" + bizId;
        return markEvent(key, type, bizId);
    }

    private String buildRefundEventKey(OrderRefundedEvent event) {
        if (event.refundKey() != null && !event.refundKey().trim().isEmpty()) {
            return EVENT_ORDER_REFUNDED + ":" + event.refundKey();
        }
        StringBuilder builder = new StringBuilder(EVENT_ORDER_REFUNDED)
                .append(":")
                .append(event.orderId())
                .append(":")
                .append(event.refundAmount());
        Map<Long, BigDecimal> refundItems = event.refundItems();
        if (refundItems != null && !refundItems.isEmpty()) {
            refundItems.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(":")
                            .append(entry.getKey())
                            .append("=")
                            .append(entry.getValue()));
        }
        return builder.toString();
    }

    private Map<Long, BigDecimal> loadOrderItemQuantityMap(Long orderId) {
        Map<Long, BigDecimal> quantities = new HashMap<>();
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        if (orderItems == null || orderItems.isEmpty()) {
            return quantities;
        }
        for (OrderItem orderItem : orderItems) {
            if (orderItem.getId() == null || orderItem.getQuantity() == null) {
                continue;
            }
            quantities.put(orderItem.getId(), orderItem.getQuantity());
        }
        return quantities;
    }

    private boolean markEvent(String eventKey, String type, Long bizId) {
        if (eventRecordRepository.existsByEventKey(eventKey)) {
            return false;
        }
        PerformanceEventRecord record = new PerformanceEventRecord();
        record.setEventKey(eventKey);
        record.setEventType(type);
        record.setBizId(bizId);
        try {
            eventRecordRepository.save(record);
            return true;
        } catch (DuplicateKeyException ex) {
            log.warn("Performance event duplicate key: {}", eventKey);
            return false;
        }
    }
}
