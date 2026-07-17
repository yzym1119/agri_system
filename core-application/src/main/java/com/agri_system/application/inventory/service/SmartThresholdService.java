package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.vo.ThresholdRecommendation;
import com.agri_system.infrastructure.inventory.mapper.InventoryMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能库存阈值服务
 * 基于历史销售数据自动计算推荐的安全库存、最高库存和补货量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartThresholdService {

    private final OrderItemMapper orderItemMapper;
    private final InventoryMapper inventoryMapper;

    /** 安全库存覆盖天数（默认7天） */
    private static final int SAFETY_DAYS = 7;
    /** 安全系数（应对需求波动） */
    private static final BigDecimal SAFETY_FACTOR = new BigDecimal("1.5");
    /** 最高库存覆盖天数（默认30天，防止积压） */
    private static final int MAX_STOCK_DAYS = 30;
    /** 补货周期天数（默认14天） */
    private static final int REORDER_CYCLE_DAYS = 14;
    /** 最低销售天数阈值，低于此值认为数据不足 */
    private static final BigDecimal MIN_SALES_THRESHOLD = new BigDecimal("0.1");

    /**
     * 计算智能阈值推荐
     * @param storeId 门店ID（null=全部门店）
     * @param days 统计天数（默认90天）
     * @return 每个商品的推荐阈值列表
     */
    public List<ThresholdRecommendation> calculateRecommendations(Long storeId, int days) {
        if (days <= 0) days = 90;

        // 1. 查询销售速度
        List<Map<String, Object>> salesData = orderItemMapper.selectProductSalesVelocity(days, storeId);
        if (salesData == null || salesData.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 查询当前库存
        Map<Long, BigDecimal> currentStockMap = loadCurrentStock(storeId);

        // 3. 计算推荐值
        List<ThresholdRecommendation> recommendations = new ArrayList<>();
        for (Map<String, Object> row : salesData) {
            Long productId = toLong(row.get("productId"));
            if (productId == null) continue;

            BigDecimal avgDailySales = toBigDecimal(row.get("avgDailySales"));
            if (avgDailySales == null || avgDailySales.compareTo(MIN_SALES_THRESHOLD) < 0) continue;

            BigDecimal currentStock = currentStockMap.getOrDefault(productId, BigDecimal.ZERO);

            // 季节性调整：如果有足够数据，用近期速度 vs 长期速度做系数
            BigDecimal seasonalFactor = calculateSeasonalFactor(productId, storeId, days);

            // 推荐最低库存 = 日均销量 × 安全天数 × 安全系数 × 季节系数
            // 向上取整：安全库存宁多勿少，且主单位通常是整数（瓶、袋、把）
            BigDecimal recommendedMin = avgDailySales
                    .multiply(BigDecimal.valueOf(SAFETY_DAYS))
                    .multiply(SAFETY_FACTOR)
                    .multiply(seasonalFactor)
                    .setScale(0, RoundingMode.CEILING);

            // 推荐最高库存 = 日均销量 × 最高库存天数 × 季节系数
            BigDecimal recommendedMax = avgDailySales
                    .multiply(BigDecimal.valueOf(MAX_STOCK_DAYS))
                    .multiply(seasonalFactor)
                    .setScale(0, RoundingMode.CEILING);

            // 推荐补货量 = 日均销量 × 补货周期 × 季节系数
            BigDecimal recommendedReorder = avgDailySales
                    .multiply(BigDecimal.valueOf(REORDER_CYCLE_DAYS))
                    .multiply(seasonalFactor)
                    .setScale(0, RoundingMode.CEILING);

            // 最低库存不能大于最高库存
            if (recommendedMin.compareTo(recommendedMax) >= 0) {
                recommendedMax = recommendedMin.multiply(BigDecimal.valueOf(3)).setScale(0, RoundingMode.CEILING);
            }

            ThresholdRecommendation rec = new ThresholdRecommendation();
            rec.setProductId(productId);
            rec.setProductName(toString(row.get("productName")));
            rec.setProductCode(toString(row.get("productCode")));
            rec.setMainUnit(toString(row.get("mainUnit")));
            rec.setCurrentStock(currentStock);
            rec.setAvgDailySales(avgDailySales.setScale(2, RoundingMode.HALF_UP));
            rec.setTotalSold(toBigDecimal(row.get("totalSold")));
            rec.setOrderCount(toInteger(row.get("orderCount")));
            rec.setRecommendedMinStock(recommendedMin);
            rec.setRecommendedMaxStock(recommendedMax);
            rec.setRecommendedReorderQuantity(recommendedReorder);
            rec.setCurrentMinStock(toBigDecimal(row.get("currentMinStock")));
            rec.setCurrentMaxStock(toBigDecimal(row.get("currentMaxStock")));
            rec.setCurrentReorderQuantity(toBigDecimal(row.get("currentReorderQuantity")));

            // 判断状态
            if (currentStock.compareTo(recommendedMin) < 0) {
                rec.setStatus("LOW");
            } else if (currentStock.compareTo(recommendedMax) > 0) {
                rec.setStatus("OVER");
            } else {
                rec.setStatus("NORMAL");
            }

            recommendations.add(rec);
        }

        // 按状态排序：低库存优先，然后按日均销量降序
        recommendations.sort((a, b) -> {
            int statusOrder = statusOrder(a.getStatus()) - statusOrder(b.getStatus());
            if (statusOrder != 0) return statusOrder;
            return b.getAvgDailySales().compareTo(a.getAvgDailySales());
        });

        return recommendations;
    }

    /**
     * 计算季节性调整系数
     * 近7天日均销量 / 长期日均销量，反映当前是否处于旺季
     */
    private BigDecimal calculateSeasonalFactor(Long productId, Long storeId, int longDays) {
        try {
            List<Map<String, Object>> recentData = orderItemMapper.selectProductSalesVelocity(7, storeId);
            if (recentData == null || recentData.isEmpty()) return BigDecimal.ONE;

            for (Map<String, Object> row : recentData) {
                Long pid = toLong(row.get("productId"));
                if (pid != null && pid.equals(productId)) {
                    BigDecimal recentAvg = toBigDecimal(row.get("avgDailySales"));
                    List<Map<String, Object>> longData = orderItemMapper.selectProductSalesVelocity(longDays, storeId);
                    if (longData != null) {
                        for (Map<String, Object> lrow : longData) {
                            if (productId.equals(toLong(lrow.get("productId")))) {
                                BigDecimal longAvg = toBigDecimal(lrow.get("avgDailySales"));
                                if (longAvg != null && longAvg.compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal factor = recentAvg.divide(longAvg, 2, RoundingMode.HALF_UP);
                                    // 限制系数范围 [0.5, 3.0]，避免极端波动
                                    if (factor.compareTo(new BigDecimal("0.5")) < 0) factor = new BigDecimal("0.5");
                                    if (factor.compareTo(new BigDecimal("3.0")) > 0) factor = new BigDecimal("3.0");
                                    return factor;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("计算季节性系数失败，使用默认值1.0: {}", e.getMessage());
        }
        return BigDecimal.ONE;
    }

    private Map<Long, BigDecimal> loadCurrentStock(Long storeId) {
        try {
            // 使用 InventoryMapper 的现有方法
            // 如果没有全量查询，这里做一个简单处理
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("加载当前库存失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "LOW" -> 0;
            case "OVER" -> 1;
            default -> 2;
        };
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        try { return new BigDecimal(obj.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return null; }
    }

    private String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
