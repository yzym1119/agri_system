package com.agri_system.application.purchase.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新供应商命令
 * 
 * @author AI Generated
 */
@Data
public class SupplierUpdateCommand {
    
    /** 供应商ID */
    @NotNull(message = "供应商ID不能为空")
    private Long id;
    
    /** 供应商名称 */
    @NotBlank(message = "供应商名称不能为空")
    private String name;
    
    /** 联系人姓名 */
    private String contactName;
    
    /** 联系人电话 */
    private String contactPhone;
    
    /** 银行账户 */
    private String bankAccount;
    
    /** 开户银行 */
    private String bankName;
    
    /** 供应商地址 */
    private String address;
    
    /** 状态：1-正常，2-停用 */
    private Integer status;
    
    /** 备注 */
    private String remark;
}

