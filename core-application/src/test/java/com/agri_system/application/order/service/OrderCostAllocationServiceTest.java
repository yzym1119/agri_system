package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.OrderMarginVO;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.order.enums.BatchEvidenceLevel;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task6 零售事实链：销售成本来自批次归属事实，而非商品级平均成本缓存。
 *
 * <p>只有 SCANNED/FULFILLMENT_CONFIRMED/RULE_ALLOCATED 且批次单位成本非空的归属事实才构成
 * ACTUAL 成本；仅有历史 cost_price_snapshot 的订单标记为 HISTORICAL_ESTIMATE，
 * 绝不被重新计算或伪装成实际成本；UNKNOWN 证据或缺失成本不得计入实际毛利。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderCostAllocationServiceTest {

    private static final Long ITEM_ID = 900L;
    private static final Long ORDER_ID = 500L;
    private static final Long PRODUCT_ID = 99L;
    private static final Long BASE_UNIT_ID = 31L;
    private static final Long OPERATOR_ID = 7L;

    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderItemBatchAllocationRepository allocationRepository;
    @Mock
    private ProductUnitService productUnitService;

    private OrderCostAllocationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCostAllocationService(orderItemMapper, allocationRepository, productUnitService);
    }

    @Test
    void marginSumsTwoAllocatedBatchCostsRatherThanProductAverageCost() {
        OrderMarginVO margin = service.calculate(orderItemIdWithTwoAllocations());
        assertThat(margin.actualCost()).isEqualByComparingTo("156.00");
        assertThat(margin.costConfidence()).isEqualTo("ACTUAL");
    }

    @Test
    void unknownBatchDoesNotClaimActualMargin() {
        assertThat(service.calculate(orderItemIdWithUnknownAllocation()).costConfidence())
            .isEqualTo("ESTIMATED_OR_UNAVAILABLE");
    }

    @Test
    void historicalSnapshotOrderIsEstimatedNeverActual() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of());

        OrderMarginVO margin = service.calculate(ITEM_ID);

        assertThat(margin.costConfidence()).isEqualTo("HISTORICAL_ESTIMATE");
        assertThat(margin.actualCost()).isNull();
        assertThat(margin.estimatedCost()).isEqualByComparingTo("100.00");
        assertThat(margin.saleAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void unknownEvidenceNeverMergesIntoActualCost() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of(
                allocation("1.5", "60.00", BatchEvidenceLevel.SCANNED),
                allocation("0.5", null, BatchEvidenceLevel.UNKNOWN)));

        OrderMarginVO margin = service.calculate(ITEM_ID);

        assertThat(margin.actualCost()).isNull();
        assertThat(margin.costConfidence()).isEqualTo("ESTIMATED_OR_UNAVAILABLE");
        assertThat(margin.estimatedCost()).isEqualByComparingTo("100.00");
    }

    @Test
    void snapshotRefreshWritesWeightedBaseUnitCostForActualAllocations() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of(
                allocation("1.5", "60.00", BatchEvidenceLevel.SCANNED),
                allocation("1.0", "66.00", BatchEvidenceLevel.FULFILLMENT_CONFIRMED)));
        when(productUnitService.getUnits(PRODUCT_ID)).thenReturn(List.of(baseUnit(BASE_UNIT_ID)));

        service.refreshOrderItemSnapshot(ITEM_ID, OPERATOR_ID);

        ArgumentCaptor<BigDecimal> baseQuantity = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> unitCost = ArgumentCaptor.forClass(BigDecimal.class);
        verify(orderItemMapper).updateBatchCostSnapshot(eq(ITEM_ID), eq(BASE_UNIT_ID),
                baseQuantity.capture(), unitCost.capture());
        assertThat(baseQuantity.getValue()).isEqualByComparingTo("2.5");
        assertThat(unitCost.getValue()).isEqualByComparingTo("62.40");
        // 重跑必须幂等：相同事实产生相同快照值
        service.refreshOrderItemSnapshot(ITEM_ID, OPERATOR_ID);
        verify(orderItemMapper, times(2)).updateBatchCostSnapshot(eq(ITEM_ID), eq(BASE_UNIT_ID),
                baseQuantity.capture(), unitCost.capture());
        assertThat(baseQuantity.getValue()).isEqualByComparingTo("2.5");
        assertThat(unitCost.getValue()).isEqualByComparingTo("62.40");
    }

    @Test
    void snapshotRefreshSkipsNonActualEvidence() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of(
                allocation("2", null, BatchEvidenceLevel.UNKNOWN)));

        service.refreshOrderItemSnapshot(ITEM_ID, OPERATOR_ID);

        verify(orderItemMapper, never()).updateBatchCostSnapshot(any(), any(), any(), any());
        verify(productUnitService, never()).getUnits(any());
    }

    @Test
    void snapshotRefreshSkipsLegacyProjectionWithoutPersistedBaseUnitId() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of(
                allocation("2", "40.00", BatchEvidenceLevel.RULE_ALLOCATED)));
        when(productUnitService.getUnits(PRODUCT_ID)).thenReturn(List.of(baseUnit(null)));

        service.refreshOrderItemSnapshot(ITEM_ID, OPERATOR_ID);

        verify(orderItemMapper, never()).updateBatchCostSnapshot(any(), any(), any(), any());
    }

    private Long orderItemIdWithTwoAllocations() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of(
                allocation("1.5", "60.00", BatchEvidenceLevel.SCANNED),
                allocation("1.0", "66.00", BatchEvidenceLevel.FULFILLMENT_CONFIRMED)));
        return ITEM_ID;
    }

    private Long orderItemIdWithUnknownAllocation() {
        when(orderItemMapper.selectOneById(ITEM_ID)).thenReturn(orderItem("2", "50.00"));
        when(allocationRepository.findByOrderItemId(ITEM_ID)).thenReturn(List.of(
                allocation("2", null, BatchEvidenceLevel.UNKNOWN)));
        return ITEM_ID;
    }

    private static OrderItem orderItem(String quantity, String costPriceSnapshot) {
        OrderItem item = new OrderItem();
        item.setId(ITEM_ID);
        item.setOrderId(ORDER_ID);
        item.setProductId(PRODUCT_ID);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setCostPriceSnapshot(costPriceSnapshot == null ? null : new BigDecimal(costPriceSnapshot));
        return item;
    }

    private static OrderItemBatchAllocation allocation(String baseQuantity, String batchUnitCost,
                                                       BatchEvidenceLevel evidenceLevel) {
        BigDecimal quantity = new BigDecimal(baseQuantity);
        BigDecimal cost = batchUnitCost == null ? null : new BigDecimal(batchUnitCost);
        OrderItemBatchAllocation allocation = new OrderItemBatchAllocation();
        allocation.setOrderItemId(ITEM_ID);
        allocation.setProductId(PRODUCT_ID);
        allocation.setAllocatedBaseQuantity(quantity);
        allocation.setBatchUnitCost(cost);
        allocation.setAllocatedCost(cost == null ? null : cost.multiply(quantity));
        allocation.setEvidenceLevel(evidenceLevel.name());
        allocation.setAllocationRuleVersion("R1");
        return allocation;
    }

    private static ProductUnit baseUnit(Long id) {
        ProductUnit unit = new ProductUnit();
        unit.setId(id);
        unit.setProductId(PRODUCT_ID);
        unit.setUnitName("斤");
        unit.setBaseQuantityPerUnit(BigDecimal.ONE);
        unit.setIsBaseUnit(1);
        unit.setStatus(1);
        return unit;
    }
}
