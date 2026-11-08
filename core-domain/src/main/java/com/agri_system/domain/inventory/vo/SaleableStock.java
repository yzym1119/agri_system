package com.agri_system.domain.inventory.vo;

import java.math.BigDecimal;

/** 当前可参与销售出库的余额；保留原批持久基准单位，禁止跨单位盲加。 */
public record SaleableStock(Long productId, Long baseUnitId, BigDecimal availableBaseQuantity) {}
