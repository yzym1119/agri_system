package com.agri_system.infrastructure.wallet.repository;

import com.agri_system.domain.wallet.model.WalletAuthCode;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.infrastructure.wallet.mapper.WalletAuthCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 钱包验证码仓储实现
 *
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class WalletAuthCodeRepositoryImpl implements WalletAuthCodeRepository {

    private final WalletAuthCodeMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public WalletAuthCode save(WalletAuthCode authCode) {
        if (authCode.getId() == null) {
            mapper.insert(authCode);
        } else {
            mapper.update(authCode);
        }
        return authCode;
    }

    @Override
    public void insertIssuedCode(Long customerId, Long orderId, String authCode, BigDecimal amount,
                                 LocalDateTime expireAt, LocalDateTime createdAt) {
        String sql = "INSERT INTO wallet_auth_code(customer_id, order_id, auth_code, amount, status, expire_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, customerId, orderId, authCode, amount, 1, java.sql.Timestamp.valueOf(expireAt), java.sql.Timestamp.valueOf(createdAt));
    }

    @Override
    public int associateConsumedCode(String authCode, Long orderId) {
        String sql = "UPDATE wallet_auth_code SET order_id = ? WHERE auth_code = ? AND status = 2 AND (order_id IS NULL OR order_id = '')";
        return jdbcTemplate.update(sql, orderId, authCode);
    }

    @Override
    public Optional<WalletAuthCode> findByCustomerIdAndAuthCode(Long customerId, String authCode) {
        WalletAuthCode authCodeRecord = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("customer_id = ?", customerId)
                        .and("auth_code = ?", authCode)
        );
        return Optional.ofNullable(authCodeRecord);
    }

    @Override
    public int consumeAuthCode(Long customerId, String authCode, BigDecimal amount, Long orderId) {
        return mapper.consumeAuthCode(customerId, authCode, amount, orderId);
    }
}






