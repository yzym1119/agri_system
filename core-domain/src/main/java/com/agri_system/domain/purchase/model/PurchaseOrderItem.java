package com.agri_system.domain.purchase.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单明细实体
 * 
 * @author AI Generated
 */
@Data
@Table("purchase_order_item")
public class PurchaseOrderItem {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 采购订单ID */
    private Long purchaseOrderId;
    
    /** 商品ID（必须存在） */
    private Long productId;
    
    /** 数量（> 0） */
    private BigDecimal quantity;
    
    /** 单位 */
    private String unit;
    
    /** 本次真实进价（元，用于计算加权平均成本） */
    private BigDecimal buyPrice;
    
    /** 小计金额（元） */
    private BigDecimal totalPrice;
    
    /** 排序顺序 */
    private Integer sortOrder;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /**
     * 计算金额
     */
    public void calculateTotalPrice() {
        if (quantity != null && buyPrice != null) {
            this.totalPrice = quantity.multiply(buyPrice).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
    
    /**
     * 验证数量是否合法
     */
    public boolean isValidQuantity() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 验证价格是否合法
     */
    public boolean isValidBuyPrice() {
        return buyPrice != null && buyPrice.compareTo(BigDecimal.ZERO) > 0;
    }
}

