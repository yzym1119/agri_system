package com.agri_system.infrastructure.credit.query;

import com.agri_system.domain.credit.query.CreditReportQuery;
import com.agri_system.domain.credit.query.CreditRiskFacts;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 复用现有Flex Mapper与外层只读事务，不接入第二数据源或修改原账户/还款事实。 */
@Repository
@RequiredArgsConstructor
public class CreditReportQueryImpl implements CreditReportQuery {
    private final CreditAccountMapper creditAccountMapper;
    private final CreditRepaymentLogMapper repaymentLogMapper;

    @Override public BigDecimal newCreditAmount(Long storeId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper query = QueryWrapper.create().where("created_at between ? and ?", start, end);
        if (storeId != null) query.and("store_id = ?", storeId);
        return creditAccountMapper.selectOneByQueryAs(query.select("COALESCE(SUM(credit_amount),0) AS total"), BigDecimal.class);
    }

    @Override public CreditRiskFacts riskFacts(Long storeId, LocalDate asOf) {
        // 总欠款
        QueryWrapper debtQ = QueryWrapper.create()
                .where("status in (1,3)").and("remaining_amount > 0");
        if (storeId != null) debtQ.and("store_id = ?", storeId);
        BigDecimal totalDebt = creditAccountMapper.selectOneByQueryAs(
                debtQ.clone().select("COALESCE(SUM(remaining_amount),0) AS total"), BigDecimal.class);
        
        // 已明确逾期的历史账户即使到期日未知，也保留其逾期事实。
        BigDecimal overdueDebt = creditAccountMapper.selectOneByQueryAs(
                debtQ.clone()
                        .select("COALESCE(SUM(remaining_amount),0) AS total")
                        .and("(status = 3 OR due_date < ?)", asOf),
                BigDecimal.class);
        
        // 本月新增赊销额（credit_account.created_at在本月）
        LocalDate monthStart = asOf.withDayOfMonth(1);
        LocalDate monthEnd = asOf.withDayOfMonth(asOf.lengthOfMonth());
        LocalDateTime monthStartTime = monthStart.atStartOfDay();
        LocalDateTime monthEndTime = monthEnd.atTime(LocalTime.MAX);
        
        QueryWrapper monthlyCreditQ = QueryWrapper.create()
                .where("created_at between ? and ?", monthStartTime, monthEndTime);
        if (storeId != null) monthlyCreditQ.and("store_id = ?", storeId);
        BigDecimal monthlyNewCredit = newCreditAmount(storeId, monthStartTime, monthEndTime);

        // 本月实收还款额（credit_repayment_log.created_at在本月）
        QueryWrapper repaymentQ = QueryWrapper.create()
                .select("COALESCE(SUM(amount),0) AS total")
                .where("created_at between ? and ?", monthStartTime, monthEndTime);
        if (storeId != null) {
            repaymentQ.and("store_id = ?", storeId);
        }
        BigDecimal monthlyRepayment = repaymentLogMapper.selectOneByQueryAs(
                repaymentQ, BigDecimal.class);
        
        // 同一批本月新建应收：净实还 / (净实还 + 剩余欠款)。
        // 本月偿还旧债仍计入上面的本月还款额，但不能混入本月赊销的回款率。
        // paid_amount 保留原实还事实，必须减去明确归属该账户的已退还款；冲欠不算现金。
        String sources = " FROM credit_repayment_log rp WHERE rp.credit_account_id = credit_account.id"
                + " AND rp.store_id = credit_account.store_id";
        String refunded = "COALESCE((SELECT SUM(rp.refunded_amount)" + sources + "),0)";
        String sourceAmount = "COALESCE((SELECT SUM(rp.amount)" + sources + "),0)";
        BigDecimal unknown = creditAccountMapper.selectOneByQueryAs(monthlyCreditQ.clone().select(
                "COALESCE(SUM(CASE WHEN paid_amount IS NULL OR remaining_amount IS NULL"
                        + " OR paid_amount < 0 OR remaining_amount < 0 OR paid_amount <> " + sourceAmount
                        + " OR EXISTS (SELECT 1" + sources + " AND (rp.refunded_amount IS NULL"
                        + " OR rp.refunded_amount < 0 OR rp.refunded_amount > rp.amount))"
                        + " THEN 1 ELSE 0 END),0) AS total"), BigDecimal.class);
        BigDecimal netPaid = creditAccountMapper.selectOneByQueryAs(monthlyCreditQ.clone().select(
                "COALESCE(SUM(paid_amount - " + refunded + "),0) AS total"), BigDecimal.class);
        BigDecimal cohortRemaining = creditAccountMapper.selectOneByQueryAs(monthlyCreditQ.clone().select(
                "COALESCE(SUM(remaining_amount),0) AS total"), BigDecimal.class);
        return new CreditRiskFacts(totalDebt, overdueDebt, monthlyNewCredit, monthlyRepayment,
                unknown, netPaid, cohortRemaining);
    }
}
