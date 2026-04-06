package com.agri_system.domain.credit.repository;

import com.agri_system.domain.credit.model.CreditRepaymentLog;

import java.time.LocalDate;
import java.util.List;

public interface CreditRepaymentLogRepository {

    CreditRepaymentLog save(CreditRepaymentLog log);

    List<CreditRepaymentLog> listByCustomer(Long customerId);

    List<CreditRepaymentLog> listByCustomer(Long customerId, LocalDate startDate, LocalDate endDate);
}






