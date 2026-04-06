package com.agri_system.domain.store.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店实体（聚合根）
 * 
 * @author AI Generated
 */
@Data
@Table("store")
public class Store {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 门店编码（唯一） */
    private String code;
    
    /** 门店名称 */
    private String name;
    
    /** 联系电话（必填） */
    private String phone;
    
    /** 详细地址 */
    private String address;
    
    /** 店长ID（关联staff表，可空） */
    private Long managerId;
    
    /** 状态：1-启用，0-停用 */
    private Integer status;
    
    /** 是否支持自提：1-是，0-否 */
    private Integer supportPickup;
    
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
    
    /** 逻辑删除：1-已删，0-未删 */
    @Column(isLogicDelete = true)
    private Integer deleted;
    
    /**
     * 检查门店是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
    
    /**
     * 检查门店是否支持自提
     */
    public boolean supportsPickup() {
        return supportPickup != null && supportPickup == 1;
    }
    
    /**
     * 检查门店是否已删除
     */
    public boolean hasBeenDeleted() {
        return deleted != null && deleted == 1;
    }
    
    /**
     * 启用门店
     */
    public void enable() {
        this.status = 1;
    }
    
    /**
     * 停用门店
     */
    public void disable() {
        this.status = 0;
    }
    
    /**
     * 逻辑删除门店
     */
    public void delete() {
        this.deleted = 1;
    }
}










