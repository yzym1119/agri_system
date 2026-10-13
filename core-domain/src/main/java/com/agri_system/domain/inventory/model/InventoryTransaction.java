package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水实体
 * 记录每一次变动的批次、数量、成本价
 * 
 * @author AI Generated
 */
@Data
@Table("inventory_transaction")
public class InventoryTransaction {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 门店ID */
    @Column(tenantId = true)
    private Long storeId;
    
    /** 商品ID */
    private Long productId;
    
    /** 批次ID（关联inventory_batch表） */
    private Long batchId;

    private Long receiptBatchId;
    
    /** 流水类型：1-入库，2-出库，3-调拨出，4-调拨入，5-调整，6-销售，7-退货 */
    private Integer transactionType;
    
    /** 操作前总库存数量 */
    private BigDecimal beforeQuantity;
    
    /** 操作后总库存数量 */
    private BigDecimal afterQuantity;
    
    /** 操作前批次数量 */
    private BigDecimal batchBeforeQty;
    
    /** 操作后批次数量 */
    private BigDecimal batchAfterQty;
    
    /** 数量（正数为增加，负数为减少） */
    private BigDecimal quantity;
    
    /** 成本价（记录当时批次的真实成本价） */
    private BigDecimal costPrice;
    
    /** 单位（主单位，用于统计） */
    private String unit;
    
    /** 原始单位（业务发生时的原始单位，如：瓶、件） */
    private String originalUnit;
    
    /** 原始数量（业务发生时的原始数量，保留4位小数，用于对账） */
    private BigDecimal originalQuantity;
    
    /** 关联类型（如：ORDER-订单，TRANSFER-调拨，ADJUSTMENT-调整） */
    private String referenceType;
    
    /** 关联ID */
    private Long referenceId;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 操作人ID */
    private Long createdBy;
}

