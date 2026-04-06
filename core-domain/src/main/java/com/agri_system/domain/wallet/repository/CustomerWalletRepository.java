package com.agri_system.domain.wallet.repository;

import com.agri_system.domain.wallet.model.CustomerWallet;

import java.util.Optional;

/**
 * 客户钱包仓储接口
 * 
 * @author AI Generated
 */
public interface CustomerWalletRepository {
    
    /**
     * 保存钱包
     */
    CustomerWallet save(CustomerWallet wallet);
    
    /**
     * 根据客户ID和钱包类型查询钱包（带乐观锁）
     * 
     * @param customerId 客户ID
     * @param walletType 钱包类型：1-本金，2-专用金，3-积分
     * @return 钱包
     */
    Optional<CustomerWallet> findByCustomerIdAndWalletType(Long customerId, Integer walletType);
    
    /**
     * 根据客户ID和钱包类型查询钱包（带悲观锁 FOR UPDATE）
     * 
     * @param customerId 客户ID
     * @param walletType 钱包类型：1-本金，2-专用金，3-积分
     * @return 钱包
     */
    Optional<CustomerWallet> findByCustomerIdAndWalletTypeWithLock(Long customerId, Integer walletType);
    
    /**
     * 根据钱包类型查询所有钱包
     * 
     * @param walletType 钱包类型：1-本金，2-专用金，3-积分
     * @return 钱包列表
     */
    java.util.List<CustomerWallet> findAllByWalletType(Integer walletType);
}


