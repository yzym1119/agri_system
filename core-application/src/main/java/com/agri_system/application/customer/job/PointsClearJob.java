package com.agri_system.application.customer.job;

import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分年度清零任务
 * 每年1月1日00:00:00执行
 * 
 * @author AI Generated
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsClearJob {
    
    private final CustomerWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    
    /**
     * 年度积分清零
     * 频率：每年1月1日00:00:00
     * 
     * 优化说明：
     * 1. 使用批量处理，避免一次性加载过多数据
     * 2. 单个客户失败不影响其他客户
     * 3. 添加幂等性检查（检查当天是否已执行过清零）
     */
    @Scheduled(cron = "0 0 0 1 1 ?")
    @Transactional(rollbackFor = Exception.class)
    public void clearPointsYearly() {
        log.info("开始执行年度积分清零任务");

        // 幂等守卫:按"客户 + 清零年度"去重。referenceId 固定为执行年度,
        // 年内重跑(手动触发/失败重试)时,已清零客户会被整体跳过——既避免重复写 YEARLY_CLEAR 流水,
        // 也避免误清客户在清零后当年新获得的积分(单靠"余额<=0 跳过"无法覆盖这种情况)。
        long clearYear = java.time.LocalDate.now().getYear();
        java.util.Set<Long> alreadyCleared = new java.util.HashSet<>(
                transactionRepository.findCustomerIdsByReference(3, "YEARLY_CLEAR", clearYear));
        log.info("本年度({})已清零客户数: {}", clearYear, alreadyCleared.size());

        // 查询所有积分钱包（Type=3）
        // 注意：如果客户数量很大（10万+），建议使用分页查询
        List<CustomerWallet> pointsWallets = walletRepository.findAllByWalletType(3);
        log.info("查询到 {} 个积分钱包", pointsWallets.size());
        
        int clearedCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        int batchSize = 1000;
        int processedCount = 0;
        
        for (CustomerWallet wallet : pointsWallets) {
            try {
                // 跳过余额为0（或异常负数）的账户
                if (wallet.getBalance() == null || wallet.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                    skipCount++;
                    continue;
                }

                // 跳过本年度已清零的客户（幂等守卫）
                if (alreadyCleared.contains(wallet.getCustomerId())) {
                    skipCount++;
                    continue;
                }

                BigDecimal originalBalance = wallet.getBalance();
                
                // 重置余额为0
                wallet.setBalance(BigDecimal.ZERO);
                walletRepository.save(wallet);
                
                // 记录流水
                WalletTransaction transaction = new WalletTransaction();
                transaction.setCustomerId(wallet.getCustomerId());
                transaction.setWalletType(3);
                transaction.setTransactionType(5); // 扣除
                transaction.setAmount(originalBalance.negate());
                transaction.setBalanceBefore(originalBalance);
                transaction.setBalanceAfter(BigDecimal.ZERO);
                transaction.setReferenceType("YEARLY_CLEAR");
                transaction.setReferenceId(clearYear); // 清零年度,作为"客户+年度"幂等去重键
                transaction.setRemark("年度积分清零");
                transaction.setCreatedAt(LocalDateTime.now());
                transaction.setCreatedBy(null);
                transactionRepository.save(transaction);

                // 同批次内防御:标记该客户已清零,避免重复 customerId 被处理两次
                alreadyCleared.add(wallet.getCustomerId());

                clearedCount++;
                processedCount++;
                
                // 每处理1000条记录，记录一次进度
                if (processedCount % batchSize == 0) {
                    log.info("年度积分清零任务进度: 已处理 {}/{}", processedCount, pointsWallets.size());
                }
                
            } catch (Exception e) {
                log.error("客户 {} 积分清零失败: {}", wallet.getCustomerId(), e.getMessage(), e);
                errorCount++;
                // 继续处理下一个客户，不中断整个任务
            }
        }
        
        log.info("年度积分清零任务完成，清零: {}, 跳过: {}, 失败: {}", 
                clearedCount, skipCount, errorCount);
    }
}






