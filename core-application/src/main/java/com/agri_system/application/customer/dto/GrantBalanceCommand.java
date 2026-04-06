package com.agri_system.application.customer.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手动调账命令
 * 
 * @author AI Generated
 */
@Data
public class GrantBalanceCommand {
    
    /** 客户ID */
    private Long customerId;
    
    /** 调整金额（正数为增加，负数为减少） */
    private BigDecimal amount;
    
    /** 备注 */
    private String remark;
    
    /** 操作人ID */
    private Long createdBy;
}






