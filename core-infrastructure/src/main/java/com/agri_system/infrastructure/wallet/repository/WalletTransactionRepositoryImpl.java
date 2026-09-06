package com.agri_system.infrastructure.wallet.repository;

import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import com.agri_system.infrastructure.wallet.mapper.WalletTransactionMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 钱包交易记录仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepository {
    
    private final WalletTransactionMapper mapper;
    
    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        if (transaction.getId() == null) {
            mapper.insert(transaction);
        } else {
            mapper.update(transaction);
        }
        return transaction;
    }
    
    @Override
    public Page<WalletTransaction> findPage(Integer pageNum, Integer pageSize,
                                           Long customerId, Integer walletType, Integer transactionType) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId);
        
        if (walletType != null) {
            query.and("wallet_type = ?", walletType);
        }
        if (transactionType != null) {
            query.and("transaction_type = ?", transactionType);
        }
        
        query.orderBy("created_at DESC");
        
        Page<WalletTransaction> page = new Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }

    @Override
    public List<WalletTransaction> findByReference(Long customerId, Integer walletType,
                                                   String referenceType, Long referenceId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .and("wallet_type = ?", walletType)
                .and("reference_type = ?", referenceType)
                .and("reference_id = ?", referenceId)
                .orderBy("created_at ASC");
        return mapper.selectListByQuery(query);
    }

    @Override
    public List<Long> findCustomerIdsByReference(Integer walletType, String referenceType, Long referenceId) {
        QueryWrapper query = QueryWrapper.create()
                .select("DISTINCT customer_id")
                .where("wallet_type = ?", walletType)
                .and("reference_type = ?", referenceType)
                .and("reference_id = ?", referenceId);
        return mapper.selectListByQueryAs(query, Long.class);
    }
}








