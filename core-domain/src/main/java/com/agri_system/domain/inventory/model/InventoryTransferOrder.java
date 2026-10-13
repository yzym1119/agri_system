package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存调拨单实体
 */
@Data
@Table("inventory_transfer")
public class InventoryTransferOrder {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String requestKey;

    private String transferNo;

    private Long fromStoreId;

    private Long toStoreId;

    private Long productId;

    private BigDecimal quantity;

    private String unit;

    /**
     * 状态：1-待调出，2-已调出，3-已入库，4-已取消
     */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;
}
