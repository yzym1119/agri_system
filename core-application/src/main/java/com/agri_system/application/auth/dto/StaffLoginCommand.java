package com.agri_system.application.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 员工登录命令
 * 
 * @author AI Generated
 */
@Data
public class StaffLoginCommand {
    
    /** 账号（编码或手机号） */
    @NotBlank(message = "账号不能为空")
    private String account;
    
    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
    
    /** 登录方式：PASSWORD-密码登录 */
    private String loginType = "PASSWORD";
}






