package com.agri_system.infrastructure.wallet.mapper;

import com.agri_system.domain.wallet.model.WalletAuthCode;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 钱包验证码Mapper接口
 *
 * @author AI Generated
 */
@Mapper
public interface WalletAuthCodeMapper extends BaseMapper<WalletAuthCode> {

    /**
     * 根据客户ID和验证码查询
     */
    @Update("""
            UPDATE wallet_auth_code
               SET status = 2,
                   used_at = NOW(),
                   order_id = #{orderId}
             WHERE customer_id = #{customerId}
               AND auth_code = #{authCode}
               AND amount = #{amount}
               AND status = 1
               AND expire_at > NOW()
            """)
    int consumeAuthCode(@Param("customerId") Long customerId,
                        @Param("authCode") String authCode,
                        @Param("amount") BigDecimal amount,
                        @Param("orderId") Long orderId);
}






