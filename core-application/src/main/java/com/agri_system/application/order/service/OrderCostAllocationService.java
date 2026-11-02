package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.OrderMarginVO;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 销售成本批次归属核算应用服务（零售事实链 Task6）。
 *
 * <p>毛利只依据 {@code order_item_batch_allocation} 中的不可变批次成本事实：
 * SCANNED/FULFILLMENT_CONFIRMED/RULE_ALLOCATED 且批次单位成本非空才构成 ACTUAL 成本；
 * 无归属事实时保留历史 cost_price_snapshot 估算边界（HISTORICAL_ESTIMATE），
 * UNKNOWN 证据或缺失成本一律不得计入实际毛利。商品级 avg_cost_price 缓存仅用于商品浏览，
 * 不参与毛利核算。</p>
 *
 * <p>订单行基准单位快照（base_unit_id/base_quantity/unit_cost）仅在 ACTUAL 口径下写入，
 * 作为展示缓存；重跑幂等，且无持久化基准单位的历史投影不写任何值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCostAllocationService {

    /** unit_cost 展示快照精度。 */
    private static final int UNIT_COST_SCALE = 8;

    private final OrderItemRepository orderItemRepository;
    private final OrderItemBatchAllocationRepository allocationRepository;
    private final ProductUnitService productUnitService;

    /** 按批次归属事实核算单行毛利；成本置信度在读取时派生，不落库。 */
    @Transactional(readOnly = true)
    public OrderMarginVO calculate(Long orderItemId) {
        OrderItem item = orderItemRepository.findById(orderItemId).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("订单明细不存在: " + orderItemId);
        }
        return OrderMarginVO.from(item, allocationRepository.findByOrderItemId(orderItemId));
    }

    /**
     * 归属事实最终确定后刷新订单行成本展示快照：base_unit_id/base_quantity/unit_cost。
     *
     * <p>仅在 ACTUAL 口径（全部证据为 SCANNED/FULFILLMENT_CONFIRMED/RULE_ALLOCATED 且批次单位成本非空）
     * 下写入；{@code unit_cost = Σ allocatedCost / Σ allocatedBaseQuantity}（基准单位加权成本）。
     * 重跑幂等，绝不触碰 cost_price_snapshot 等 legacy 字段。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshOrderItemSnapshot(Long orderItemId, Long operatorId) {
        OrderItem item = orderItemRepository.findById(orderItemId).orElse(null);
        if (item == null) {
            log.warn("刷新订单行批次成本快照跳过：订单明细不存在, orderItemId={}", orderItemId);
            return;
        }
        List<OrderItemBatchAllocation> allocations = allocationRepository.findByOrderItemId(orderItemId);
        OrderMarginVO margin = OrderMarginVO.from(item, allocations);
        if (!OrderMarginVO.COST_CONFIDENCE_ACTUAL.equals(margin.costConfidence())) {
            // 历史估算/未知证据/成本缺失不写展示快照，保持 legacy 行为
            return;
        }
        BigDecimal totalBaseQuantity = totalBaseQuantity(allocations);
        if (totalBaseQuantity == null) {
            log.warn("刷新订单行批次成本快照跳过：归属事实缺少有效基准数量, orderItemId={}", orderItemId);
            return;
        }
        Long baseUnitId = resolvePersistedBaseUnitId(item.getProductId());
        if (baseUnitId == null) {
            // legacy 投影单位没有持久化ID，写入会破坏 (base_unit_id, product_id) 外键，宁缺毋滥
            log.warn("刷新订单行批次成本快照跳过：无持久化基准单位, orderItemId={}, productId={}",
                    orderItemId, item.getProductId());
            return;
        }
        BigDecimal unitCost = margin.actualCost().divide(totalBaseQuantity, UNIT_COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal writableUnitCost = unitCost.compareTo(BigDecimal.ZERO) > 0 ? unitCost : null;
        orderItemRepository.updateBatchCostSnapshot(orderItemId, baseUnitId, totalBaseQuantity, writableUnitCost);
        log.debug("订单行批次成本快照已刷新, orderItemId={}, baseUnitId={}, baseQuantity={}, unitCost={}, operatorId={}",
                orderItemId, baseUnitId, totalBaseQuantity, writableUnitCost, operatorId);
    }

    private BigDecimal totalBaseQuantity(List<OrderItemBatchAllocation> allocations) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemBatchAllocation allocation : allocations) {
            if (allocation == null || allocation.getAllocatedBaseQuantity() == null) {
                return null;
            }
            total = total.add(allocation.getAllocatedBaseQuantity());
        }
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }

    private Long resolvePersistedBaseUnitId(Long productId) {
        if (productId == null) {
            return null;
        }
        try {
            return productUnitService.getUnits(productId).stream()
                    .filter(ProductUnit::isBase)
                    .map(ProductUnit::getId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ex) {
            // 单位模型异常不得回滚履约事务：仅跳过展示快照写入
            log.warn("刷新订单行批次成本快照跳过：基准单位不可解析, productId={}", productId, ex);
            return null;
        }
    }
}
