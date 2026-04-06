package com.agri_system.domain.customer.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP周期权益实体
 * 
 * @author AI Generated
 */
@Data
@Table("marketing_vip_rights")
public class MarketingVipRights {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 关联VIP等级 */
    private Integer vipLevel;
    
    /** 权益类型：1-送券，2-送专用金 */
    private Integer benefitType;
    
    /** 专用金金额（元） */
    private BigDecimal benefitValue;
    
    /** 赠送的优惠券模板ID */
    private Long templateId;
    
    /** 赠送优惠券模板名称（不持久化） */
    @Column(ignore = true)
    private String templateName;
    
    /** 赠送优惠券模板状态（不持久化，用于前端显示） */
    @Column(ignore = true)
    private Integer templateStatus; // 1-启用，0-停用
    
    /** 赠送数量 */
    private Integer templateCount;
    
    /** 状态：1-启用，0-停用 */
    private Integer status;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查权益是否启用
     * 
     * @return 是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
    
    /**
     * 是否为送券权益
     * 
     * @return 是否送券
     */
    public boolean isCouponBenefit() {
        return benefitType != null && benefitType == 1;
    }
    
    /**
     * 是否为送专用金权益
     * 
     * @return 是否送专用金
     */
    public boolean isSpecialFundBenefit() {
        return benefitType != null && benefitType == 2;
    }
}







