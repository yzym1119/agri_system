package com.agri_system.application.product.service;

import java.math.BigDecimal;

/** 保留原始单位数量，同时提供直接换算后的库存基准数量。 */
public record UnitConversionSnapshot(
        String unitName,
        BigDecimal originalQuantity,
        BigDecimal baseQuantityPerUnit,
        BigDecimal baseQuantity) {
}
