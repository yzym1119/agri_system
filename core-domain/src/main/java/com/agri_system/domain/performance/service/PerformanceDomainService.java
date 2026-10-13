package com.agri_system.domain.performance.service;

import com.agri_system.domain.performance.model.CommissionRule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 绩效领域服务：封装提成计算等核心规则
 */
public class PerformanceDomainService {

    private static final BigDecimal DEFAULT_MAX_RATIO = new BigDecimal("0.20");

    public CommissionResult calculateSalesCommission(BigDecimal saleAmount,
                                                     BigDecimal costAmount,
                                                     CommissionRule rule,
                                                     boolean allowSalesPercent,
                                                     BigDecimal overrideRatio) {
        // 旧调用没有数量证据，只能保守视为一行；不能用售价/成本推测卖出数量。
        return calculateSalesCommission(saleAmount, costAmount, BigDecimal.ONE, rule, allowSalesPercent, overrideRatio);
    }

    public CommissionResult calculateSalesCommission(BigDecimal saleAmount,
                                                     BigDecimal costAmount,
                                                     BigDecimal quantity,
                                                     CommissionRule rule,
                                                     boolean allowSalesPercent,
                                                     BigDecimal overrideRatio) {
        BigDecimal profit = saleAmount.subtract(costAmount);
        if (profit.compareTo(BigDecimal.ZERO) <= 0) {
            return CommissionResult.zero("毛利为负，不计提成");
        }

        BigDecimal commission = BigDecimal.ZERO;
        StringBuilder desc = new StringBuilder();

        switch (rule.getCalcMode()) {
            case "FIXED" -> {
                if (quantity == null || quantity.signum() <= 0) {
                    return CommissionResult.zero("缺少真实销售数量，不计固定提成");
                }
                commission = rule.getCalcValue().multiply(quantity);
                desc.append("固定金额 * 数量");
            }
            case "PROFIT_PERCENT" -> {
                commission = profit.multiply(rule.getCalcValue())
                        .setScale(2, RoundingMode.HALF_UP);
                desc.append("毛利 * 比例");
            }
            case "SALES_PERCENT" -> {
                if (!allowSalesPercent) {
                    return CommissionResult.zero("商品未开启销售额提成");
                }
                commission = saleAmount.multiply(rule.getCalcValue())
                        .setScale(2, RoundingMode.HALF_UP);
                desc.append("销售额 * 比例");
            }
            default -> {
                return CommissionResult.zero("未知计算模式");
            }
        }

        BigDecimal maxRatio = overrideRatio != null ? overrideRatio : DEFAULT_MAX_RATIO;
        BigDecimal ratioLimit = profit.multiply(maxRatio).setScale(2, RoundingMode.HALF_UP);
        boolean needReview = false;

        if (rule.getMaxPerItem() != null && commission.compareTo(rule.getMaxPerItem()) > 0) {
            commission = rule.getMaxPerItem();
            desc.append("，命中单行封顶");
        }

        if (commission.compareTo(ratioLimit) > 0) {
            commission = ratioLimit;
            needReview = true;
            desc.append("，命中毛利比例上限");
        }

        if (rule.getMaxPerOrder() != null && commission.compareTo(rule.getMaxPerOrder()) > 0) {
            commission = rule.getMaxPerOrder();
            desc.append("，命中订单封顶");
        }

        return new CommissionResult(commission.setScale(2, RoundingMode.HALF_UP), desc.toString(), needReview);
    }

    public record CommissionResult(BigDecimal amount, String desc, boolean needReview) {
        public static CommissionResult zero(String desc) {
            return new CommissionResult(BigDecimal.ZERO, desc, false);
        }
    }
}

