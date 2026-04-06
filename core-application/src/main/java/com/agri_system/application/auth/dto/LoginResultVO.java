package com.agri_system.application.auth.dto;

import lombok.Data;

/**
 * 登录结果VO
 * 
 * @author AI Generated
 */
@Data
public class LoginResultVO {
    
    /** Token名称 */
    private String tokenName;
    
    /** Token值 */
    private String tokenValue;
    
    /** 用户信息 */
    private UserInfo userInfo;
    
    /**
     * 用户信息
     */
    @Data
    public static class UserInfo {
        /** 用户ID */
        private Long id;
        
        /** 用户名/姓名 */
        private String name;
        
        /** 角色（员工）或类型（客户） */
        private String role;
        
        /** 其他信息（JSON格式，可扩展） */
        private Object extra;
    }
}






