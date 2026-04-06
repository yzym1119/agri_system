package com.agri_system.infrastructure.credit.repository;

import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CreditAccountRepositoryImpl implements CreditAccountRepository {

    private final CreditAccountMapper mapper;

    @Override
    public CreditAccount save(CreditAccount account) {
        if (account.getId() == null) {
            mapper.insert(account);
        } else {
            mapper.update(account);
        }
        return account;
    }

    @Override
    public Optional<CreditAccount> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public List<CreditAccount> findOutstandingAccounts(Long customerId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .and("status = ?", 1)
                .and("remaining_amount > 0")
                .orderBy("created_at", true);
        return mapper.selectListByQuery(qw);
    }

    @Override
    public List<CreditAccount> listAccounts(Long customerId, Integer status) {
        QueryWrapper qw = QueryWrapper.create();
        if (customerId != null) {
            qw.where("customer_id = ?", customerId);
        }
        if (status != null) {
            qw.and("status = ?", status);
        }
        qw.orderBy("created_at", false);
        return mapper.selectListByQuery(qw);
    }

    @Override
    public com.mybatisflex.core.paginate.Page<CreditAccount> findPage(Long customerId, Integer status, int pageNum, int pageSize) {
        QueryWrapper qw = QueryWrapper.create().from("credit_account");
        if (customerId != null) {
            qw.where("customer_id = ?", customerId);
        }
        if (status != null) {
            qw.and("status = ?", status);
        }
        qw.orderBy("created_at", false);
        return mapper.paginate(pageNum, pageSize, qw);
    }

    @Override
    public List<CreditAccount> listAccounts(Long customerId, Integer status, LocalDate startDate, LocalDate endDate) {
        QueryWrapper qw = QueryWrapper.create();
        if (customerId != null) {
            qw.where("customer_id = ?", customerId);
        }
        if (status != null) {
            qw.and("status = ?", status);
        }
        if (startDate != null) {
            qw.and("DATE(created_at) >= ?", startDate);
        }
        if (endDate != null) {
            qw.and("DATE(created_at) <= ?", endDate);
        }
        qw.orderBy("created_at", true);
        return mapper.selectListByQuery(qw);
    }

    @Override
    public BigDecimal sumRemainingAmount(Long customerId) {
        QueryWrapper qw = QueryWrapper.create()
                .select("COALESCE(SUM(remaining_amount),0) as total")
                .where("customer_id = ?", customerId);
        Object result = mapper.selectObjectByQuery(qw);
        if (result == null) {
            return BigDecimal.ZERO;
        }
        if (result instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(result.toString());
    }

    @Override
    public LocalDateTime findLatestDebtDate(Long customerId) {
        QueryWrapper qw = QueryWrapper.create()
                .select("MAX(created_at) as latest")
                .where("customer_id = ?", customerId);
        Object result = mapper.selectObjectByQuery(qw);
        if (result == null) {
            return null;
        }
        if (result instanceof LocalDateTime) {
            return (LocalDateTime) result;
        }
        if (result instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(result.toString());
    }

    @Override
    public Optional<CreditAccount> findByOrderId(Long orderId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("order_id = ?", orderId);
        return Optional.ofNullable(mapper.selectOneByQuery(qw));
    }
}

