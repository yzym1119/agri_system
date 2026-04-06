package com.agri_system.domain.customer.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户权益流水实体
 * 
 * @author AI Generated
 */
@Data
@Table("customer_benefit_log")
public class CustomerBenefitLog {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 来源类型：1-每月权益(自动) 2-充值赠送 3-手动发放 */
    private Integer sourceType;
    
    /** 内容快照(如: 现金50元, 化肥券x1) */
    private String benefitContent;
    
    /** 关联ID(如充值规则ID, 模板ID) */
    private Long associatedId;
    
    /** 操作人(0为系统) */
    private Long createdBy;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
}









