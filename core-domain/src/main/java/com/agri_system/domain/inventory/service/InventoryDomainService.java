package com.agri_system.domain.inventory.service;

import com.agri_system.domain.inventory.enums.InventoryTransactionType;
import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.inventory.model.InventoryTransaction;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存领域服务
 * 核心算法层：同源合并、严格FIFO（悲观锁）、调拨拆批、强一致性同步
 * 
 * @author AI Generated
 */
@RequiredArgsConstructor
public class InventoryDomainService {
    
    private final InventoryBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    
    /**
     * 入库逻辑（同源合并，异源新建）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param batchNo 批次号
     * @param quantity 入库数量
     * @param costPrice 本次进价（批次成本价，不可变）
     * @param productionDate 生产日期（可选）
     * @param expireDate 有效期（可选）
     * @param sourceType 来源类型（PURCHASE-采购，TRANSFER_IN-调拨入库等）
     * @param sourceId 来源单据ID
     * @param sourceLineNo 来源单据行号
     * @param unit 单位
     * @param createdBy 操作人ID
     * @return 批次ID
     */
    public Long inbound(Long storeId, Long productId, String batchNo, 
                       BigDecimal quantity, BigDecimal costPrice,
                       LocalDate productionDate, LocalDate expireDate,
                       String sourceType, Long sourceId, Integer sourceLineNo,
                       String unit, Long createdBy) {
        // 1. 根据同源判定字段查询是否存在可合并批次
        InventoryBatch batch = batchRepository.findBySameSource(
                storeId, productId, batchNo, sourceType, sourceId, sourceLineNo)
                .orElse(null);
        
        // 获取操作前总库存数量
        BigDecimal beforeTotal = inventoryRepository.findByStoreAndProduct(storeId, productId)
                .map(inv -> inv.getQuantity())
                .orElse(BigDecimal.ZERO);
        
        if (batch != null) {
            // 2. 如果匹配 → 合并（仅更新 quantity，成本价不变）
            BigDecimal batchBeforeQty = batch.getQuantity();
            batch.add(quantity);
            BigDecimal batchAfterQty = batch.getQuantity();
            
            // 更新日期（如果提供了新日期）
            if (productionDate != null) {
                batch.setProductionDate(productionDate);
            }
            if (expireDate != null) {
                batch.setExpireDate(expireDate);
            }
            
            batchRepository.save(batch);
            
            // 记录流水（包含before/after，入库时原始单位就是主单位）
            BigDecimal afterTotal = beforeTotal.add(quantity);
            Integer transactionType = determineTransactionType(sourceType);
            recordTransaction(storeId, productId, batch.getId(), transactionType, 
                            quantity, batch.getCostPrice(),
                            beforeTotal, afterTotal,
                            batchBeforeQty, batchAfterQty,
                            unit, unit, quantity, // 入库时原始单位=主单位
                            createdBy, sourceType, sourceId);
        } else {
            // 3. 如果不存在 → 新增批次
            LocalDateTime now = LocalDateTime.now();
            batch = new InventoryBatch();
            batch.setStoreId(storeId);
            batch.setProductId(productId);
            batch.setBatchNo(batchNo);
            batch.setProductionDate(productionDate);
            batch.setExpireDate(expireDate);
            batch.setQuantity(quantity);
            batch.setCostPrice(costPrice); // 成本价不可变
            batch.setIsActive(1);
            batch.setSourceType(sourceType);
            batch.setSourceId(sourceId);
            batch.setSourceLineNo(sourceLineNo);
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            
            batchRepository.save(batch);
            
            // 记录流水（新建批次，原始单位=主单位）
            BigDecimal afterTotal = beforeTotal.add(quantity);
            Integer transactionType = determineTransactionType(sourceType);
            recordTransaction(storeId, productId, batch.getId(), transactionType, 
                            quantity, costPrice,
                            beforeTotal, afterTotal,
                            BigDecimal.ZERO, quantity,
                            unit, unit, quantity, // 入库时原始单位=主单位
                            createdBy, sourceType, sourceId);
        }
        
        // 4. 触发强一致性同步
        refreshTotalStock(storeId, productId);
        
        return batch.getId();
    }
    
    /**
     * 根据来源类型确定流水类型
     * 
     * @param sourceType 来源类型
     * @return 流水类型代码
     */
    private Integer determineTransactionType(String sourceType) {
        if (sourceType == null) {
            return InventoryTransactionType.PURCHASE_IN.getCode(); // 默认采购入库
        }
        // 售后退回
        if ("SALES_RETURN".equalsIgnoreCase(sourceType)) {
            return InventoryTransactionType.SALES_RETURN.getCode(); // 4
        }
        // 订单退款回库
        if ("ORDER_REFUND".equalsIgnoreCase(sourceType)) {
            return InventoryTransactionType.SALES_RETURN.getCode(); // 4
        }
        // 调拨入库
        if ("TRANSFER_IN".equalsIgnoreCase(sourceType)) {
            return InventoryTransactionType.TRANSFER_IN.getCode(); // 6
        }
        // 盘点盘盈
        if ("ADJUSTMENT".equalsIgnoreCase(sourceType)) {
            return InventoryTransactionType.ADJUSTMENT_IN.getCode(); // 7
        }
        // 默认采购入库
        return InventoryTransactionType.PURCHASE_IN.getCode(); // 1
    }
    
    /**
     * 出库逻辑（严格 FIFO，使用悲观锁，支持单位换算）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param quantity 扣减总数（主单位数量，已归一化）
     * @param unit 主单位（用于流水记录）
     * @param originalUnit 原始单位（业务发生时的单位，如：瓶、件，用于审计）
     * @param originalQuantity 原始数量（业务发生时的数量，用于审计）
     * @param createdBy 操作人ID
     * @param transactionType 流水类型（2-POS销售，3-线上销售，4-售后退回等）
     * @param referenceType 关联类型（如：ORDER-订单）
     * @param referenceId 关联ID
     * @return 扣减的批次详情列表
     */
    public List<BatchDeductionDetail> outbound(Long storeId, Long productId, BigDecimal quantity,
                                               String unit, String originalUnit, BigDecimal originalQuantity,
                                               Long createdBy, Integer transactionType, String referenceType, Long referenceId) {
        // 1. 使用悲观锁查询批次（FOR UPDATE）
        List<InventoryBatch> batches = batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId);
        
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("inventory-2001: 库存不足");
        }
        
        // 2. 检查总库存是否足够（使用 SUM 批次数量，不使用快照）
        BigDecimal totalAvailable = batches.stream()
                .map(InventoryBatch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalAvailable.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("inventory-2001: 库存不足，当前可用: " + totalAvailable + ", 需要: " + quantity);
        }
        
        // 获取操作前总库存数量
        BigDecimal beforeTotal = inventoryRepository.findByStoreAndProduct(storeId, productId)
                .map(inv -> inv.getQuantity())
                .orElse(BigDecimal.ZERO);
        
        // 3. 按 FIFO 顺序逐批扣减
        List<BatchDeductionDetail> deductionDetails = new ArrayList<>();
        BigDecimal remaining = quantity;
        
        for (InventoryBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal batchBeforeQty = batch.getQuantity();
            BigDecimal deductAmount;
            
            if (batchBeforeQty.compareTo(remaining) <= 0) {
                // 该批次全部扣减
                deductAmount = batchBeforeQty;
            } else {
                // 该批次部分扣减
                deductAmount = remaining;
            }
            
            // 扣减数量（会检查是否出现负数）
            try {
                batch.deduct(deductAmount);
            } catch (IllegalArgumentException e) {
                // 如果扣减后出现负数，抛出异常
                throw new IllegalArgumentException("inventory-2003: 批次扣减出现负数（数据异常）", e);
            }
            
            BigDecimal batchAfterQty = batch.getQuantity();
            
            // 归零容差：防止出现 0.00000001 这种幽灵库存
            if (batchAfterQty.compareTo(new BigDecimal("0.000001")) < 0) {
                batchAfterQty = BigDecimal.ZERO;
                batch.setQuantity(BigDecimal.ZERO);
                batch.setIsActive(0); // 归档
            }
            
            batchRepository.save(batch);
            
            // 记录流水（包含before/after和原始单位信息）
            BigDecimal currentAfterTotal = beforeTotal.subtract(deductAmount);
            recordTransaction(storeId, productId, batch.getId(), transactionType != null ? transactionType : 2, 
                            deductAmount.negate(), batch.getCostPrice(),
                            beforeTotal, currentAfterTotal,
                            batchBeforeQty, batchAfterQty,
                            unit, originalUnit, originalQuantity,
                            createdBy, referenceType, referenceId);
            beforeTotal = currentAfterTotal;
            
            // 记录扣减详情
            BatchDeductionDetail detail = new BatchDeductionDetail();
            detail.setBatchId(batch.getId());
            detail.setBatchNo(batch.getBatchNo());
            detail.setExpireDate(batch.getExpireDate());
            detail.setCostPrice(batch.getCostPrice());
            detail.setQuantity(deductAmount);
            deductionDetails.add(detail);
            
            remaining = remaining.subtract(deductAmount);
        }
        
        // 4. 触发强一致性同步
        refreshTotalStock(storeId, productId);
        
        return deductionDetails;
    }
    
    /**
     * 调拨逻辑（出库拆批 + 入库保价重建）
     * 
     * @param fromStoreId 调出门店ID
     * @param toStoreId 调入门店ID
     * @param productId 商品ID
     * @param quantity 调拨数量
     * @param createdBy 操作人ID
     * @param transferId 调拨单ID
     * @return 调拨详情
     */
    public TransferDetail transfer(Long fromStoreId, Long toStoreId, Long productId, 
                                  BigDecimal quantity, String unit, Long createdBy, Long transferId) {
        // 1. 调出：按 FIFO 出库，得到多个批次（调拨时原始单位=主单位）
        List<BatchDeductionDetail> deductionDetails = outbound(fromStoreId, productId, quantity, 
                                                             unit, unit, quantity, // 调拨时原始单位=主单位
                                                             createdBy, InventoryTransactionType.TRANSFER_OUT.getCode(), "TRANSFER_OUT", transferId);
        
        // 2. 调入：对每个拆分的批次生成对应的入库记录（保持原成本价）
        List<TransferBatchDetail> transferBatchDetails = new ArrayList<>();
        
        for (BatchDeductionDetail detail : deductionDetails) {
            // 在目标门店创建一个全新的批次（保持原成本价）
            Long toBatchId = inbound(toStoreId, productId, detail.getBatchNo(), 
                   detail.getQuantity(), detail.getCostPrice(), // 保持原成本价
                   null, detail.getExpireDate(),
                   "TRANSFER_IN", transferId, null, // sourceType, sourceId, sourceLineNo
                   unit, createdBy);
            
            // 记录调拨入库流水（已在 inbound 方法中记录，这里不需要重复记录）
            
            // 记录调拨明细（用于审计）
            TransferBatchDetail transferBatchDetail = new TransferBatchDetail();
            transferBatchDetail.setFromBatchId(detail.getBatchId());
            transferBatchDetail.setToBatchId(toBatchId);
            transferBatchDetail.setQuantity(detail.getQuantity());
            transferBatchDetail.setCostPrice(detail.getCostPrice());
            transferBatchDetails.add(transferBatchDetail);
        }
        
        TransferDetail transferDetail = new TransferDetail();
        transferDetail.setFromStoreId(fromStoreId);
        transferDetail.setToStoreId(toStoreId);
        transferDetail.setProductId(productId);
        transferDetail.setQuantity(quantity);
        transferDetail.setBatchDetails(deductionDetails);
        transferDetail.setTransferBatchDetails(transferBatchDetails);
        
        return transferDetail;
    }
    
    /**
     * 库存调整（盘点）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @param quantityDiff 调整数量（正数为盘盈，负数为盘亏）
     * @param type 调整类型：7-盘盈，8-盘亏
     * @param createdBy 操作人ID
     * @param remark 备注
     */
    public void adjust(Long storeId, Long productId, BigDecimal quantityDiff, 
                      Integer type, String unit, Long createdBy, String remark) {
        // 1. 获取当前总库存
        BigDecimal beforeQuantity = batchRepository.sumQuantityByStoreAndProduct(storeId, productId);
        if (beforeQuantity == null) {
            beforeQuantity = BigDecimal.ZERO;
        }
        
        // 2. 计算调整后库存
        BigDecimal afterQuantity = beforeQuantity.add(quantityDiff);
        if (afterQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("调整后库存不能为负数");
        }
        
        // 3. 如果调整数量为正（盘盈），创建新批次
        if (quantityDiff.compareTo(BigDecimal.ZERO) > 0) {
            // 盘盈：创建新批次，成本价使用0（盘盈不产生成本）
            BigDecimal avgCostPrice = BigDecimal.ZERO;
            LocalDateTime now = LocalDateTime.now();
            
            InventoryBatch batch = new InventoryBatch();
            batch.setStoreId(storeId);
            batch.setProductId(productId);
            batch.setBatchNo("ADJUST_" + System.currentTimeMillis());
            batch.setQuantity(quantityDiff);
            batch.setCostPrice(avgCostPrice);
            batch.setSourceType("ADJUSTMENT");
            batch.setSourceId(System.currentTimeMillis());
            batch.setSourceLineNo(1);
            batch.setIsActive(1);
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            
            batchRepository.save(batch);
            
            // 记录流水（盘盈时原始单位=主单位）
            recordTransaction(storeId, productId, batch.getId(), type, quantityDiff,
                    avgCostPrice, beforeQuantity, afterQuantity,
                    BigDecimal.ZERO, quantityDiff, unit, unit, quantityDiff, // 盘盈时原始单位=主单位
                    createdBy, "ADJUSTMENT", batch.getId());
        } else {
            // 盘亏：按FIFO扣减批次
            BigDecimal remaining = quantityDiff.abs();
            List<InventoryBatch> batches = batchRepository.findActiveBatchesForFIFO(storeId, productId);
            
            for (InventoryBatch batch : batches) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                
                BigDecimal batchQty = batch.getQuantity();
                BigDecimal deductQty = remaining.min(batchQty);
                
                BigDecimal batchBeforeQty = batchQty;
                BigDecimal batchAfterQty = batchQty.subtract(deductQty);
                
                batch.setQuantity(batchAfterQty);
                if (batchAfterQty.compareTo(BigDecimal.ZERO) == 0) {
                    batch.setIsActive(0);
                }
                batchRepository.save(batch);
                
                // 记录流水（盘亏时原始单位=主单位）
                recordTransaction(storeId, productId, batch.getId(), type, 
                        deductQty.negate(), batch.getCostPrice(),
                        beforeQuantity, afterQuantity,
                        batchBeforeQty, batchAfterQty, unit, unit, deductQty, // 盘亏时原始单位=主单位
                        createdBy, "ADJUSTMENT", batch.getId());
                
                remaining = remaining.subtract(deductQty);
            }
            
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("库存不足，无法完成盘亏调整");
            }
        }
        
        // 4. 同步总库存
        refreshTotalStock(storeId, productId);
    }
    
    /**
     * 强一致性同步（重算总库存）
     * 禁止直接对inventory表进行quantity = quantity + diff操作
     * 必须执行重算逻辑（write-after-read覆盖更新）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     */
    public void refreshTotalStock(Long storeId, Long productId) {
        // 1. 从批次表聚合计算总库存
        BigDecimal total = batchRepository.sumQuantityByStoreAndProduct(storeId, productId);
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        
        // 2. 更新或创建库存快照（覆盖更新，不使用增量）
        inventoryRepository.createIfNotExists(storeId, productId, total);
        inventoryRepository.updateSnapshot(storeId, productId, total);
    }
    
    /**
     * 记录库存流水（包含before/after数量和原始单位信息）
     */
    private void recordTransaction(Long storeId, Long productId, Long batchId,
                                  Integer transactionType, BigDecimal quantity,
                                  BigDecimal costPrice,
                                  BigDecimal beforeQuantity, BigDecimal afterQuantity,
                                  BigDecimal batchBeforeQty, BigDecimal batchAfterQty,
                                  String unit, String originalUnit, BigDecimal originalQuantity,
                                  Long createdBy, String referenceType, Long referenceId) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setStoreId(storeId);
        transaction.setProductId(productId);
        transaction.setBatchId(batchId);
        transaction.setTransactionType(transactionType);
        transaction.setQuantity(quantity);
        transaction.setCostPrice(costPrice);
        transaction.setBeforeQuantity(beforeQuantity);
        transaction.setAfterQuantity(afterQuantity);
        transaction.setBatchBeforeQty(batchBeforeQty);
        transaction.setBatchAfterQty(batchAfterQty);
        transaction.setUnit(unit);
        transaction.setOriginalUnit(originalUnit);
        transaction.setOriginalQuantity(originalQuantity);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        transaction.setCreatedBy(createdBy);
        transaction.setCreatedAt(LocalDateTime.now());
        
        transactionRepository.save(transaction);
    }
    
    /**
     * 批次扣减详情（用于调拨）
     */
    public static class BatchDeductionDetail {
        private Long batchId;
        private String batchNo;
        private LocalDate expireDate;
        private BigDecimal costPrice;
        private BigDecimal quantity;
        
        // Getters and Setters
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public LocalDate getExpireDate() { return expireDate; }
        public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    }
    
    /**
     * 调拨批次详情（用于审计）
     */
    public static class TransferBatchDetail {
        private Long fromBatchId;
        private Long toBatchId;
        private BigDecimal quantity;
        private BigDecimal costPrice;
        
        // Getters and Setters
        public Long getFromBatchId() { return fromBatchId; }
        public void setFromBatchId(Long fromBatchId) { this.fromBatchId = fromBatchId; }
        public Long getToBatchId() { return toBatchId; }
        public void setToBatchId(Long toBatchId) { this.toBatchId = toBatchId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    }
    
    /**
     * 调拨详情
     */
    public static class TransferDetail {
        private Long transferId;
        private String transferNo;
        private Long fromStoreId;
        private Long toStoreId;
        private Long productId;
        private BigDecimal quantity;
        private List<BatchDeductionDetail> batchDetails;
        private List<TransferBatchDetail> transferBatchDetails;
        
        // Getters and Setters
        public Long getTransferId() { return transferId; }
        public void setTransferId(Long transferId) { this.transferId = transferId; }
        public String getTransferNo() { return transferNo; }
        public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
        public Long getFromStoreId() { return fromStoreId; }
        public void setFromStoreId(Long fromStoreId) { this.fromStoreId = fromStoreId; }
        public Long getToStoreId() { return toStoreId; }
        public void setToStoreId(Long toStoreId) { this.toStoreId = toStoreId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public List<BatchDeductionDetail> getBatchDetails() { return batchDetails; }
        public void setBatchDetails(List<BatchDeductionDetail> batchDetails) { this.batchDetails = batchDetails; }
        public List<TransferBatchDetail> getTransferBatchDetails() { return transferBatchDetails; }
        public void setTransferBatchDetails(List<TransferBatchDetail> transferBatchDetails) { this.transferBatchDetails = transferBatchDetails; }
    }
}
