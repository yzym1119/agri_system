package com.agri_system.domain.wallet.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包交易记录实体
 * 
 * @author AI Generated
 */
@Data
@Table("wallet_transaction")
public class WalletTransaction {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 钱包类型：1-本金，2-专用金，3-积分 */
    private Integer walletType;
    
    /** 交易类型：1-充值，2-消费，3-退款，4-赠送，5-扣除 */
    private Integer transactionType;
    
    /** 金额（正数为增加，负数为减少） */
    private BigDecimal amount;
    
    /** 交易前余额 */
    private BigDecimal balanceBefore;
    
    /** 交易后余额 */
    private BigDecimal balanceAfter;
    
    /** 关联类型（如：ORDER-订单，RECHARGE-充值） */
    private String referenceType;
    
    /** 关联ID */
    private Long referenceId;
    
    /** 备注 */
    private String remark;
    
    /** 支付方式：1-现金，2-微信，3-支付宝（仅充值交易有效） */
    private Integer paymentMethod;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 操作人ID */
    private Long createdBy;
}








