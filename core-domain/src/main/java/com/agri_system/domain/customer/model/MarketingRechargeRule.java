package com.agri_system.domain.customer.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值营销规则实体
 * 
 * @author AI Generated
 */
@Data
@Table("marketing_recharge_rule")
public class MarketingRechargeRule {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 规则名称 */
    private String name;
    
    /** 门槛金额（元） */
    private BigDecimal minRechargeAmount;
    
    /** 升级到的VIP等级 */
    private Integer targetVipLevel;
    
    /** 赠送VIP时长(天)，默认1年 */
    private Integer giftDurationDays;
    
    /** 赠送专用金（元） */
    private BigDecimal giftSpecialFund;
    
    /** 赠送优惠券模板ID */
    private Long giftTemplateId;
    
    /** 赠送优惠券模板名称（不持久化） */
    @Column(ignore = true)
    private String giftTemplateName;
    
    /** 赠送优惠券模板状态（不持久化，用于前端显示） */
    @Column(ignore = true)
    private Integer giftTemplateStatus;
    
    /** 赠送数量 */
    private Integer giftTemplateCount;
    
    /** 状态：1-启用，0-停用 */
    private Integer status;
    
    /** 优先级（数字越小优先级越高） */
    private Integer sortOrder;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查规则是否启用
     * 
     * @return 是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
    
    /**
     * 检查充值金额是否满足门槛
     * 
     * @param rechargeAmount 充值金额
     * @return 是否满足
     */
    public boolean matches(BigDecimal rechargeAmount) {
        if (!isEnabled()) {
            return false;
        }
        return minRechargeAmount != null && rechargeAmount != null 
            && rechargeAmount.compareTo(minRechargeAmount) >= 0;
    }
}







