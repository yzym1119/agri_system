package com.agri_system.domain.credit.query;

import java.math.BigDecimal;

/** 原账户与明确还款来源的只读聚合事实；回款率由应用层解释，未知来源不可猜补。 */
public record CreditRiskFacts(BigDecimal totalDebt, BigDecimal overdueDebt,
                              BigDecimal monthlyNewCredit, BigDecimal monthlyRepayment,
                              BigDecimal accountsWithUnknownSources, BigDecimal netMonthlyPaid,
                              BigDecimal monthlyRemainingCredit) { }
