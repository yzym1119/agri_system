package com.agri_system.infrastructure.wallet.repository;

import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.infrastructure.wallet.mapper.CustomerWalletMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 客户钱包仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CustomerWalletRepositoryImpl implements CustomerWalletRepository {
    
    private final CustomerWalletMapper mapper;
    
    @Override
    public CustomerWallet save(CustomerWallet wallet) {
        if (wallet.getId() == null) {
            mapper.insert(wallet);
        } else {
            mapper.update(wallet);
        }
        return wallet;
    }
    
    @Override
    public Optional<CustomerWallet> findByCustomerIdAndWalletType(Long customerId, Integer walletType) {
        CustomerWallet wallet = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("customer_id = ?", customerId)
                        .and("wallet_type = ?", walletType)
        );
        return Optional.ofNullable(wallet);
    }
    
    @Override
    public Optional<CustomerWallet> findByCustomerIdAndWalletTypeWithLock(Long customerId, Integer walletType) {
        CustomerWallet wallet = mapper.selectByCustomerIdAndWalletTypeWithLock(customerId, walletType);
        return Optional.ofNullable(wallet);
    }
    
    @Override
    public java.util.List<CustomerWallet> findAllByWalletType(Integer walletType) {
        return mapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("wallet_type = ?", walletType)
        );
    }
}

