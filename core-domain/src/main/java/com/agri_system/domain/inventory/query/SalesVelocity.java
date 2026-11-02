package com.agri_system.domain.inventory.query;
import java.math.BigDecimal;
/** 原观察窗口成交销量按持久销售单位换算为商品主单位；未改变原总销量状态口径。 */
public record SalesVelocity(Long productId,String productName,String productCode,String mainUnit,
                            BigDecimal avgDailySales,BigDecimal totalSold,Integer orderCount,
                            BigDecimal minStock,BigDecimal maxStock,BigDecimal reorderQuantity,int unresolvedUnitCount) { }
