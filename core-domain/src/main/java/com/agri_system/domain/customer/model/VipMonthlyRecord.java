package com.agri_system.domain.customer.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * VIP每月权益发放记录实体
 * 
 * @author AI Generated
 */
@Data
@Table("vip_monthly_record")
public class VipMonthlyRecord {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 发放月份，如 2023-10 */
    private String yearMonth;
    
    /** VIP等级 */
    private Integer vipLevel;
    
    /** 记录类型：1-系统自动发放（月度权益），2-充值奖励发放 */
    private Integer recordType;
    
    /** 发放时间 */
    private LocalDateTime grantTime;
    
    /** 发放内容描述 */
    private String content;
}







