package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存批次实体（聚合根）
 * 核心写表，所有库存变动都基于批次
 * 
 * @author AI Generated
 */
@Data
@Table("inventory_batch")
public class InventoryBatch {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 门店ID */
    @Column(tenantId = true)
    private Long storeId;
    
    /** 商品ID */
    private Long productId;
    
    /** 批次号（手动输入或系统生成） */
    private String batchNo;
    
    /** 生产日期 */
    private LocalDate productionDate;
    
    /** 有效期至 */
    private LocalDate expireDate;
    
    /** 当前剩余数量 */
    private BigDecimal quantity;
    
    /** 该批次的成本价（不可变，不允许修改） */
    private BigDecimal costPrice;
    
    /** 是否有效：1-有效，0-归档/售罄 */
    private Integer isActive;
    
    /** 来源类型：PURCHASE-采购，TRANSFER_IN-调拨入库，ADJUSTMENT-盘盈等 */
    private String sourceType;
    
    /** 来源单据ID */
    private Long sourceId;
    
    /** 来源单据行号 */
    private Integer sourceLineNo;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查批次是否有效
     */
    public boolean isActive() {
        return isActive != null && isActive == 1;
    }
    
    /**
     * 检查批次是否已售罄
     */
    public boolean isSoldOut() {
        return quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0;
    }
    
    /**
     * 检查批次是否已过期
     */
    public boolean isExpired() {
        if (expireDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(expireDate);
    }
    
    /**
     * 检查批次是否即将过期（7天内）
     */
    public boolean isExpiringSoon() {
        if (expireDate == null) {
            return false;
        }
        LocalDate sevenDaysLater = LocalDate.now().plusDays(7);
        return !isExpired() && !expireDate.isAfter(sevenDaysLater);
    }
    
    /**
     * 归档批次（售罄或手动归档）
     */
    public void archive() {
        this.isActive = 0;
    }
    
    /**
     * 激活批次
     */
    public void activate() {
        this.isActive = 1;
    }
    
    /**
     * 扣减数量
     * 
     * @param amount 扣减数量
     * @throws IllegalArgumentException 如果数量不足
     */
    public void deduct(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于0");
        }
        if (quantity == null || quantity.compareTo(amount) < 0) {
            throw new IllegalArgumentException("库存不足，当前数量: " + quantity + ", 需要扣减: " + amount);
        }
        this.quantity = this.quantity.subtract(amount);
        
        // 如果扣减后数量为0，自动归档
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            archive();
        }
    }
    
    /**
     * 增加数量
     * 
     * @param amount 增加数量
     */
    public void add(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("增加数量必须大于0");
        }
        if (this.quantity == null) {
            this.quantity = BigDecimal.ZERO;
        }
        this.quantity = this.quantity.add(amount);
        // 增加数量后，如果之前是归档状态，需要激活
        if (!isActive()) {
            activate();
        }
    }
    
    /**
     * 检查是否为同源批次
     * 同源规则：store_id + product_id + batch_no + source_type + source_id + source_line_no 完全一致
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param batchNo 批次号
     * @param sourceType 来源类型
     * @param sourceId 来源单据ID
     * @param sourceLineNo 来源单据行号
     * @return 是否为同源批次
     */
    public boolean isSameSource(Long storeId, Long productId, String batchNo, 
                                String sourceType, Long sourceId, Integer sourceLineNo) {
        if (!this.storeId.equals(storeId) || !this.productId.equals(productId)) {
            return false;
        }
        if (!this.batchNo.equals(batchNo)) {
            return false;
        }
        // 比较sourceType（允许null）
        if (this.sourceType == null && sourceType != null) {
            return false;
        }
        if (this.sourceType != null && !this.sourceType.equals(sourceType)) {
            return false;
        }
        // 比较sourceId（允许null）
        if (this.sourceId == null && sourceId != null) {
            return false;
        }
        if (this.sourceId != null && !this.sourceId.equals(sourceId)) {
            return false;
        }
        // 比较sourceLineNo（允许null）
        if (this.sourceLineNo == null && sourceLineNo != null) {
            return false;
        }
        if (this.sourceLineNo != null && !this.sourceLineNo.equals(sourceLineNo)) {
            return false;
        }
        return true;
    }
}

