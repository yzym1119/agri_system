package com.agri_system.domain.purchase.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单实体（聚合根）
 * 
 * @author AI Generated
 */
@Data
@Table("purchase_order")
public class PurchaseOrder {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 采购单号，唯一 */
    private String purchaseNo;
    
    /** 供应商ID（必填） */
    private Long supplierId;
    
    /** 入库门店ID（必填） */
    @Column(tenantId = true)
    private Long storeId;
    
    /** 明细金额合计 */
    private BigDecimal totalAmount;
    
    /** 状态：1-待提交，2-待入库，3-已入库，4-已取消 */
    private Integer status;

    /** 逻辑删除：1-已删，0-未删 */
    @Column(isLogicDelete = true)
    private Integer deleted;
    
    /** 采购日期 */
    private LocalDate purchaseDate;
    
    /** 预计到货日期 */
    private LocalDate expectedArrivalDate;
    
    /** 实际到货日期 */
    private LocalDate arrivalDate;
    
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
    
    /**
     * 检查是否可以审核
     */
    public boolean canAudit() {
        return status != null && status == 1; // 待提交状态，可提交/审核
    }
    
    /**
     * 检查是否可以入库
     */
    public boolean canInbound() {
        return status != null && status == 2; // 待入库状态
    }
    
    /**
     * 检查是否已入库
     */
    public boolean isInbound() {
        return status != null && status == 3; // 已入库状态
    }
    
    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return status != null && status == 4; // 已取消状态
    }
    
    /**
     * 审核采购单
     */
    public void audit() {
        if (!canAudit()) {
            throw new IllegalStateException("采购单状态不允许审核");
        }
        this.status = 2; // 待入库
    }
    
    /**
     * 入库完成
     */
    public void completeInbound() {
        if (!canInbound()) {
            throw new IllegalStateException("采购单状态不允许入库");
        }
        this.status = 3; // 已入库
    }
    
    /**
     * 取消采购单
     */
    public void cancel() {
        if (isInbound()) {
            throw new IllegalStateException("已入库的采购单不能取消");
        }
        this.status = 4; // 已取消
    }
}

