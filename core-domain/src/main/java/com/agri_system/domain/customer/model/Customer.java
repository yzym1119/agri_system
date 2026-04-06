package com.agri_system.domain.customer.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户实体
 * 
 * @author AI Generated
 */
@Data
@Table("customer")
public class Customer {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 客户编码 */
    private String code;
    
    /** 客户姓名 */
    private String name;
    
    /** 手机号 */
    private String phone;
    
    /** 客户类型：1-普通客户，2-VIP客户 */
    private Integer type;
    
    /** VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效） */
    private Integer vipLevel;
    
    /** VIP开始日期 */
    private LocalDate vipStartDate;
    
    /** VIP结束日期 */
    private LocalDate vipEndDate;
    
    /** 地址 */
    private String address;
    
    /** 身份证号 */
    private String idCard;
    
    /** 赊销额度（元） */
    private BigDecimal creditLimit;
    
    /** 状态：1-正常，2-停用 */
    private Integer status;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /** 创建人ID */
    private Long createdBy;
    
    /** 更新人ID */
    private Long updatedBy;
    
    /** 总订单数 */
    private Integer totalOrderCount;
    
    /** 总订单金额 */
    private BigDecimal totalOrderAmount;
    
    /** 最后订单时间 */
    private LocalDateTime lastOrderTime;
    
    /** 绑定员工ID */
    private Long bindStaffId;
    
    /** 乐观锁版本号 */
    private Integer version;
    
    /** 逻辑删除：1-已删，0-未删 */
    @Column(isLogicDelete = true)
    private Integer deleted;
    
    /**
     * 是否为VIP客户
     * 
     * @return 是否VIP
     */
    public boolean isVip() {
        return type != null && type == 2;
    }
    
    /**
     * 升级为VIP
     * 
     * @param vipLevel VIP等级
     * @param durationDays 时长（天）
     */
    public void upgradeToVip(Integer vipLevel, Integer durationDays) {
        this.type = 2; // VIP客户
        this.vipLevel = vipLevel;
        
        LocalDate now = LocalDate.now();
        if (vipEndDate == null || vipEndDate.isBefore(now)) {
            // 如果已过期或未设置，从今天开始
            this.vipStartDate = now;
            this.vipEndDate = now.plusDays(durationDays);
        } else {
            // 如果未过期，延长到期时间
            this.vipEndDate = vipEndDate.plusDays(durationDays);
        }
    }
    
    /**
     * 检查VIP是否已过期
     * 
     * @return 是否过期
     */
    public boolean isVipExpired() {
        if (!isVip() || vipEndDate == null) {
            return true;
        }
        return LocalDate.now().isAfter(vipEndDate);
    }
    
    /**
     * 降级为普通客户
     */
    public void downgradeToNormal() {
        this.type = 1; // 普通客户
        this.vipLevel = null;
        this.vipStartDate = null;
        this.vipEndDate = null;
    }
}






