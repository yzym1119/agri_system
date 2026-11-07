package com.agri_system.application.wallet.service;

import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletAuthCode;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 钱包应用服务
 * 负责钱包业务流程编排
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class WalletApplicationService {
    private static final Logger logger = LoggerFactory.getLogger(WalletApplicationService.class);
    
    private final CustomerWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletAuthCodeRepository walletAuthCodeRepository;
    
    private static final int AUTH_CODE_LENGTH = 6;
    private static final java.security.SecureRandom AUTH_CODE_RANDOM = new java.security.SecureRandom();

    /** 手动发放专用金的单次上限（元）；防误操作与无限增发，可按经营需要调整。 */
    private static final BigDecimal MAX_SINGLE_SPECIAL_FUND_GRANT = new BigDecimal("5000.00");
    
    /**
     * 扣减本金余额
     * 
     * @param customerId 客户ID
     * @param amount 扣减金额
     * @param referenceType 关联类型
     * @param referenceId 关联ID
     * @param createdBy 操作人ID
     * @param remark 备注
     * @return 扣减后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductBalance(Long customerId, BigDecimal amount, 
                                   String referenceType, Long referenceId, Long createdBy, String remark) {
        // 使用悲观锁查询钱包
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 1)
                .orElseGet(() -> {
                    // 如果钱包不存在，创建新钱包
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(1);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    newWallet.setCreatedAt(java.time.LocalDateTime.now());
                    newWallet.setUpdatedAt(java.time.LocalDateTime.now());
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = wallet.getBalance();
        
        // 扣减余额
        wallet.deduct(amount);
        walletRepository.save(wallet);
        
        // 记录交易流水
        recordTransaction(customerId, 1, 2, amount.negate(), 
                         balanceBefore, wallet.getBalance(), 
                         referenceType, referenceId, createdBy, remark != null ? remark : "扣减本金余额");
        
        return wallet.getBalance();
    }
    
    /**
     * 扣减专用金余额
     * 
     * @param customerId 客户ID
     * @param amount 扣减金额
     * @param referenceType 关联类型
     * @param referenceId 关联ID
     * @param createdBy 操作人ID
     * @param remark 备注
     * @return 扣减后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductSpecialFund(Long customerId, BigDecimal amount, 
                                       String referenceType, Long referenceId, Long createdBy, String remark) {
        // 使用悲观锁查询钱包
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 2)
                .orElseGet(() -> {
                    // 如果钱包不存在，创建新钱包
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(2);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = wallet.getBalance();
        
        // 扣减余额
        wallet.deduct(amount);
        walletRepository.save(wallet);
        
        // 记录交易流水
        recordTransaction(customerId, 2, 2, amount.negate(), 
                         balanceBefore, wallet.getBalance(), 
                         referenceType, referenceId, createdBy, remark != null ? remark : "扣减专用金余额");
        
        return wallet.getBalance();
    }

    /**
     * 扣减积分
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductPoints(Long customerId, BigDecimal points,
                                   String referenceType, Long referenceId, Long createdBy, String remark) {
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 3)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(3);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        ensurePointsNotFrozen(wallet);
        BigDecimal balanceBefore = wallet.getBalance();
        wallet.deduct(points);
        walletRepository.save(wallet);
        recordTransaction(customerId, 3, 2, points.negate(),
                balanceBefore, wallet.getBalance(),
                referenceType, referenceId, createdBy, remark != null ? remark : "积分扣减");
        return wallet.getBalance();
    }
    
    /**
     * 退回本金余额（退款）
     * 
     * @param customerId 客户ID
     * @param amount 退回金额
     * @param referenceType 关联类型
     * @param referenceId 关联ID
     * @param createdBy 操作人ID
     * @return 退回后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal refundBalance(Long customerId, BigDecimal amount, 
                                    String referenceType, Long referenceId, Long createdBy) {
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 1)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(1);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = wallet.getBalance();
        
        // 增加余额
        wallet.add(amount);
        walletRepository.save(wallet);
        
        // 记录交易流水
        recordTransaction(customerId, 1, 3, amount, 
                         balanceBefore, wallet.getBalance(), 
                         referenceType, referenceId, createdBy, "退回本金余额");
        
        return wallet.getBalance();
    }
    
    /**
     * 退回专用金余额（退款）
     * 
     * @param customerId 客户ID
     * @param amount 退回金额
     * @param referenceType 关联类型
     * @param referenceId 关联ID
     * @param createdBy 操作人ID
     * @return 退回后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal refundSpecialFund(Long customerId, BigDecimal amount, 
                                       String referenceType, Long referenceId, Long createdBy) {
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 2)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(2);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = wallet.getBalance();
        
        // 增加余额
        wallet.add(amount);
        walletRepository.save(wallet);
        
        // 记录交易流水
        recordTransaction(customerId, 2, 3, amount, 
                         balanceBefore, wallet.getBalance(), 
                         referenceType, referenceId, createdBy, "退回专用金余额");
        
        return wallet.getBalance();
    }

    /**
     * 退回积分
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal refundPoints(Long customerId, BigDecimal points,
                                   String referenceType, Long referenceId, Long createdBy, String remark) {
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 3)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(3);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        BigDecimal balanceBefore = wallet.getBalance();
        wallet.add(points);
        walletRepository.save(wallet);
        recordTransaction(customerId, 3, 3, points,
                balanceBefore, wallet.getBalance(),
                referenceType, referenceId, createdBy, remark != null ? remark : "积分退回");
        return wallet.getBalance();
    }
    
    /**
     * 查询本金余额
     * 
     * @param customerId 客户ID
     * @return 余额
     */
    public BigDecimal getBalance(Long customerId) {
        return walletRepository.findByCustomerIdAndWalletType(customerId, 1)
                .map(CustomerWallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }
    
    /**
     * 查询专用金余额
     * 
     * @param customerId 客户ID
     * @return 余额
     */
    public BigDecimal getSpecialFundBalance(Long customerId) {
        return walletRepository.findByCustomerIdAndWalletType(customerId, 2)
                .map(CustomerWallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }
    
    /**
     * 增加本金余额
     * 
     * @param customerId 客户ID
     * @param amount 增加金额
     * @param referenceType 关联类型
     * @param referenceId 关联ID
     * @param createdBy 操作人ID
     * @param remark 备注
     * @return 增加后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal addBalance(Long customerId, BigDecimal amount, 
                                String referenceType, Long referenceId, Long createdBy, String remark) {
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 1)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(1);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    newWallet.setCreatedAt(java.time.LocalDateTime.now());
                    newWallet.setUpdatedAt(java.time.LocalDateTime.now());
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = wallet.getBalance();
        wallet.add(amount);
        walletRepository.save(wallet);
        
        recordTransaction(customerId, 1, 4, amount,
                balanceBefore, wallet.getBalance(),
                referenceType, referenceId, createdBy, remark != null ? remark : "增加本金余额");
        
        return wallet.getBalance();
    }
    
    /**
     * 增加专用金余额
     * 
     * @param customerId 客户ID
     * @param amount 增加金额
     * @param referenceType 关联类型
     * @param referenceId 关联ID
     * @param createdBy 操作人ID
     * @param remark 备注
     * @return 增加后的余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal addSpecialFund(Long customerId, BigDecimal amount,
                                     String referenceType, Long referenceId, Long createdBy, String remark) {
        validateManualGrant(customerId, amount);
        CustomerWallet wallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(customerId, 2)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(customerId);
                    newWallet.setWalletType(2);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = wallet.getBalance();
        wallet.add(amount);
        walletRepository.save(wallet);
        
        recordTransaction(customerId, 2, 4, amount,
                balanceBefore, wallet.getBalance(),
                referenceType, referenceId, createdBy, remark != null ? remark : "增加专用金余额");
        
        return wallet.getBalance();
    }
    
    /** 手动发放的金额防线：必须在任何钱包/流水写入之前拒绝非法金额与无限增发。 */
    private static void validateManualGrant(Long customerId, BigDecimal amount) {
        if (customerId == null) {
            throw new IllegalArgumentException("客户ID不能为空");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(MAX_SINGLE_SPECIAL_FUND_GRANT) > 0) {
            throw new IllegalArgumentException("发放金额必须大于0、最多两位小数，且单次不超过"
                    + MAX_SINGLE_SPECIAL_FUND_GRANT.toPlainString() + "元");
        }
    }

    /**
     * 校验钱包验证码（简化实现，实际应该调用验证码服务）
     * 
     * @param customerId 客户ID
     * @param authCode 验证码
     * @return 是否有效
     */
    public boolean validateWalletAuthCode(Long customerId, String authCode) {
        if (customerId == null || authCode == null || authCode.length() != 6) {
            return false;
        }

        Optional<WalletAuthCode> authCodeRecord = walletAuthCodeRepository.findByCustomerIdAndAuthCode(customerId, authCode);
        if (authCodeRecord.isEmpty()) {
            return false;
        }

        WalletAuthCode record = authCodeRecord.get();
        if (record.getStatus() == null || record.getStatus() != 1) {
            return false;
        }
        return record.getExpireAt() != null && record.getExpireAt().isAfter(LocalDateTime.now());
    }

    /**
     * 生成并保存钱包验证码（用于小程序请求发送给客户）
     *
     * @param customerId 客户ID
     * @param orderId    关联订单ID（可选）
     * @param amount     支付金额（可选，存为参考）
     * @param expireMinutes 过期分钟
     * @return 生成的6位验证码（注意：测试环境返回，生产可改为不直接返回）
     */
    @Transactional(rollbackFor = Exception.class)
    public String generateAndStoreWalletAuthCode(Long customerId, Long orderId, java.math.BigDecimal amount, int expireMinutes) {
        String code = String.format(java.util.Locale.ROOT, "%0" + AUTH_CODE_LENGTH + "d", AUTH_CODE_RANDOM.nextInt(1_000_000));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime expireAt = now.plusMinutes(expireMinutes > 0 ? expireMinutes : 5);
        walletAuthCodeRepository.insertIssuedCode(customerId, orderId, code, amount != null ? amount : java.math.BigDecimal.ZERO, expireAt, now);
        logger.info("钱包授权码已生成，expireAt={}", expireAt);
        return code;
    }


    /**
     * @deprecated 废弃，使用 validateAuthCodeOnly + consumeAuthCode 组合
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean validateAndConsumeWalletAuthCode(Long customerId, String authCode, Long orderId, java.math.BigDecimal amount) {
        throw new UnsupportedOperationException("此方法已废弃，请使用 validateAuthCodeOnly 和 consumeAuthCode 的组合");
    }

    /**
     * Associate previously consumed auth code with an orderId (used when consumption occurred before order saved)
     */
    @Transactional(rollbackFor = Exception.class)
    public void associateAuthCodeWithOrder(String authCode, Long orderId) {
        if (authCode == null || orderId == null) return;
        int updated = walletAuthCodeRepository.associateConsumedCode(authCode, orderId);
        if (updated > 0) {
            logger.info("钱包授权已关联订单，orderId={}", orderId);
        } else {
            logger.warn("钱包授权未关联订单，orderId={}", orderId);
        }
    }
    
    // ==================== 私有方法 ====================
    
    private void recordTransaction(Long customerId, Integer walletType, Integer transactionType,
                                   BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   String referenceType, Long referenceId, Long createdBy, String remark) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomerId(customerId);
        transaction.setWalletType(walletType);
        transaction.setTransactionType(transactionType);
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setRemark(remark);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(createdBy);
        transactionRepository.save(transaction);
    }

    private void ensurePointsNotFrozen(CustomerWallet wallet) {
        if (wallet.getFrozenBalance() != null && wallet.getFrozenBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("wallet-3002: 积分账户已冻结，无法操作");
        }
    }

}

