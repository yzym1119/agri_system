package com.agri_system.domain.wallet.repository;

import com.agri_system.domain.wallet.model.WalletTransaction;
import com.mybatisflex.core.paginate.Page;

import java.util.List;

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

    /**
     * 按关联查询钱包交易记录（用于按订单回查已发放/已回退积分等）
     *
     * @param customerId     客户ID
     * @param walletType     钱包类型
     * @param referenceType  关联类型（如 ORDER / ORDER_REFUND）
     * @param referenceId    关联ID（如订单ID）
     * @return 命中的交易记录（按时间升序）
     */
    List<WalletTransaction> findByReference(Long customerId, Integer walletType,
                                            String referenceType, Long referenceId);

    /**
     * 批量查询已存在指定关联记录的客户ID集合（用于幂等守卫，如年度积分清零去重）
     *
     * @param walletType    钱包类型
     * @param referenceType 关联类型（如 YEARLY_CLEAR）
     * @param referenceId   关联ID（如清零年度）
     * @return 命中的去重客户ID列表
     */
    List<Long> findCustomerIdsByReference(Integer walletType, String referenceType, Long referenceId);
}





























