package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细实体
 * 
 * @author AI Generated
 */
@Data
@Table("order_item")
public class OrderItem {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 订单ID */
    private Long orderId;
    
    /** 商品ID（单品订单） */
    private Long productId;
    
    /** 套餐ID（套餐订单） */
    private Long packageId;
    
    /** 明细类型：1-商品，2-套餐 */
    private Integer itemType;
    
    /** 商品/套餐名称（冗余字段，便于查询） */
    private String productName;
    
    /** 数量 */
    private BigDecimal quantity;
    
    /** 单位 */
    private String unit;
    
    /** 单价（元） */
    private BigDecimal unitPrice;
    
    /** 成本快照（元） */
    private BigDecimal costPriceSnapshot;

    /** 小计金额（元） */
    private BigDecimal totalPrice;
    
    /** 提成金额（元） */
    private BigDecimal commissionAmount;
    
    /** 是否允许使用专用金：0-不允许，1-允许（来源于商品快照） */
    private Integer allowSpecialFund;
    
    /** 排序顺序 */
    private Integer sortOrder;
}



