package com.agri_system.application.productpackage.dto.vo;

import java.math.BigDecimal;
import java.util.List;

/** 套餐按套报价及实际物料金额；前端不承担拆分、计价或专用金金额计算。 */
public record PackagePriceQuote(String quoteId, Long packageId, String packageName, BigDecimal quantity,
                                BigDecimal suggestedUnitPrice, BigDecimal referenceFloor, boolean belowReference,
                                BigDecimal dealUnitPrice, BigDecimal totalAmount, BigDecimal specialFundEligibleAmount,
                                List<Item> items) {
    public record Item(Long productId, String productName, BigDecimal quantity, String unit, Long saleUnitId,
                       BigDecimal unitPrice, BigDecimal totalPrice, Integer allowSpecialFund,
                       BigDecimal suggestedUnitPrice) {}
}
