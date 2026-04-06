package com.agri_system.application.customer.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * VIP月度权益发放记录VO
 * 
 * @author AI Generated
 */
@Data
public class VipMonthlyRecordVO {
    
    private Long id;
    private Long customerId;
    private String yearMonth;
    private Integer vipLevel;
    private Integer recordType; // 记录类型：1-系统自动发放（月度权益），2-充值奖励发放
    private LocalDateTime grantTime;
    private String content; // 发放内容描述
}

