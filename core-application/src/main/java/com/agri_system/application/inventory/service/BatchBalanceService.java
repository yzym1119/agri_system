package com.agri_system.application.inventory.service;

import com.agri_system.domain.inventory.model.InventoryReceiptBatch;

/**
 * 收货事实已落库后的余额适配点。
 * 实现由后续批次余额任务提供；未配置实现时收货事实仍可独立保存。
 */
public interface BatchBalanceService {
    void increaseFromReceipt(Long receiptId, InventoryReceiptBatch receiptBatch);
}
