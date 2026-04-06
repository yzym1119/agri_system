package com.agri_system.domain.credit.repository;

import com.agri_system.domain.credit.model.CreditAccount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CreditAccountRepository {

    CreditAccount save(CreditAccount account);

    Optional<CreditAccount> findById(Long id);

    List<CreditAccount> findOutstandingAccounts(Long customerId);

    List<CreditAccount> listAccounts(Long customerId, Integer status);

    List<CreditAccount> listAccounts(Long customerId, Integer status, LocalDate startDate, LocalDate endDate);
    
    /**
     * 分页查询欠款单据
     */
    com.mybatisflex.core.paginate.Page<CreditAccount> findPage(Long customerId, Integer status, int pageNum, int pageSize);

    BigDecimal sumRemainingAmount(Long customerId);

    LocalDateTime findLatestDebtDate(Long customerId);

    Optional<CreditAccount> findByOrderId(Long orderId);
}






