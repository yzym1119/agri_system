package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存快照实体（只读）
 * 数据来源必须是inventory_batch的聚合结果
 * 
 * @author AI Generated
 */
@Data
@Table("inventory")
public class Inventory {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 门店ID */
    @Column(tenantId = true)
    private Long storeId;
    
    /** 商品ID */
    private Long productId;
    
    /** 库存数量（快照，由批次表聚合计算） */
    private BigDecimal quantity;
}









