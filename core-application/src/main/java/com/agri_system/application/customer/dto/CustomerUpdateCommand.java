package com.agri_system.application.customer.dto;

import lombok.Data;

/**
 * 客户更新命令
 * 
 * @author AI Generated
 */
@Data
public class CustomerUpdateCommand {
    
    /** 客户姓名 */
    private String name;
    
    /** 身份证号 */
    private String idCard;
    
    /** 地址 */
    private String address;
    
    /** 归属员工ID */
    private Long bindStaffId;
    
    /** 备注 */
    private String remark;
    
    /** 操作人ID */
    private Long updatedBy;
}

