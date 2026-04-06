package com.agri_system.domain.wallet.repository;

import com.agri_system.domain.wallet.model.WalletTransaction;
import com.mybatisflex.core.paginate.Page;

/**
 * 钱包交易记录仓储接口
 * 
 * @author AI Generated
 */
public interface WalletTransactionRepository {
    
    /**
     * 保存交易记录
     */
    WalletTransaction save(WalletTransaction transaction);
    
    /**
     * 分页查询钱包交易记录
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param customerId 客户ID（必填）
     * @param walletType 钱包类型（可选）
     * @param transactionType 交易类型（可选）
     * @return 分页结果
     */
    Page<WalletTransaction> findPage(Integer pageNum, Integer pageSize,
                                    Long customerId, Integer walletType, Integer transactionType);
}





























