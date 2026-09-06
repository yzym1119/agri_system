package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** SKU 的库存权威来源切换状态，绝不能根据余额行存在与否推断。 */
@Data
@Table("inventory_balance_feature_status")
public class InventoryBalanceFeatureStatus {
    public static final String LEGACY = "LEGACY";
    public static final String BATCH_LOCATION = "BATCH_LOCATION";

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long storeId;
    private Long productId;
    private String balanceMode;
    private LocalDateTime cutoverAt;
    private Long cutoverBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
