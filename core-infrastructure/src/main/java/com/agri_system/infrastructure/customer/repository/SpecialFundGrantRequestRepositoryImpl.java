package com.agri_system.infrastructure.customer.repository;

import com.agri_system.domain.customer.repository.SpecialFundGrantRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/** 只保存专用金发放请求身份与结果；JdbcTemplate使用项目既有同Flex事务连接。 */
@Repository
@RequiredArgsConstructor
public class SpecialFundGrantRequestRepositoryImpl implements SpecialFundGrantRequestRepository {
    private final JdbcTemplate jdbc;

    @Override
    public void reserve(String requestKey, Long customerId, BigDecimal amount) {
        jdbc.update("INSERT INTO special_fund_grant_request(request_key,customer_id,amount,status) VALUES(?,?,?,'PENDING') "
                + "ON DUPLICATE KEY UPDATE request_key=request_key", requestKey, customerId, amount);
    }

    @Override
    public Optional<GrantRequest> find(String requestKey, boolean lock) {
        return jdbc.query("SELECT request_key,customer_id,amount,balance_after,status FROM special_fund_grant_request "
                        + "WHERE request_key=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new GrantRequest(rs.getString(1), rs.getLong(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getString(5)), requestKey)
                .stream().findFirst();
    }

    @Override
    public void complete(String requestKey, BigDecimal balanceAfter) {
        if (jdbc.update("UPDATE special_fund_grant_request SET status='COMPLETED',balance_after=?,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE request_key=? AND status='PENDING'",
                balanceAfter, requestKey) != 1) {
            throw new IllegalStateException("专用金发放请求状态已变化");
        }
    }
}
