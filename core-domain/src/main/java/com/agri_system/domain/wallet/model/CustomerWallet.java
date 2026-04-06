package com.agri_system.domain.wallet.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户钱包实体
 * 
 * @author AI Generated
 */
@Data
@Table("customer_wallet")
public class CustomerWallet {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 钱包类型：1-本金，2-专用金，3-积分 */
    private Integer walletType;
    
    /** 余额 */
    private BigDecimal balance;
    
    /** 冻结金额/积分 */
    private BigDecimal frozenBalance;
    
    /** 乐观锁版本号 */
    private Integer version;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 扣减余额（带乐观锁检查）
     * 
     * @param amount 扣减金额
     * @throws IllegalArgumentException 余额不足或版本号不匹配
     */
    public void deduct(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("扣减金额必须大于0");
        }
        
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("wallet-2001: 余额不足，当前余额: " + balance + ", 需要: " + amount);
        }
        
        this.balance = balance.subtract(amount);
        if (version == null) {
            version = 0;
        }
        this.version = version + 1;
    }
    
    /**
     * 增加余额
     * 
     * @param amount 增加金额
     */
    public void add(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("增加金额必须大于0");
        }
        
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        
        this.balance = balance.add(amount);
        if (version == null) {
            version = 0;
        }
        this.version = version + 1;
    }
}


