package com.agri_system.application.product.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "商品业务单位")
public class ProductUnitVO {
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
    private boolean readOnly;
    private String projectionStatus;
}
