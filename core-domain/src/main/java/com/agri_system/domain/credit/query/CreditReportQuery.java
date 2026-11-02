package com.agri_system.domain.credit.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 赊销报表只读入口；显式门店继续与实际租户范围相交，null不扩大调用方权限。 */
public interface CreditReportQuery {
    BigDecimal newCreditAmount(Long storeId, LocalDateTime start, LocalDateTime end);
    CreditRiskFacts riskFacts(Long storeId, LocalDate asOf);
}
