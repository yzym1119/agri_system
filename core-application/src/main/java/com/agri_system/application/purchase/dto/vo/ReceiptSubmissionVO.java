package com.agri_system.application.purchase.dto.vo;

import java.util.List;

/** 原收货事实快照；UNKNOWN只能说明尚未读到本人的结果，不能作为未收货证明。 */
public record ReceiptSubmissionVO(String requestKey,String status,String storeId,String purchaseOrderId,String receiptId,
                                  String receiptNo,List<Line> lines) {
    public record Line(String purchaseOrderItemId,String productId,String remark,List<Batch> batches) {}
    public record Batch(String id,String internalBatchNo,String supplierBatchNo,String originalUnitName,String originalQuantity,
                        String originalUnitCost,String productionDate,String expiryDate,String dateStatus,String inspectionReference) {}
}
