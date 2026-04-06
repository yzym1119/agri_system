package com.agri_system.application.customer.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值命令
 * 
 * @author AI Generated
 */
@Data
public class RechargeCommand {
    
    /** 客户ID */
    private Long customerId;
    
    /** 充值金额 */
    private BigDecimal amount;
    
    /** 支付方式：1-现金，2-微信，3-支付宝 */
    private Integer paymentMethod;
    
    /** 操作人ID */
    private Long createdBy;
}







