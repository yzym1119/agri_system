package com.agri_system.domain.auth.model;

import lombok.Data;
import java.time.LocalDateTime;

/** 初次开户/关联申请与一次核验决定；不替代客户或资金事实。 */
@Data
public class CustomerIdentityEnrollment {
    private Long id;
    private String requestKey;
    private String fingerprint;
    private String activeSubject;
    private String appId;
    private String openId;
    private String phone;
    private String displayName;
    private LocalDateTime verifiedAt;
    private String status;
    private String reviewReason;
    private Long customerId;
    private Long identityId;
    private Long identityAuthVersion;
    private String decisionRequestKey;
    private String decisionFingerprint;
    private Long reviewedBy;
    private String verificationMethod;
    private String decisionReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Override public String toString(){return "CustomerIdentityEnrollment[申请内容隐藏]";}
}
