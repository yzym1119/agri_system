package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.order.enums.BatchEvidenceLevel;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.model.OrderPickupRequest;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPickupRequestRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 库房取货与销售批次证据应用服务（零售事实链 Task5）。
 *
 * <p>POS 结账不要求收银员选择批次：货架可立即交付时按配置规则落 {@code RULE_ALLOCATED}
 * （货架单批次）或 {@code UNKNOWN}（混批），均不阻断结账；货架无货或操作员选择库房取货时
 * 创建取货请求，拣货员扫描内部批次/货位标签得到 {@code SCANNED}，或显式确认批次与货位得到
 * {@code FULFILLMENT_CONFIRMED}，并以实际拣货移动（PICK）回填真实批次成本与移动事实。</p>
 *
 * <p>所有新链路写入以 {@link BatchBalanceService#isBatchLocationEnabled(Long, Long)} 为闸门：
 * 未激活的 SKU 不产生任何归属/取货事实，调用方继续 legacy 出库路径。</p>
 */
@Service
@RequiredArgsConstructor
public class PickupFulfillmentService {

    /** 货架单批次自动归属规则名。 */
    static final String RULE_FLOOR_SINGLE_BATCH = "FLOOR_SINGLE_BATCH";
    /** 扫描内部批次标签的归属规则名。 */
    static final String RULE_PICK_SCAN = "PICK_SCAN";
    /** 显式确认批次与货位的归属规则名。 */
    static final String RULE_PICK_CONFIRM = "PICK_CONFIRM";
    /** 归属规则版本；规则调整时递增以便对账追溯。 */
    static final String ALLOCATION_RULE_VERSION = "R1";

    private final OrderItemBatchAllocationRepository allocationRepository;
    private final OrderPickupRequestRepository pickupRequestRepository;
    private final BatchBalanceService batchBalanceService;
    private final ProductUnitService productUnitService;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchBalanceRepository batchBalanceRepository;
    private final InventoryReceiptBatchRepository receiptBatchRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;

    /**
     * POS 直接销售时的批次证据归属。
     *
     * @return true 表示新链路已处理（结账不再走 legacy outbound）；false 表示 SKU 未激活，调用方保持现状
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean recordDirectSaleEvidence(Order order, OrderItem item, boolean warehousePickup, Long operatorId) {
        if (order == null || item == null || item.getProductId() == null || order.getStoreId() == null
                || !batchBalanceService.isBatchLocationEnabled(order.getStoreId(), item.getProductId())) {
            return false;
        }
        BigDecimal baseQuantity = toBaseQuantity(item);
        Optional<InventoryLocation> floor = salesFloor(order.getStoreId());
        List<InventoryBatchBalance> floorBalances = floor
                .map(location -> safeBalances(
                        batchBalanceRepository.findByLocationAndProduct(location.getId(), item.getProductId())))
                .orElseGet(List::of);
        BigDecimal floorAvailable = floorBalances.stream()
                .map(InventoryBatchBalance::availableBaseQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean floorCanFulfill = !warehousePickup && floor.isPresent()
                && floorAvailable.compareTo(baseQuantity) >= 0;

        if (floorCanFulfill && floorBalances.size() == 1) {
            InventoryBatchBalance single = floorBalances.get(0);
            BigDecimal unitCost = receiptBatchRepository.findById(single.getBatchId())
                    .map(InventoryReceiptBatch::getBaseUnitCost)
                    .orElse(null);
            OrderItemBatchAllocation allocation = baseAllocation(order, item, operatorId, baseQuantity);
            allocation.setBatchId(single.getBatchId());
            allocation.setLocationId(floor.get().getId());
            allocation.setBatchUnitCost(unitCost);
            allocation.setAllocatedCost(unitCost == null ? null : unitCost.multiply(baseQuantity));
            allocation.setEvidenceLevel(BatchEvidenceLevel.RULE_ALLOCATED.name());
            allocation.setAllocationRule(RULE_FLOOR_SINGLE_BATCH);
            allocation.setAllocationRuleVersion(ALLOCATION_RULE_VERSION);
            allocationRepository.save(allocation);
            return true;
        }

        // 货架混批或货架无货/选择库房取货：结账绝不阻断，落 UNKNOWN 证据
        OrderItemBatchAllocation allocation = baseAllocation(order, item, operatorId, baseQuantity);
        allocation.setLocationId(floorCanFulfill ? floor.get().getId() : null);
        allocation.setEvidenceLevel(BatchEvidenceLevel.UNKNOWN.name());
        allocation = allocationRepository.save(allocation);

        if (warehousePickup || !floorCanFulfill) {
            Long requestId = savePendingRequest(order.getStoreId(), order.getId(), item, baseQuantity,
                    resolveBackroomLocationId(order.getStoreId()), operatorId);
            allocation.setPickupRequestId(requestId);
            allocationRepository.save(allocation);
        }
        return true;
    }

    /** 货架无货或选择库房取货时创建后仓拣货请求。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createRequest(Long orderId, Long orderItemId, Long sourceLocationId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        OrderItem item = itemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("订单明细不存在: " + orderItemId));
        if (!orderId.equals(item.getOrderId())) {
            throw new IllegalArgumentException("订单明细与订单不匹配: " + orderItemId);
        }
        if (order.getStoreId() == null
                || !batchBalanceService.isBatchLocationEnabled(order.getStoreId(), item.getProductId())) {
            throw new IllegalStateException("该SKU未启用批次货位余额，不能创建库房取货请求");
        }
        if (sourceLocationId != null && locationRepository.findById(sourceLocationId).isEmpty()) {
            throw new IllegalArgumentException("库存位置不存在: " + sourceLocationId);
        }
        Long resolvedSource = sourceLocationId != null
                ? sourceLocationId
                : resolveBackroomLocationId(order.getStoreId());
        return savePendingRequest(order.getStoreId(), orderId, item, toBaseQuantity(item), resolvedSource, null);
    }

    /**
     * 拣货完成：扫描内部批次标签得到 SCANNED，显式确认批次与货位得到 FULFILLMENT_CONFIRMED，
     * 通过 PICK 移动回填真实批次成本与移动事实。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderItemBatchAllocation> fulfill(Long pickupRequestId, PickupFulfillmentCommand command,
                                                  Long operatorId) {
        OrderPickupRequest request = pickupRequestRepository.findById(pickupRequestId)
                .orElseThrow(() -> new IllegalArgumentException("库房取货请求不存在: " + pickupRequestId));
        if (!OrderPickupRequest.Status.PENDING.name().equals(request.getStatus())) {
            throw new IllegalStateException("库房取货请求状态不允许拣货: " + request.getStatus());
        }
        if (command == null) {
            throw new IllegalArgumentException("拣货命令不能为空");
        }

        Long sourceLocationId;
        String evidenceLevel;
        String allocationRule;
        InventoryReceiptBatch batch;
        if (StringUtils.hasText(command.getScannedCode())) {
            // 仅内部批次标签可解析；仅表示 SKU 的 EAN/GS1 不产生批次证据
            batch = receiptBatchRepository.findByInternalReceiptBatchNo(command.getScannedCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "扫描码不能解析为内部批次标签，仅表示SKU的条码不产生批次证据: " + command.getScannedCode()));
            sourceLocationId = request.getSourceLocationId() != null
                    ? request.getSourceLocationId()
                    : resolveBackroomLocationId(request.getStoreId());
            evidenceLevel = BatchEvidenceLevel.SCANNED.name();
            allocationRule = RULE_PICK_SCAN;
        } else {
            if (command.getBatchId() == null) {
                throw new IllegalArgumentException("必须扫描内部批次标签或明确指定批次与货位");
            }
            batch = receiptBatchRepository.findById(command.getBatchId())
                    .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + command.getBatchId()));
            sourceLocationId = command.getLocationId() != null
                    ? command.getLocationId()
                    : request.getSourceLocationId();
            if (sourceLocationId == null) {
                throw new IllegalArgumentException("必须明确指定拣货货位");
            }
            locationRepository.findById(sourceLocationId)
                    .orElseThrow(() -> new IllegalArgumentException("库存位置不存在: " + sourceLocationId));
            evidenceLevel = BatchEvidenceLevel.FULFILLMENT_CONFIRMED.name();
            allocationRule = RULE_PICK_CONFIRM;
        }
        if (!batch.getProductId().equals(request.getProductId())) {
            throw new IllegalArgumentException("批次商品与订单明细不一致，批次ID: " + batch.getId());
        }

        InventoryLocation floor = salesFloor(request.getStoreId())
                .orElseThrow(() -> new IllegalStateException("门店未配置销售货位，无法完成库房取货"));
        BigDecimal baseQuantity = request.getRequestedBaseQuantity();
        BigDecimal unitCost = batch.getBaseUnitCost();
        InventoryMovement movement = batchBalanceService.move(new BatchBalanceService.MoveRequest(
                batch.getId(), sourceLocationId, floor.getId(), BatchBalanceService.MovementPurpose.PICK,
                null, batch.getOriginalUnitId(), batch.getOriginalUnitName(),
                pickedOriginalQuantity(batch, baseQuantity), baseQuantity, unitCost,
                "PICKUP_REQUEST", pickupRequestId, operatorId, "库房取货履约", null,
                "PICK:" + pickupRequestId + ":" + batch.getId() + ":" + sourceLocationId));

        List<OrderItemBatchAllocation> existing = allocationRepository.findByOrderItemId(request.getOrderItemId());
        OrderItemBatchAllocation allocation = existing.isEmpty() ? new OrderItemBatchAllocation() : existing.get(0);
        if (existing.isEmpty()) {
            allocation.setOrderItemId(request.getOrderItemId());
            allocation.setProductId(request.getProductId());
            allocation.setStoreId(request.getStoreId());
            allocation.setCreatedAt(LocalDateTime.now());
            allocation.setCreatedBy(operatorId);
        }
        allocation.setBatchId(batch.getId());
        allocation.setLocationId(floor.getId());
        allocation.setPickupRequestId(pickupRequestId);
        allocation.setAllocatedBaseQuantity(baseQuantity);
        allocation.setBatchUnitCost(unitCost);
        allocation.setAllocatedCost(unitCost == null ? null : unitCost.multiply(baseQuantity));
        allocation.setEvidenceLevel(evidenceLevel);
        allocation.setAllocationRule(allocationRule);
        allocation.setAllocationRuleVersion(ALLOCATION_RULE_VERSION);
        allocation.setSourceMovementId(movement.getId());
        allocationRepository.save(allocation);

        request.setStatus(OrderPickupRequest.Status.FULFILLED.name());
        request.setFulfilledAt(LocalDateTime.now());
        request.setFulfilledBy(operatorId);
        pickupRequestRepository.save(request);

        return List.of(allocation);
    }

    private Long savePendingRequest(Long storeId, Long orderId, OrderItem item, BigDecimal baseQuantity,
                                    Long sourceLocationId, Long operatorId) {
        OrderPickupRequest request = new OrderPickupRequest();
        request.setOrderId(orderId);
        request.setOrderItemId(item.getId());
        request.setStoreId(storeId);
        request.setProductId(item.getProductId());
        request.setRequestedBaseQuantity(baseQuantity);
        request.setSourceLocationId(sourceLocationId);
        request.setStatus(OrderPickupRequest.Status.PENDING.name());
        request.setCreatedAt(LocalDateTime.now());
        request.setCreatedBy(operatorId);
        return pickupRequestRepository.save(request).getId();
    }

    private OrderItemBatchAllocation baseAllocation(Order order, OrderItem item, Long operatorId,
                                                    BigDecimal baseQuantity) {
        OrderItemBatchAllocation allocation = new OrderItemBatchAllocation();
        allocation.setOrderItemId(item.getId());
        allocation.setProductId(item.getProductId());
        allocation.setStoreId(order.getStoreId());
        allocation.setAllocatedBaseQuantity(baseQuantity);
        allocation.setCreatedAt(LocalDateTime.now());
        allocation.setCreatedBy(operatorId);
        return allocation;
    }

    private BigDecimal toBaseQuantity(OrderItem item) {
        UnitConversionSnapshot snapshot = productUnitService.toBaseQuantity(
                item.getProductId(), item.getUnit(), item.getQuantity());
        return snapshot.baseQuantity();
    }

    private BigDecimal pickedOriginalQuantity(InventoryReceiptBatch batch, BigDecimal baseQuantity) {
        if (batch.getOriginalQuantity() == null || batch.getBaseQuantity() == null
                || batch.getBaseQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return baseQuantity;
        }
        return batch.getOriginalQuantity().multiply(baseQuantity)
                .divide(batch.getBaseQuantity(), 8, RoundingMode.HALF_UP);
    }

    private Optional<InventoryLocation> salesFloor(Long storeId) {
        return locationRepository.findEnabledByStoreAndType(storeId,
                InventoryLocation.LocationType.SALES_FLOOR.name());
    }

    private Long resolveBackroomLocationId(Long storeId) {
        return locationRepository.findEnabledByStoreAndType(storeId,
                        InventoryLocation.LocationType.STORE_BACKROOM.name())
                .map(InventoryLocation::getId)
                .orElse(null);
    }

    private List<InventoryBatchBalance> safeBalances(List<InventoryBatchBalance> balances) {
        return balances == null ? List.of() : balances;
    }
}
