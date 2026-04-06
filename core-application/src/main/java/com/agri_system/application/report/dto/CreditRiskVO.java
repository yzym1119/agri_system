package com.agri_system.application.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务风控VO
 */
@Data
public class CreditRiskVO {

    /** 总欠款 */
    private BigDecimal totalDebt;

    /** 逾期欠款 */
    private BigDecimal overdueDebt;

    /** 回款率(%) */
    private BigDecimal repaymentRate;

    /** 本月新增赊销额 */
    private BigDecimal monthlyNewCredit;

    /** 本月实收还款额 */
    private BigDecimal monthlyRepayment;
}




