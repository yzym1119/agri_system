package com.agri_system.domain.customer.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体
 * 
 * @author AI Generated
 */
@Data
@Table("coupon_template")
public class CouponTemplate {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 模板名称 */
    private String name;
    
    /** 类型：1-满减券，2-折扣券 */
    private Integer type;
    
    /** 面额/折扣率 */
    private BigDecimal amount;
    
    /** 使用门槛（元） */
    private BigDecimal minOrderAmount;
    
    /** 限制分类ID（为空则全场通用） */
    private Long limitCategoryId;
    
    /** 限制商品ID（为空则不限） */
    private Long limitProductId;
    
    /** 有效期类型：1-固定日期范围，2-领取后有效天数 */
    private Integer validityType;
    
    /** 固定有效期开始 */
    private LocalDateTime validStartTime;
    
    /** 固定有效期结束 */
    private LocalDateTime validEndTime;
    
    /** 领取后有效天数 */
    private Integer validDays;
    
    /** 发放总量限制（-1不限） */
    private Integer totalCount;
    
    /** 状态：1-启用，0-停用 */
    private Integer status;

    /** 逻辑删除：1-已删，0-未删 */
    @Column(isLogicDelete = true)
    private Integer deleted;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查模板是否可用
     * 
     * @return 是否可用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}







