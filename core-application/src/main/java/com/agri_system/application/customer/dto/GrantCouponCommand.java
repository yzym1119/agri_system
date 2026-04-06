package com.agri_system.application.customer.dto;

import lombok.Data;

/**
 * 手动发券命令
 * 
 * @author AI Generated
 */
@Data
public class GrantCouponCommand {
    
    /** 客户ID */
    private Long customerId;
    
    /** 优惠券模板ID */
    private Long templateId;
    
    /** 发放数量 */
    private Integer count;
    
    /** 操作人ID */
    private Long createdBy;
}






