package com.agri_system.infrastructure.wallet.repository;

import com.agri_system.domain.wallet.model.WalletAuthCode;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.infrastructure.wallet.mapper.WalletAuthCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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






