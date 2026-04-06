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
}

