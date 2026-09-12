package com.agri_system.domain.inventory.service;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;
import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBalanceFeatureStatusRepository;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryMovementRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 新单位模型下的批次位置余额领域服务。
 * 调用方必须在一个事务中调用；基础设施对源余额执行 SELECT FOR UPDATE。
 */
@RequiredArgsConstructor
public class BatchBalanceService {
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryReceiptBatchRepository receiptBatchRepository;
    private final InventoryBalanceFeatureStatusRepository featureStatusRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryMovement move(MoveRequest request) {
        validateRequest(request);
        InventoryLocation from = request.fromLocationId() == null ? null : requireLocation(request.fromLocationId());
        InventoryLocation to = request.toLocationId() == null ? null : requireLocation(request.toLocationId());
        Long storeId = resolveStoreId(from, to);

        InventoryBatchBalance source = null;
        InventoryBatchBalance destination = null;
        if (from != null) {
            source = balanceRepository.findByBatchAndLocationForUpdate(request.batchId(), from.getId())
                    .orElseThrow(() -> new IllegalArgumentException("源货位不存在该批次余额"));
            assertEnabled(source.getStoreId(), source.getProductId());
            if (!source.getStoreId().equals(from.getStoreId())) {
                throw new IllegalArgumentException("批次余额与源货位门店不一致");
            }
            source.deductAvailable(request.baseQuantity());
        }
        InventoryBatchBalance lockedSource = source;
        if (to != null) {
            destination = balanceRepository.findByBatchAndLocationForUpdate(request.batchId(), to.getId())
                    .orElseGet(() -> newBalance(request.batchId(), lockedSource, to));
            assertEnabled(destination.getStoreId(), destination.getProductId());
            if (!destination.getStoreId().equals(to.getStoreId())) {
                throw new IllegalArgumentException("批次余额与目的货位门店不一致");
            }
            destination.increase(request.baseQuantity());
        }

        if (source != null && destination != null && !source.getStoreId().equals(destination.getStoreId())) {
            throw new IllegalArgumentException("跨店调拨必须拆为同一transferId的出库和入库事实");
        }
        InventoryBatchBalance factBalance = source != null ? source : destination;
        if (factBalance == null) {
            throw new IllegalArgumentException("移动必须指定源货位或目的货位");
        }
        if (!factBalance.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("移动门店不一致");
        }
        if (source != null) {
            balanceRepository.save(source);
        }
        if (destination != null && destination != source) {
            balanceRepository.save(destination);
        }

        InventoryMovement movement = movement(request, factBalance, from, to);
        movementRepository.append(movement);
        projectInventory(factBalance.getStoreId(), factBalance.getProductId());
        return movement;
    }

    /**
     * 跨店调拨必须明确两端收货批次：出库和入库分别保存同一 transferId 的事实。
     * 目的批次由调用方在收货链路中建立，以保留供应商批号、日期和单位成本谱系。
     */
    public TransferResult transfer(TransferRequest request) {
        if (request == null || blank(request.transferId())) throw new IllegalArgumentException("跨店调拨必须提供transferId");
        InventoryReceiptBatch sourceBatch = receiptBatchRepository.findById(request.sourceBatchId())
                .orElseThrow(() -> new IllegalArgumentException("调出收货批次不存在"));
        InventoryReceiptBatch destinationBatch = receiptBatchRepository.findById(request.destinationBatchId())
                .orElseThrow(() -> new IllegalArgumentException("调入收货批次不存在"));
        if (sourceBatch.getStoreId().equals(destinationBatch.getStoreId())) {
            throw new IllegalArgumentException("跨店调拨的两端批次必须属于不同门店");
        }
        if (!sourceBatch.getProductId().equals(destinationBatch.getProductId())
                || !equals(sourceBatch.getSupplierBatchNo(), destinationBatch.getSupplierBatchNo())
                || sourceBatch.getBaseUnitCost().compareTo(destinationBatch.getBaseUnitCost()) != 0) {
            throw new IllegalArgumentException("跨店调拨批次必须保持SKU、供应商批号和单位成本谱系");
        }
        MoveRequest outbound = new MoveRequest(sourceBatch.getId(), request.fromLocationId(), null, MovementPurpose.TRANSFER,
                request.transferId(), request.originalUnitId(), request.originalUnitName(), request.originalQuantity(),
                request.baseQuantity(), sourceBatch.getBaseUnitCost(), request.referenceType(), request.referenceId(),
                request.operatorId(), request.remark(), null, "TRANSFER:" + request.transferId() + ":OUT");
        InventoryMovement outboundMovement = move(outbound);
        MoveRequest inbound = new MoveRequest(destinationBatch.getId(), null, request.toLocationId(), MovementPurpose.TRANSFER,
                request.transferId(), request.originalUnitId(), request.originalUnitName(), request.originalQuantity(),
                request.baseQuantity(), destinationBatch.getBaseUnitCost(), request.referenceType(), request.referenceId(),
                request.operatorId(), request.remark(), null, "TRANSFER:" + request.transferId() + ":IN");
        return new TransferResult(outboundMovement, move(inbound));
    }

    /** 收货事实已持久化后调用；未明确切换的 SKU 不创建新余额。 */
    public void increaseFromReceipt(Long receiptId, InventoryReceiptBatch receiptBatch) {
        if (receiptBatch == null || !isEnabled(receiptBatch.getStoreId(), receiptBatch.getProductId())) {
            return;
        }
        InventoryLocation location = locationRepository
                .findEnabledByStoreAndType(receiptBatch.getStoreId(), InventoryLocation.LocationType.STORE_BACKROOM.name())
                .orElseGet(() -> createBackroom(receiptBatch.getStoreId()));
        InventoryBatchBalance balance = balanceRepository.findByBatchAndLocationForUpdate(receiptBatch.getId(), location.getId())
                .orElseGet(() -> receiptBalance(receiptBatch, location.getId()));
        balance.increase(requirePositive(receiptBatch.getBaseQuantity(), "收货基准数量"));
        balanceRepository.save(balance);
        projectInventory(receiptBatch.getStoreId(), receiptBatch.getProductId());
    }

    public BigDecimal availableBaseQuantity(Long locationId, Long productId) {
        return zero(balanceRepository.sumAvailableBaseQuantity(locationId, productId));
    }

    private void validateRequest(MoveRequest request) {
        if (request == null || request.batchId() == null) throw new IllegalArgumentException("批次不能为空");
        requirePositive(request.originalQuantity(), "原始数量");
        requirePositive(request.baseQuantity(), "基准数量");
        requirePositive(request.unitCost(), "单位成本");
        if (blank(request.originalUnitName())) throw new IllegalArgumentException("原始单位不能为空");
        if (request.originalUnitId() == null) throw new IllegalArgumentException("原始单位ID不能为空");
        if (request.purpose() == null) throw new IllegalArgumentException("移动用途不能为空");
        if (request.fromLocationId() != null && request.fromLocationId().equals(request.toLocationId())) {
            throw new IllegalArgumentException("源货位和目的货位不能相同");
        }
        // 按用途约束端点,杜绝用补货/拣货单边凭空增删库存(Critical 1)
        switch (request.purpose()) {
            case REPLENISHMENT, PICK -> {
                if (request.fromLocationId() == null || request.toLocationId() == null) {
                    throw new IllegalArgumentException("补货和拣货必须同时指定源货位和目的货位");
                }
            }
            case TRANSFER -> {
                if (blank(request.transferId())) throw new IllegalArgumentException("跨店调拨必须提供transferId");
                if (request.fromLocationId() != null && request.toLocationId() != null) {
                    throw new IllegalArgumentException("跨店调拨必须拆成同一transferId的出库和入库两条事实,不能在单次move同时指定源和目的");
                }
                if (request.fromLocationId() == null && request.toLocationId() == null) {
                    throw new IllegalArgumentException("跨店调拨必须指定源货位或目的货位");
                }
            }
            case COUNT_ADJUSTMENT, RETURN -> {
                if (request.direction() == null) throw new IllegalArgumentException("盘点调整或退货必须指定方向IN/OUT");
                if (blank(request.remark())) throw new IllegalArgumentException("盘点调整或退货必须填写审计说明");
                if (blank(request.referenceType()) || request.referenceId() == null
                        || "RECEIPT".equalsIgnoreCase(request.referenceType())) {
                    throw new IllegalArgumentException("盘点调整或退货必须关联非收货业务单据");
                }
                if (blank(request.idempotencyKey())) {
                    throw new IllegalArgumentException("盘点调整或退货必须提供幂等键防止重复提交");
                }
                if (request.direction() == Direction.IN) {
                    if (request.toLocationId() == null) throw new IllegalArgumentException("入库方向必须指定目的货位");
                    if (request.fromLocationId() != null) throw new IllegalArgumentException("入库方向不能同时指定源货位");
                } else {
                    if (request.fromLocationId() == null) throw new IllegalArgumentException("出库方向必须指定源货位");
                    if (request.toLocationId() != null) throw new IllegalArgumentException("出库方向不能同时指定目的货位");
                }
            }
        }
    }

    private InventoryLocation requireLocation(Long id) {
        InventoryLocation location = locationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("库存位置不存在"));
        if (!Integer.valueOf(1).equals(location.getStatus())) throw new IllegalArgumentException("库存位置已停用");
        try {
            InventoryLocation.LocationType.valueOf(location.getLocationType());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("库存位置类型无效");
        }
        return location;
    }

    private InventoryBatchBalance newBalance(Long batchId, InventoryBatchBalance source, InventoryLocation destination) {
        if (source == null) {
            InventoryReceiptBatch receiptBatch = receiptBatchRepository.findById(batchId)
                    .orElseThrow(() -> new IllegalArgumentException("目的货位不存在该批次余额；入库必须使用目标门店收货批次"));
            if (!receiptBatch.getStoreId().equals(destination.getStoreId())) {
                throw new IllegalArgumentException("目的收货批次与目的货位门店不一致");
            }
            InventoryBatchBalance balance = receiptBalance(receiptBatch, destination.getId());
            return balance;
        }
        InventoryBatchBalance balance = new InventoryBatchBalance();
        balance.setBatchId(batchId);
        balance.setProductId(source.getProductId());
        balance.setStoreId(destination.getStoreId());
        balance.setLocationId(destination.getId());
        balance.setBaseQuantity(BigDecimal.ZERO);
        balance.setReservedBaseQuantity(BigDecimal.ZERO);
        balance.setVersion(0);
        return balance;
    }

    private InventoryBatchBalance receiptBalance(InventoryReceiptBatch batch, Long locationId) {
        InventoryBatchBalance balance = new InventoryBatchBalance();
        balance.setBatchId(batch.getId());
        balance.setProductId(batch.getProductId());
        balance.setStoreId(batch.getStoreId());
        balance.setLocationId(locationId);
        balance.setBaseQuantity(BigDecimal.ZERO);
        balance.setReservedBaseQuantity(BigDecimal.ZERO);
        balance.setVersion(0);
        return balance;
    }

    private InventoryLocation createBackroom(Long storeId) {
        InventoryLocation location = new InventoryLocation();
        location.setStoreId(storeId);
        location.setLocationType(InventoryLocation.LocationType.STORE_BACKROOM.name());
        location.setName("门店后仓");
        location.setStatus(1);
        location.setCreatedAt(LocalDateTime.now());
        location.setUpdatedAt(location.getCreatedAt());
        locationRepository.save(location);
        return location;
    }

    private InventoryMovement movement(MoveRequest request, InventoryBatchBalance balance,
                                       InventoryLocation from, InventoryLocation to) {
        LocalDateTime now = LocalDateTime.now();
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementNo("MOVE-" + UUID.randomUUID());
        movement.setIdempotencyKey(request.idempotencyKey());
        movement.setBatchId(request.batchId());
        movement.setProductId(balance.getProductId());
        movement.setStoreId(balance.getStoreId());
        movement.setFromLocationId(from == null ? null : from.getId());
        movement.setToLocationId(to == null ? null : to.getId());
        movement.setMovementType(request.purpose().name());
        movement.setTransferId(request.transferId());
        movement.setOriginalUnitId(request.originalUnitId());
        movement.setOriginalUnitName(request.originalUnitName());
        movement.setOriginalQuantity(request.originalQuantity());
        movement.setBaseQuantity(request.baseQuantity());
        movement.setUnitCost(request.unitCost());
        movement.setReferenceType(request.referenceType());
        movement.setReferenceId(request.referenceId());
        movement.setOperatorId(request.operatorId());
        movement.setRemark(request.remark());
        movement.setOccurredAt(now);
        movement.setCreatedAt(now);
        return movement;
    }

    private void projectInventory(Long storeId, Long productId) {
        inventoryRepository.createIfNotExists(storeId, productId, BigDecimal.ZERO);
        // 悲观锁串行化同门店同商品的并发投影,避免无锁 SUM 互相覆盖(Important 5)
        inventoryRepository.findByStoreAndProductForUpdate(storeId, productId);
        inventoryRepository.updateSnapshot(storeId, productId,
                zero(balanceRepository.sumBaseQuantityByStoreAndProduct(storeId, productId)));
    }

    private Long resolveStoreId(InventoryLocation from, InventoryLocation to) {
        if (from != null && to != null && !from.getStoreId().equals(to.getStoreId())) {
            throw new IllegalArgumentException("跨店调拨必须拆为同一transferId的出库和入库事实");
        }
        return from != null ? from.getStoreId() : to.getStoreId();
    }

    private void assertEnabled(Long storeId, Long productId) {
        if (!isEnabled(storeId, productId)) {
            throw new IllegalStateException("SKU尚未切换到批次货位余额模式");
        }
    }

    private boolean isEnabled(Long storeId, Long productId) {
        return featureStatusRepository.findByStoreAndProduct(storeId, productId)
                .map(InventoryBalanceFeatureStatus::getBalanceMode)
                .filter(InventoryBalanceFeatureStatus.BATCH_LOCATION::equals)
                .isPresent();
    }

    private BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(name + "必须大于0");
        return value;
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean equals(Object left, Object right) { return java.util.Objects.equals(left, right); }

    public enum MovementPurpose { REPLENISHMENT, PICK, TRANSFER, COUNT_ADJUSTMENT, RETURN }

    /** 单边增减方向:仅 COUNT_ADJUSTMENT/RETURN 使用。IN=盘盈/退货入库(只增目的),OUT=盘亏/报损出库(只扣源)。 */
    public enum Direction { IN, OUT }

    public record MoveRequest(Long batchId, Long fromLocationId, Long toLocationId, MovementPurpose purpose,
                              String transferId, Long originalUnitId, String originalUnitName,
                              BigDecimal originalQuantity, BigDecimal baseQuantity, BigDecimal unitCost,
                              String referenceType, Long referenceId, Long operatorId, String remark,
                              Direction direction, String idempotencyKey) { }
    public record TransferRequest(Long sourceBatchId, Long destinationBatchId, Long fromLocationId, Long toLocationId,
                                  String transferId, Long originalUnitId, String originalUnitName,
                                  BigDecimal originalQuantity, BigDecimal baseQuantity,
                                  String referenceType, Long referenceId, Long operatorId, String remark) { }
    public record TransferResult(InventoryMovement outbound, InventoryMovement inbound) { }
}
