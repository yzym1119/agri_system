package com.agri_system.infrastructure.wallet.mapper;

import com.agri_system.domain.wallet.model.CustomerWallet;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 客户钱包Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface CustomerWalletMapper extends BaseMapper<CustomerWallet> {
    
    /**
     * 根据客户ID和钱包类型查询钱包（带悲观锁 FOR UPDATE）
     */
    @Select("SELECT * FROM customer_wallet WHERE customer_id = #{customerId} AND wallet_type = #{walletType} FOR UPDATE")
    CustomerWallet selectByCustomerIdAndWalletTypeWithLock(Long customerId, Integer walletType);
}








