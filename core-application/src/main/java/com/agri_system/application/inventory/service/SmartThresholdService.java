package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.vo.ThresholdRecommendation;
import com.agri_system.domain.inventory.query.InventoryPlanningQuery;
import com.agri_system.domain.inventory.query.PlanningStock;
import com.agri_system.domain.inventory.query.SalesVelocity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 智能库存阈值服务
 * 基于历史销售数据自动计算推荐的安全库存、最高库存和补货量
 *
 * <p>当前库存来自批次余额的<b>单次分组查询</b>（base - reserved后换算为主单位），
 * 季节系数所需的近7天/长期销售速度也在每次计算时一次性预加载，绝不逐商品重复查询。
 * 推荐值只供人工参考（“在推荐区间内”不等于“安全”）；样本或换算证据不足的行不生成可采纳推荐值，仍返回，
 * 但以 {@code status=DATA_INSUFFICIENT} / {@code confidence=INSUFFICIENT_DATA} 明确标注。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartThresholdService {

    private final InventoryPlanningQuery planningQuery;

    /** 安全库存覆盖天数（默认7天） */
    private static final int SAFETY_DAYS = 7;
    /** 安全系数（应对需求波动） */
    private static final BigDecimal SAFETY_FACTOR = new BigDecimal("1.5");
    /** 最高库存覆盖天数（默认30天，防止积压） */
    private static final int MAX_STOCK_DAYS = 30;
    /** 补货周期天数（默认14天） */
    private static final int REORDER_CYCLE_DAYS = 14;
    /** 最低日均销量阈值，低于此值认为数据不足（照常返回，只标注置信度） */
    private static final BigDecimal MIN_SALES_THRESHOLD = new BigDecimal("0.1");
    /** 季节系数使用的近期窗口天数 */
    private static final int RECENT_WINDOW_DAYS = 7;
    /** 季节系数下限 */
    private static final BigDecimal SEASONAL_FACTOR_MIN = new BigDecimal("0.5");
    /** 季节系数上限 */
    private static final BigDecimal SEASONAL_FACTOR_MAX = new BigDecimal("3.0");

    private static final String CONFIDENCE_SUFFICIENT = "SUFFICIENT_DATA";
    private static final String CONFIDENCE_INSUFFICIENT = "INSUFFICIENT_DATA";

    /**
     * 计算智能阈值推荐
     * @param storeId 门店ID（null=全部门店）
     * @param days 统计天数（默认90天）
     * @return 每个商品的推荐阈值列表
     */
    @Transactional(readOnly = true)
    public List<ThresholdRecommendation> calculateRecommendations(Long storeId, int days) {
        if (days <= 0) days = 90;

        // 1. 查询销售速度（观察窗口）
        List<SalesVelocity> salesData = planningQuery.salesVelocity(days, storeId);
        if (salesData == null || salesData.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 查询当前库存：门店一次分组查询，缺失余额或换算证据保持未知
        Map<Long, PlanningStock> currentStockMap = planningQuery.stocks(storeId);

        // 3. 季节系数所需的近期/长期速度只预加载一次，与商品数量无关
        List<SalesVelocity> recentData = days == RECENT_WINDOW_DAYS
                ? salesData
                : planningQuery.salesVelocity(RECENT_WINDOW_DAYS, storeId);
        Map<Long, BigDecimal> windowAvgByProduct = indexAvgDailySales(salesData);
        Map<Long, BigDecimal> recentAvgByProduct = indexAvgDailySales(recentData);

        // 4. 计算推荐值
        List<ThresholdRecommendation> recommendations = new ArrayList<>();
        for (SalesVelocity row : salesData) {
            Long productId = row.productId();
            if (productId == null) continue;

            BigDecimal avgDailySales = row.avgDailySales();
            PlanningStock stock=currentStockMap.get(productId);
            boolean sameStockUnit=stock!=null&&row.mainUnit()!=null&&!row.mainUnit().isBlank()
                    &&Objects.equals(stock.mainUnit(),row.mainUnit())&&stock.basePerMainUnit()!=null&&stock.basePerMainUnit().signum()>0;
            BigDecimal currentStock=sameStockUnit?stock.quantity():null;
            boolean incompleteUnits=row.unresolvedUnitCount()>0 || recentData.stream().anyMatch(r->productId.equals(r.productId())&&(r.unresolvedUnitCount()>0||!Objects.equals(r.mainUnit(),row.mainUnit())));
            boolean insufficient = incompleteUnits || currentStock==null || avgDailySales == null || avgDailySales.compareTo(MIN_SALES_THRESHOLD) < 0;
            // 日均缺失时按 0 参与公式，行本身仍以 DATA_INSUFFICIENT 标注，绝不当作安全
            BigDecimal effectiveAvg = avgDailySales == null ? BigDecimal.ZERO : avgDailySales;

            // 季节性调整：近期速度 vs 长期速度，全部从内存映射计算
            BigDecimal seasonalFactor = calculateSeasonalFactor(productId, windowAvgByProduct, recentAvgByProduct);

            // 推荐最低库存 = 日均销量 × 安全天数 × 安全系数 × 季节系数
            // 向上取整：安全库存宁多勿少，且主单位通常是整数（瓶、袋、把）
            BigDecimal recommendedMin = effectiveAvg
                    .multiply(BigDecimal.valueOf(SAFETY_DAYS))
                    .multiply(SAFETY_FACTOR)
                    .multiply(seasonalFactor)
                    .setScale(0, RoundingMode.CEILING);

            // 推荐最高库存 = 日均销量 × 最高库存天数 × 季节系数
            BigDecimal recommendedMax = effectiveAvg
                    .multiply(BigDecimal.valueOf(MAX_STOCK_DAYS))
                    .multiply(seasonalFactor)
                    .setScale(0, RoundingMode.CEILING);

            // 推荐补货量 = 日均销量 × 补货周期 × 季节系数
            BigDecimal recommendedReorder = effectiveAvg
                    .multiply(BigDecimal.valueOf(REORDER_CYCLE_DAYS))
                    .multiply(seasonalFactor)
                    .setScale(0, RoundingMode.CEILING);

            // 最低库存不能大于最高库存
            if (recommendedMin.compareTo(recommendedMax) >= 0) {
                recommendedMax = recommendedMin.multiply(BigDecimal.valueOf(3)).setScale(0, RoundingMode.CEILING);
            }

            ThresholdRecommendation rec = new ThresholdRecommendation();
            rec.setProductId(productId);
            rec.setProductName(row.productName());
            rec.setProductCode(row.productCode());
            rec.setMainUnit(row.mainUnit());
            rec.setCurrentStock(currentStock);
            rec.setAvgDailySales(incompleteUnits?null:avgDailySales);
            rec.setTotalSold(row.totalSold());
            rec.setOrderCount(row.orderCount());
            rec.setRecommendedMinStock(insufficient?null:recommendedMin);
            rec.setRecommendedMaxStock(insufficient?null:recommendedMax);
            rec.setRecommendedReorderQuantity(insufficient?null:recommendedReorder);
            rec.setCurrentMinStock(row.minStock());
            rec.setCurrentMaxStock(row.maxStock());
            rec.setCurrentReorderQuantity(row.reorderQuantity());
            rec.setObservationDays(days);
            rec.setConfidence(insufficient ? CONFIDENCE_INSUFFICIENT : CONFIDENCE_SUFFICIENT);

            if (insufficient) {
                // 样本不足：照常返回真实库存与窗口/订单数，但明确标注不可据此下结论
                rec.setStatus("DATA_INSUFFICIENT");
            } else if (currentStock.compareTo(recommendedMin) < 0) {
                rec.setStatus("LOW");
            } else if (currentStock.compareTo(recommendedMax) > 0) {
                rec.setStatus("OVER");
            } else {
                // 仅表示落在推荐区间内，绝不表示“安全”
                rec.setStatus("NORMAL");
            }

            recommendations.add(rec);
        }

        // 按状态排序：低库存优先，然后按日均销量降序（样本不足最后）
        recommendations.sort((a, b) -> {
            int statusOrder = statusOrder(a.getStatus()) - statusOrder(b.getStatus());
            if (statusOrder != 0) return statusOrder;
            return nullSafeSales(b).compareTo(nullSafeSales(a));
        });

        return recommendations;
    }

    /**
     * 计算季节性调整系数
     * 近7天日均销量 / 长期日均销量，反映当前是否处于旺季（全部走内存映射，不重复查库）
     */
    private BigDecimal calculateSeasonalFactor(Long productId,
                                               Map<Long, BigDecimal> windowAvgByProduct,
                                               Map<Long, BigDecimal> recentAvgByProduct) {
        BigDecimal recentAvg = recentAvgByProduct.get(productId);
        BigDecimal longAvg = windowAvgByProduct.get(productId);
        if (recentAvg == null || longAvg == null || longAvg.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal factor = recentAvg.divide(longAvg, 2, RoundingMode.HALF_UP);
        // 限制系数范围 [0.5, 3.0]，避免极端波动
        if (factor.compareTo(SEASONAL_FACTOR_MIN) < 0) return SEASONAL_FACTOR_MIN;
        if (factor.compareTo(SEASONAL_FACTOR_MAX) > 0) return SEASONAL_FACTOR_MAX;
        return factor;
    }

    /** 把销售速度行按商品索引成 商品ID → 日均销量 的内存映射。 */
    private Map<Long, BigDecimal> indexAvgDailySales(List<SalesVelocity> rows) {
        Map<Long, BigDecimal> indexed = new HashMap<>();
        if (rows == null) return indexed;
        for (SalesVelocity row : rows) {
            if (row == null) continue;
            Long productId = row.productId();
            BigDecimal avgDailySales = row.avgDailySales();
            if (productId != null && avgDailySales != null) {
                indexed.put(productId, avgDailySales);
            }
        }
        return indexed;
    }

    private static BigDecimal nullSafeSales(ThresholdRecommendation rec) {
        return rec.getAvgDailySales() == null ? BigDecimal.ZERO : rec.getAvgDailySales();
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "LOW" -> 0;
            case "OVER" -> 1;
            case "NORMAL" -> 2;
            case "DATA_INSUFFICIENT" -> 3;
            default -> 4;
        };
    }

}
