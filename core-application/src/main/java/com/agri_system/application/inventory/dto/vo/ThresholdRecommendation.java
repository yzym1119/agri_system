package com.agri_system.application.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能库存阈值推荐VO
 */
@Data
public class ThresholdRecommendation {

    private Long productId;
    private String productName;
    private String productCode;
    private String mainUnit;

    /** 当前库存 */
    private BigDecimal currentStock;

    /** 日均销量（近N天平均） */
    private BigDecimal avgDailySales;

    /** 总销量（统计周期内） */
    private BigDecimal totalSold;

    /** 订单数（统计周期内） */
    private Integer orderCount;

    /** 推荐最低库存 */
    private BigDecimal recommendedMinStock;

    /** 推荐最高库存 */
    private BigDecimal recommendedMaxStock;

    /** 推荐补货量 */
    private BigDecimal recommendedReorderQuantity;

    /** 当前手动设置的最低库存 */
    private BigDecimal currentMinStock;

    /** 当前手动设置的最高库存 */
    private BigDecimal currentMaxStock;

    /** 当前手动设置的补货量 */
    private BigDecimal currentReorderQuantity;

    /** 状态：LOW=低库存, OVER=积压, NORMAL=正常 */
    private String status;
}
