package com.agri_system.domain.auth.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户身份实体
 * 
 * @author AI Generated
 */
@Data
@Table("customer_identity")
public class CustomerIdentity {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 身份类型：WECHAT_OPENID-微信OpenID，PHONE-手机号，OTHER-其他 */
    private String identityType;

    /** 微信AppID命名空间；空串仅保留未知历史，不能猜成当前应用。 */
    private String appId;
    
    /** 身份值（OpenID/手机号等） */
    private String identityValue;
    
    /** 微信SessionKey（仅微信登录时使用） */
    private String sessionKey;
    
    /** 状态：1-正常，2-停用 */
    private Integer status;

    /** 持久授权版本；关联或状态改变单调递增，禁止旧会话复活。 */
    private Long authVersion;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查身份是否正常
     * 
     * @return 是否正常
     */
    public boolean isActive() {
        return status != null && status == 1;
    }
    
    /**
     * 是否为微信OpenID类型
     * 
     * @return 是否微信OpenID
     */
    public boolean isWechatOpenId() {
        return "WECHAT_OPENID".equals(identityType);
    }
}






