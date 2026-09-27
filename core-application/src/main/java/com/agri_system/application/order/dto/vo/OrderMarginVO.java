package com.agri_system.application.order.dto.vo;

import com.agri_system.domain.order.enums.BatchEvidenceLevel;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单行毛利口径（零售事实链 Task6）。
 *
 * <p>销售成本只依据 {@code order_item_batch_allocation} 中的不可变批次成本事实核算，
 * 不使用商品级 avg_cost_price 缓存。成本置信度在读取时派生、不落库：</p>
 * <ul>
 *   <li>{@code ACTUAL}：存在归属事实，且全部证据级别为 SCANNED/FULFILLMENT_CONFIRMED/RULE_ALLOCATED
 *       且批次单位成本与归属成本均非空；{@code actualCost} = Σ allocatedCost，{@code estimatedCost} 为空。
 *       不要求覆盖校验。</li>
 *   <li>{@code HISTORICAL_ESTIMATE}：无归属事实但存在历史 cost_price_snapshot；估算成本 =
 *       snapshot × quantity，{@code actualCost} 为空，历史订单绝不被重新计算。</li>
 *   <li>{@code ESTIMATED_OR_UNAVAILABLE}：存在 UNKNOWN 证据或缺失成本；估算成本 = snapshot × quantity
 *       （无 snapshot 时为 null，该销售计入成本缺失口径）。</li>
 * </ul>
 *
 * @param saleAmount     销售额（元）= 单价 × 数量（null 按 0 处理）
 * @param actualCost     实际成本（元）；仅 ACTUAL 口径非空，可安全合计为实际毛利
 * @param estimatedCost  估算成本（元）；仅非 ACTUAL 口径且存在历史快照时非空，绝不可并入实际成本
 * @param costConfidence 成本置信度：ACTUAL / HISTORICAL_ESTIMATE / ESTIMATED_OR_UNAVAILABLE
 */
public record OrderMarginVO(
        BigDecimal saleAmount,
        BigDecimal actualCost,
        BigDecimal estimatedCost,
        String costConfidence) {

    /** 实际成本口径：全部证据为扫描/确认/规则归属且批次成本齐全。 */
    public static final String COST_CONFIDENCE_ACTUAL = "ACTUAL";
    /** 历史估算口径：无归属事实，沿用历史 cost_price_snapshot 估算。 */
    public static final String COST_CONFIDENCE_HISTORICAL_ESTIMATE = "HISTORICAL_ESTIMATE";
    /** 估算或不可用口径：证据含 UNKNOWN 或成本缺失，不得计入实际毛利。 */
    public static final String COST_CONFIDENCE_ESTIMATED_OR_UNAVAILABLE = "ESTIMATED_OR_UNAVAILABLE";

    /** 由订单行及其批次归属事实派生毛利口径；无归属事实时退化为历史快照估算。 */
    public static OrderMarginVO from(OrderItem item, List<OrderItemBatchAllocation> allocations) {
        BigDecimal saleAmount = defaultZero(item.getUnitPrice()).multiply(defaultZero(item.getQuantity()));
        List<OrderItemBatchAllocation> facts = allocations == null ? List.of() : allocations;

        BigDecimal actualCost = BigDecimal.ZERO;
        boolean allActual = !facts.isEmpty();
        for (OrderItemBatchAllocation fact : facts) {
            if (fact == null || !isActualEvidence(fact.getEvidenceLevel())
                    || fact.getBatchUnitCost() == null || fact.getAllocatedCost() == null) {
                allActual = false;
                break;
            }
            actualCost = actualCost.add(fact.getAllocatedCost());
        }
        if (allActual) {
            return new OrderMarginVO(saleAmount, actualCost, null, COST_CONFIDENCE_ACTUAL);
        }

        if (item.getCostPriceSnapshot() != null) {
            BigDecimal estimated = defaultZero(item.getCostPriceSnapshot()).multiply(defaultZero(item.getQuantity()));
            String confidence = facts.isEmpty()
                    ? COST_CONFIDENCE_HISTORICAL_ESTIMATE
                    : COST_CONFIDENCE_ESTIMATED_OR_UNAVAILABLE;
            return new OrderMarginVO(saleAmount, null, estimated, confidence);
        }
        return new OrderMarginVO(saleAmount, null, null, COST_CONFIDENCE_ESTIMATED_OR_UNAVAILABLE);
    }

    private static boolean isActualEvidence(String evidenceLevel) {
        return BatchEvidenceLevel.SCANNED.name().equals(evidenceLevel)
                || BatchEvidenceLevel.FULFILLMENT_CONFIRMED.name().equals(evidenceLevel)
                || BatchEvidenceLevel.RULE_ALLOCATED.name().equals(evidenceLevel);
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
