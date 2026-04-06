package com.agri_system.domain.wallet.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包验证码实体
 *
 * @author AI Generated
 */
@Data
@Table("wallet_auth_code")
public class WalletAuthCode {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long customerId;

    private Long orderId;

    private String authCode;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime expireAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;
}







