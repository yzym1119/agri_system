package com.agri_system.domain.purchase.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商实体
 * 
 * @author AI Generated
 */
@Data
@Table("supplier")
public class Supplier {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 供应商编码（唯一） */
    private String code;
    
    /** 供应商名称 */
    private String name;
    
    /** 联系人姓名 */
    private String contactName;
    
    /** 联系人电话 */
    private String contactPhone;
    
    /** 银行账户 */
    private String bankAccount;
    
    /** 开户银行 */
    private String bankName;
    
    /** 供应商地址 */
    private String address;
    
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
    
    // 注意：supplier表没有deleted字段，不使用逻辑删除
    
    /**
     * 检查供应商是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
    
    /**
     * 启用供应商
     */
    public void enable() {
        this.status = 1;
    }
    
    /**
     * 停用供应商
     */
    public void disable() {
        this.status = 2;
    }
}

