package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 门店库存位置。具体货位编码由门店按需启用。 */
@Data
@Table("inventory_location")
public class InventoryLocation {
    @Id(keyType = KeyType.Auto)
    private Long id;
    @Column(tenantId = true)
    private Long storeId;
    private String locationType;
    private String code;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum LocationType {
        SALES_FLOOR, STORE_BACKROOM, CENTRAL_WAREHOUSE, OTHER_STORE
    }
}
