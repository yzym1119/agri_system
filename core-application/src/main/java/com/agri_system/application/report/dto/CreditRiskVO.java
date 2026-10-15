package com.agri_system.application.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务风控VO
 */
@Data
public class CreditRiskVO {

    /** 所选门店未结清应收余额，包含正常及逾期账户。 */
    private BigDecimal totalDebt;

    /** 所选门店逾期且余额大于0的应收；明确逾期状态保留历史到期日未知的事实。 */
    private BigDecimal overdueDebt;

    /** 回款率(%) */
    private BigDecimal repaymentRate;

    /** 本月新增赊销额 */
    private BigDecimal monthlyNewCredit;

    /** 本月实收还款额 */
    private BigDecimal monthlyRepayment;
}




