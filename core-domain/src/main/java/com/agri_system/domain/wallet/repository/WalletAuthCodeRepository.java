package com.agri_system.domain.wallet.repository;

import com.agri_system.domain.wallet.model.WalletAuthCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /** 发放授权码：仅保存本次明确给出的绑定、金额和有效期，不覆盖既有记录。 */
    void insertIssuedCode(Long customerId, Long orderId, String authCode, BigDecimal amount,
                          LocalDateTime expireAt, LocalDateTime createdAt);

    /** 旧兼容关联入口，只关联已核销且尚无订单的原记录，返回实际影响行数。 */
    int associateConsumedCode(String authCode, Long orderId);

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
     * @param customerId 客户ID
     * @param authCode 验证码字符串
     * @param amount 本次实际校验金额
     * @param orderId 订单ID
     * @return 更新影响的行数
     */
    int consumeAuthCode(Long customerId, String authCode, BigDecimal amount, Long orderId);

    /**
     * 统计客户自指定时间以来发放的验证码数量（发送频率限制）
     *
     * @param customerId 客户ID
     * @param since 起始时间（含）
     * @return 发放记录数
     */
    int countIssuedSince(Long customerId, LocalDateTime since);
}






