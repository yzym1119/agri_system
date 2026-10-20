package com.agri_system.domain.product.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/** 数量按实际持久化基准单位校验；只有明确的计量名称允许拆零。 */
public final class QuantityGranularityPolicy {
    private static final Set<String> MEASURED_UNITS = Set.of(
            "公斤", "千克", "kg", "克", "g", "斤", "两", "吨", "t", "毫克", "mg",
            "升", "l", "毫升", "ml", "立方米", "m³", "m3", "米", "m", "厘米", "cm",
            "毫米", "mm", "千米", "公里", "km", "平方米", "m²", "m2", "亩", "公顷",
            // V43 内置字典保存完整标签；只接受这些明确别名，不剥除任意括号猜测单位。
            "千克(kg)", "克(g)", "毫升(ml)", "升(l)");

    private QuantityGranularityPolicy() {}

    public static boolean allowsFractionalBaseQuantity(String baseUnitName) {
        return baseUnitName != null && MEASURED_UNITS.contains(baseUnitName.trim().toLowerCase(Locale.ROOT));
    }

    public static BigDecimal requireBaseQuantity(String baseUnitName, BigDecimal quantity) {
        if (baseUnitName == null || baseUnitName.isBlank()) throw new IllegalArgumentException("缺少实际基准单位，不能校验数量");
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("基准数量不能为负");
        if (!allowsFractionalBaseQuantity(baseUnitName) && quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("按基准单位“" + baseUnitName + "”换算后的数量必须为整数");
        }
        try { return quantity.setScale(8, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("基准数量超出8位小数精度，不能舍入", error); }
    }
}
