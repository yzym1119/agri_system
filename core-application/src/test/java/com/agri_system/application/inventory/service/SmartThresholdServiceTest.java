package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.vo.ThresholdRecommendation;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能阈值推荐：真实当前库存、样本不足可解释、绝不把阈值当作“安全”。
 *
 * <p>当前库存来自批次余额的<b>单次分组查询</b>（available = base - reserved），
 * 季节系数所需的近7天/长期速度也在每次计算时一次性预加载，不再逐商品重复查询。</p>
 */
@ExtendWith(MockitoExtension.class)
class SmartThresholdServiceTest {

    private static final Long STORE_ID = 1L;
    private static final Long PRODUCT_ID = 5L;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private InventoryBatchBalanceRepository batchBalanceRepository;

    private SmartThresholdService service;

    @BeforeEach
    void setUp() {
        service = new SmartThresholdService(orderItemMapper, batchBalanceRepository);
    }

    @Test
    void recommendationUsesRealCurrentStockAndMarksInsufficientHistory() {
        when(orderItemMapper.selectProductSalesVelocity(90, STORE_ID))
                .thenReturn(List.of(velocityRow(PRODUCT_ID, "0.05", "5", "2")));
        when(orderItemMapper.selectProductSalesVelocity(7, STORE_ID)).thenReturn(List.of());
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByProduct(STORE_ID))
                .thenReturn(Map.of(PRODUCT_ID, new BigDecimal("24")));

        ThresholdRecommendation recommendation = service.calculateRecommendations(STORE_ID, 90).get(0);

        assertThat(recommendation.getCurrentStock()).isEqualByComparingTo("24");
        assertThat(recommendation.getConfidence()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void groupedStockQueryRunsOnceAndInsufficientRowsKeepWindowAndEvidence() {
        when(orderItemMapper.selectProductSalesVelocity(90, STORE_ID)).thenReturn(List.of(
                velocityRow(1L, "4.00", "360", "12"),
                velocityRow(2L, "0.04", "4", "1")));
        when(orderItemMapper.selectProductSalesVelocity(7, STORE_ID)).thenReturn(List.of(
                velocityRow(1L, "6.00", "42", "3"),
                velocityRow(2L, "0.04", "4", "1")));
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByProduct(STORE_ID)).thenReturn(
                Map.of(1L, new BigDecimal("30"), 2L, new BigDecimal("8")));

        List<ThresholdRecommendation> recommendations = service.calculateRecommendations(STORE_ID, 90);

        assertThat(recommendations).hasSize(2);
        // 门店库存只做一次分组查询，绝不逐商品查询
        verify(batchBalanceRepository, times(1)).sumAvailableBaseQuantityGroupedByProduct(STORE_ID);
        // 速度只预加载两次（观察窗口 + 近7天），与商品数量无关
        verify(orderItemMapper, times(1)).selectProductSalesVelocity(90, STORE_ID);
        verify(orderItemMapper, times(1)).selectProductSalesVelocity(7, STORE_ID);

        ThresholdRecommendation insufficient = recommendations.get(1);
        assertThat(insufficient.getProductId()).isEqualTo(2L);
        assertThat(insufficient.getStatus()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(insufficient.getConfidence()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(insufficient.getCurrentStock()).isEqualByComparingTo("8");
        assertThat(insufficient.getObservationDays()).isEqualTo(90);
        assertThat(insufficient.getOrderCount()).isEqualTo(1);
        assertThat(insufficient.getTotalSold()).isEqualByComparingTo("4");

        ThresholdRecommendation sufficient = recommendations.get(0);
        assertThat(sufficient.getProductId()).isEqualTo(1L);
        assertThat(sufficient.getConfidence()).isEqualTo("SUFFICIENT_DATA");
        assertThat(sufficient.getObservationDays()).isEqualTo(90);
        assertThat(sufficient.getStatus()).isEqualTo("LOW");
    }

    @Test
    void withinBandIsReportedAsNormalAndNeverAsSafe() {
        when(orderItemMapper.selectProductSalesVelocity(90, STORE_ID))
                .thenReturn(List.of(velocityRow(PRODUCT_ID, "1.00", "90", "9")));
        when(orderItemMapper.selectProductSalesVelocity(7, STORE_ID)).thenReturn(List.of());
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByProduct(STORE_ID))
                .thenReturn(Map.of(PRODUCT_ID, new BigDecimal("20")));

        ThresholdRecommendation recommendation = service.calculateRecommendations(STORE_ID, 90).get(0);

        assertThat(recommendation.getStatus()).isEqualTo("NORMAL");
        assertThat(recommendation.getStatus()).isNotEqualTo("SAFE");
        assertThat(recommendation.getConfidence()).isEqualTo("SUFFICIENT_DATA");
        assertThat(recommendation.getCurrentStock()).isEqualByComparingTo("20");
    }

    @Test
    void productWithoutGroupedBalanceKeepsZeroDefaultButNotAsSafetyProof() {
        when(orderItemMapper.selectProductSalesVelocity(90, STORE_ID))
                .thenReturn(List.of(velocityRow(PRODUCT_ID, "1.00", "90", "9")));
        when(orderItemMapper.selectProductSalesVelocity(7, STORE_ID)).thenReturn(List.of());
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByProduct(STORE_ID)).thenReturn(Map.of());

        ThresholdRecommendation recommendation = service.calculateRecommendations(STORE_ID, 90).get(0);

        assertThat(recommendation.getCurrentStock()).isEqualByComparingTo("0");
        assertThat(recommendation.getStatus()).isEqualTo("LOW");
        assertThat(recommendation.getConfidence()).isEqualTo("SUFFICIENT_DATA");
    }

    private Map<String, Object> velocityRow(Long productId, String avgDailySales, String totalSold, String orderCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("productId", productId);
        row.put("productName", "商品" + productId);
        row.put("productCode", "P" + productId);
        row.put("mainUnit", "袋");
        row.put("totalSold", new BigDecimal(totalSold));
        row.put("avgDailySales", new BigDecimal(avgDailySales));
        row.put("orderCount", new BigDecimal(orderCount));
        return row;
    }
}
