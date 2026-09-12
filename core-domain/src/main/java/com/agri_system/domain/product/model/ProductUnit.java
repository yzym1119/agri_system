package com.agri_system.domain.product.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SKU 业务单位，系数直接表示一个该单位对应的库存基准数量。 */
@Data
@Table("product_unit")
public class ProductUnit {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long productId;
    private String unitName;
    private BigDecimal baseQuantityPerUnit;
    private Integer isBaseUnit;
    private Integer allowPurchase;
    private Integer allowSale;
    private Integer allowInventory;
    private Integer allowTransfer;
    private String barcode;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    /** 旧主辅字段的只读投影信息，不映射到 product_unit 表。 */
    @Column(ignore = true)
    private String projectionStatus;

    @Column(ignore = true)
    private boolean readOnly;

    public boolean isEnabled() {
        return status != null && status == 1;
    }

    public boolean isBase() {
        return isBaseUnit != null && isBaseUnit == 1;
    }
}
