package com.agri_system.infrastructure.wallet.mapper;

import com.agri_system.domain.wallet.model.WalletAuthCode;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

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
    @Update("UPDATE wallet_auth_code SET status = 2, used_at = NOW(), order_id = #{orderId} WHERE auth_code = #{authCode} AND status = 1")
    int consumeAuthCode(String authCode, Long orderId);
}







