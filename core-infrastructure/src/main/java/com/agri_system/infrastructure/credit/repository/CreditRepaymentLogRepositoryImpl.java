package com.agri_system.infrastructure.credit.repository;

import com.agri_system.domain.credit.model.CreditRepaymentLog;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CreditRepaymentLogRepositoryImpl implements CreditRepaymentLogRepository {

    private final CreditRepaymentLogMapper mapper;

    @Override
    public CreditRepaymentLog save(CreditRepaymentLog log) {
        if (log.getId() == null) {
            if (log.getRefundedAmount() == null && log.getCreditAccountId() != null) log.setRefundedAmount(java.math.BigDecimal.ZERO);
            mapper.insert(log);
        } else {
            mapper.update(log);
        }
        return log;
    }

    @Override
    public List<CreditRepaymentLog> listByCustomer(Long customerId) {
        return mapper.selectListByQuery(
                QueryWrapper.create()
                        .where("customer_id = ?", customerId)
                        .orderBy("created_at", false)
        );
    }

    @Override
    public List<CreditRepaymentLog> listByCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper qw = QueryWrapper.create()
                .where("customer_id = ?", customerId);
        if (startDate != null) {
            qw.and("DATE(created_at) >= ?", startDate);
        }
        if (endDate != null) {
            qw.and("DATE(created_at) <= ?", endDate);
        }
        qw.orderBy("created_at", false);
        return mapper.selectListByQuery(qw);
    }
    @Override public List<CreditRepaymentLog> listByCustomer(Long customerId, Long storeId, LocalDate start, LocalDate end) {
        QueryWrapper query = QueryWrapper.create().where("customer_id = ?", customerId);
        if (storeId != null) query.and("store_id = ?", storeId);
        if (start != null) query.and("created_at >= ?", start.atStartOfDay());
        if (end != null) query.and("created_at < ?", end.plusDays(1).atStartOfDay());
        return mapper.selectListByQuery(query.orderBy("created_at", false).orderBy("id", false));
    }
    @Override public List<CreditRepaymentLog> findByRequestKey(Long storeId, String requestKey) {
        return mapper.selectListByQuery(QueryWrapper.create().where("store_id = ?", storeId).and("request_key = ?", requestKey)
                .orderBy("id", true).forUpdate());
    }
    @Override public List<CreditRepaymentLog> findByRepaymentNoForUpdate(Long storeId, String repaymentNo) {
        return mapper.selectListByQuery(QueryWrapper.create().where("store_id = ?", storeId).and("repayment_no = ?", repaymentNo).orderBy("id", true).forUpdate());
    }
    @Override public List<CreditRepaymentLog> findByCreditAccountIdForUpdate(Long accountId) {
        return mapper.selectListByQuery(QueryWrapper.create().where("credit_account_id = ?", accountId)
                .orderBy("created_at", false).orderBy("id", false).forUpdate());
    }
}

