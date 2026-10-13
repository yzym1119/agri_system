package com.agri_system.application.purchase.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 仅返回刚提交收货批次的实际标签内容，内部批次码来自已保存记录。 */
public record ReceiptBatchLabelVO(Long id, Long productId, String internalReceiptBatchNo,
        String supplierBatchNo, String originalUnitName, BigDecimal originalQuantity,
        LocalDate productionDate, LocalDate expiryDate, String dateStatus) { }
