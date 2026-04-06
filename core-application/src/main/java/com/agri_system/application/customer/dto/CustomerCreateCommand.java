package com.agri_system.application.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户创建命令
 * 
 * @author AI Generated
 */
@Data
public class CustomerCreateCommand {
    
    /** 客户姓名 */
    @NotBlank(message = "客户姓名不能为空")
    private String name;
    
    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;
    
    /** 身份证号 */
    private String idCard;
    
    /** 地址 */
    private String address;
    
    /** 归属员工ID */
    private Long bindStaffId;
    
    /** 备注 */
    private String remark;
    
    /** 操作人ID */
    private Long createdBy;
}

