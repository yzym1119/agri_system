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
    @jakarta.validation.constraints.Size(max=512,message="微信code过长")
    private String code;

    @Override public String toString(){return "ClientWechatLoginCommand[授权凭证隐藏]";}
}






