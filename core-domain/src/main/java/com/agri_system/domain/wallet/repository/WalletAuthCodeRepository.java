package com.agri_system.domain.wallet.repository;

import com.agri_system.domain.wallet.model.WalletAuthCode;

import java.util.Optional;

/**
 * 钱包验证码仓储接口
 *
 * @author AI Generated
 */
public interface WalletAuthCodeRepository {

    /**
     * 保存验证码
     */
    WalletAuthCode save(WalletAuthCode authCode);

    /**
     * 根据客户ID和验证码查询（用于验证）
     *
     * @param customerId 客户ID
     * @param authCode 验证码
     * @return 验证码记录
     */
    Optional<WalletAuthCode> findByCustomerIdAndAuthCode(Long customerId, String authCode);

    /**
     * 根据验证码字符串消费验证码
     *
     * @param authCode 验证码字符串
     * @param orderId 订单ID
     * @return 更新影响的行数
     */
    int consumeAuthCode(String authCode, Long orderId);
}







