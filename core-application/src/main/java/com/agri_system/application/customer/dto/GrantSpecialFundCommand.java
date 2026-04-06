package com.agri_system.application.customer.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手动发专用金命令
 * 
 * @author AI Generated
 */
@Data
public class GrantSpecialFundCommand {
    
    /** 客户ID */
    private Long customerId;
    
    /** 发放金额 */
    private BigDecimal amount;
    
    /** 备注 */
    private String remark;
    
    /** 操作人ID */
    private Long createdBy;
}






