package com.agri_system.domain.credit.repository;

import com.agri_system.domain.credit.model.CreditRepaymentLog;

import java.time.LocalDate;
import java.util.List;

public interface CreditRepaymentLogRepository {

    CreditRepaymentLog save(CreditRepaymentLog log);

    List<CreditRepaymentLog> listByCustomer(Long customerId);

    List<CreditRepaymentLog> listByCustomer(Long customerId, LocalDate startDate, LocalDate endDate);
    List<CreditRepaymentLog> listByCustomer(Long customerId, Long storeId, LocalDate startDate, LocalDate endDate);
    List<CreditRepaymentLog> findByRequestKey(Long storeId, String requestKey);
    /** 持客户锁后读取已提交的完整批次，不能使用锁前的一致性快照。 */
    List<CreditRepaymentLog> findByRepaymentNoForUpdate(Long storeId, String repaymentNo);
    List<CreditRepaymentLog> findByCreditAccountIdForUpdate(Long accountId);
}






