package com.agri_system.domain.inventory.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 统一批次账的只读报表投影；数量与单位成本均按商品主单位，不可用于库存写入。 */
public record InventoryReportBatch(Long batchId, Long storeId, Long productId, String supplierBatchNo,
                                   BigDecimal quantityInMainUnit, BigDecimal unitCostInMainUnit,
                                   LocalDate productionDate, LocalDate expiryDate, LocalDateTime receivedAt) { }
