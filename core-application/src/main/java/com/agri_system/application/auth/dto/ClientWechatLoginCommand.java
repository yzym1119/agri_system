package com.agri_system.application.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户微信登录命令
 * 
 * @author AI Generated
 */
@Data
public class ClientWechatLoginCommand {
    
    /** 微信code */
    @NotBlank(message = "微信code不能为空")
    private String code;
}






