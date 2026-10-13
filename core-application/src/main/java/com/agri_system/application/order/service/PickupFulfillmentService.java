package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
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
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 核实已出库订单的提货批次。确认提货不再次移动或扣减库存。 */
@Service
@RequiredArgsConstructor
public class PickupFulfillmentService {

    private final OrderItemBatchAllocationRepository allocationRepository;
    private final OrderPickupRequestRepository pickupRequestRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryReceiptBatchRepository receiptBatchRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final ProductRepository productRepository;

    private final com.agri_system.domain.after_sales.repository.AfterSalesRepository afterSalesRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 货架无货或选择库房取货时创建后仓拣货请求。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createRequest(Long orderId, Long orderItemId, Long sourceLocationId) {
        OrderItem item = itemRepository.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("订单明细不存在: " + orderItemId));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        if (!orderId.equals(item.getOrderId())) {
            throw new IllegalArgumentException("订单明细与订单不匹配: " + orderItemId);
        }
        if (pickupRequestRepository.findPendingByOrderItemId(orderItemId).isPresent()) {
            throw new IllegalStateException("该订单明细已有未完成的库房取货请求");
        }
        List<OrderItemBatchAllocation> assigned = allocationRepository.findByOrderItemId(orderItemId);
        if (assigned.isEmpty() || assigned.stream().anyMatch(a -> a.getSourceMovementId() == null)) {
            throw new IllegalStateException("订单明细尚未实际出库，不能创建取货请求");
        }
        if (sourceLocationId != null && locationRepository.findById(sourceLocationId).isEmpty()) {
            throw new IllegalArgumentException("库存位置不存在: " + sourceLocationId);
        }
        Long resolvedSource = sourceLocationId != null
                ? sourceLocationId
                : resolveBackroomLocationId(order.getStoreId());
        BigDecimal assignedQuantity = assigned.stream().map(OrderItemBatchAllocation::getAllocatedBaseQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertCanHandOver(order);
        assertNotReturned(assigned);
        if (assigned.stream().anyMatch(a -> a.getPickupRequestId() != null)) {
            throw new IllegalStateException("出库分配已有取货请求，不能重复登记");
        }
        Long requestId = savePendingRequest(order.getStoreId(), orderId, item, assignedQuantity, resolvedSource, null);
        for (OrderItemBatchAllocation allocation : assigned) {
            allocation.setPickupRequestId(requestId);
            allocationRepository.save(allocation);
        }
        return requestId;
    }

    /**
     * 查询门店取货工作项（管理端取货待办页用）。
     *
     * <p>批量装配订单/商品/货位标签：固定 6 条查询，绝不按行回查。关联行缺失时标签字段为 null。</p>
     */
    public List<PickupWorkItemVO> listPickupRequests(Long storeId, String status) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId 不能为空");
        }
        String normalizedStatus = StringUtils.hasText(status)
                ? status.trim()
                : OrderPickupRequest.Status.PENDING.name();
        if (!OrderPickupRequest.Status.PENDING.name().equals(normalizedStatus)
                && !OrderPickupRequest.Status.FULFILLED.name().equals(normalizedStatus)) {
            throw new IllegalArgumentException("取货请求状态无效: " + status);
        }
        List<OrderPickupRequest> requests = pickupRequestRepository.findByStoreIdAndStatus(storeId, normalizedStatus);
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = requests.stream().map(OrderPickupRequest::getOrderId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> productIds = requests.stream().map(OrderPickupRequest::getProductId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, List<OrderItemBatchAllocation>> allocationsByRequest = allocationRepository.findByRequestIds(
                requests.stream().map(OrderPickupRequest::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItemBatchAllocation::getPickupRequestId));
        Map<Long, Order> ordersById = orderRepository.findByIds(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity(), (left, right) -> left));
        Map<Long, Product> productsById = productRepository.findByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left));
        Map<Long, InventoryLocation> locationsById = locationRepository.findByStoreId(storeId).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, Function.identity(), (left, right) -> left));
        Map<Long, InventoryReceiptBatch> batchesById = receiptBatchRepository.findByStore(storeId).stream()
                .collect(Collectors.toMap(InventoryReceiptBatch::getId, Function.identity(), (left, right) -> left));
        return requests.stream()
                .map(request -> toPickupWorkItem(request, ordersById, productsById, locationsById, batchesById, allocationsByRequest.getOrDefault(request.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private PickupWorkItemVO toPickupWorkItem(OrderPickupRequest request, Map<Long, Order> ordersById,
                                              Map<Long, Product> productsById,
                                              Map<Long, InventoryLocation> locationsById, Map<Long, InventoryReceiptBatch> batchesById, List<OrderItemBatchAllocation> allocations) {
        PickupWorkItemVO vo = new PickupWorkItemVO();
        vo.setId(request.getId());
        vo.setOrderId(request.getOrderId());
        Order order = request.getOrderId() == null ? null : ordersById.get(request.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());
        vo.setOrderItemId(request.getOrderItemId());
        vo.setProductId(request.getProductId());
        Product product = request.getProductId() == null ? null : productsById.get(request.getProductId());
        vo.setProductCode(product == null ? null : product.getCode());
        vo.setProductName(product == null ? null : product.getName());
        vo.setRequestedBaseQuantity(request.getRequestedBaseQuantity());
        vo.setSourceLocationId(request.getSourceLocationId());
        InventoryLocation location = request.getSourceLocationId() == null
                ? null : locationsById.get(request.getSourceLocationId());
        vo.setSourceLocationName(location == null ? null : location.getName());
        vo.setStatus(request.getStatus());
        vo.setCreatedAt(request.getCreatedAt());
        vo.setAllocations(allocations.stream().map(allocation -> {
            PickupWorkItemVO.AllocationVO line = new PickupWorkItemVO.AllocationVO();
            line.setId(allocation.getId()); line.setBatchId(allocation.getBatchId());
            InventoryReceiptBatch batch = batchesById.get(allocation.getBatchId());
            line.setInternalReceiptBatchNo(batch == null ? null : batch.getInternalReceiptBatchNo());
            line.setLocationId(allocation.getLocationId());
            InventoryLocation actualLocation = locationsById.get(allocation.getLocationId());
            line.setLocationName(actualLocation == null ? null : actualLocation.getName());
            line.setAllocatedBaseQuantity(allocation.getAllocatedBaseQuantity());
            return line;
        }).toList());
        return vo;
    }

    /** 核对已出库分配并确认交货，永不再次扣库存。 */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderItemBatchAllocation> fulfill(Long pickupRequestId, PickupFulfillmentCommand command,
                                                  Long operatorId) {
        OrderPickupRequest request = pickupRequestRepository.findByIdForUpdate(pickupRequestId)
                .orElseThrow(() -> new IllegalArgumentException("库房取货请求不存在"));
        if (!OrderPickupRequest.Status.PENDING.name().equals(request.getStatus())) throw new IllegalStateException("取货请求已完成");
        OrderItem item = itemRepository.findByIdForUpdate(request.getOrderItemId()).orElseThrow();
        if (!request.getOrderId().equals(item.getOrderId())) throw new IllegalStateException("取货请求与订单明细不一致");
        List<OrderItemBatchAllocation> assigned = allocationRepository.findByOrderItemId(request.getOrderItemId());
        if (assigned.isEmpty() || assigned.stream().anyMatch(a -> a.getSourceMovementId() == null)) throw new IllegalStateException("订单缺少实际出库记录");
        assertCanHandOver(orderRepository.findById(request.getOrderId()).orElseThrow(() -> new IllegalStateException("订单不存在")));
        assertNotReturned(assigned);
        if (assigned.stream().anyMatch(a -> !request.getId().equals(a.getPickupRequestId())
                || !request.getProductId().equals(a.getProductId()) || !request.getStoreId().equals(a.getStoreId()))) {
            throw new IllegalStateException("出库分配的请求归属不一致");
        }
        BigDecimal allocatedQuantity = assigned.stream().map(OrderItemBatchAllocation::getAllocatedBaseQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedQuantity.compareTo(request.getRequestedBaseQuantity()) != 0) throw new IllegalStateException("请求数量与实际出库记录不一致");
        if (command == null) throw new IllegalArgumentException("取货确认不能为空");
        boolean scanned = StringUtils.hasText(command.getScannedCode());
        if (command.getAllocationIds() != null) {
            List<Long> confirmed = command.getAllocationIds();
            Set<Long> confirmedIds = new java.util.HashSet<>(confirmed);
            Set<Long> assignedIds = assigned.stream().map(OrderItemBatchAllocation::getId).collect(Collectors.toSet());
            if (scanned || command.getBatchId() != null || command.getLocationId() != null
                    || confirmedIds.size() != confirmed.size() || !confirmedIds.equals(assignedIds)) {
                throw new IllegalArgumentException("请完整核对本请求全部出库分配，不允许遗漏、重复或混入其他请求");
            }
        } else {
            Long confirmedBatch = command.getBatchId();
            if (scanned) {
                confirmedBatch = receiptBatchRepository.findByInternalReceiptBatchNo(command.getScannedCode().trim())
                        .orElseThrow(() -> new IllegalArgumentException("扫描码不能解析为内部批次标签，仅表示SKU的条码不产生批次证据: " + command.getScannedCode())).getId();
            }
            if (confirmedBatch == null) throw new IllegalArgumentException("必须核对出库批次");
            final Long actualBatch = confirmedBatch;
            if (assigned.stream().anyMatch(a -> !actualBatch.equals(a.getBatchId())
                    || (command.getLocationId() != null && !command.getLocationId().equals(a.getLocationId())))) {
                throw new IllegalArgumentException("取货批次与已出库记录不一致，请逐项完整核对所有出库分配");
            }
        }
        for (OrderItemBatchAllocation allocation : assigned) {
            allocation.setEvidenceLevel(scanned ? "SCANNED" : "FULFILLMENT_CONFIRMED");
            allocationRepository.save(allocation);
        }
        request.setStatus(OrderPickupRequest.Status.FULFILLED.name());
        request.setFulfilledAt(LocalDateTime.now()); request.setFulfilledBy(operatorId);
        pickupRequestRepository.save(request);
        return assigned;
    }

    /** 锁原订单行后再用 current read 查已回库流水，不能受旧的 REPEATABLE READ 快照影响。 */
    private void assertNotReturned(List<OrderItemBatchAllocation> assigned) {
        List<Long> ids = assigned.stream().map(OrderItemBatchAllocation::getId).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Long> returned = jdbc.queryForList("SELECT id FROM inventory_movement WHERE movement_type='RETURN' "
                + "AND reference_type='SALE_ALLOCATION' AND reference_id IN (" + placeholders + ") FOR UPDATE",
                Long.class, ids.toArray());
        if (!returned.isEmpty()) throw new IllegalStateException("原销售商品已经回库，不能再次交货");
    }

    private void assertCanHandOver(Order order) {
        if (order.getOrderStatus() == null || !Set.of(2, 3, 4, 5, 6).contains(order.getOrderStatus())) {
            throw new IllegalStateException("订单未支付、已取消或退款，不能继续取货交付");
        }
        if (afterSalesRepository.findByOrderId(order.getId()).stream()
                .anyMatch(afterSales -> !Integer.valueOf(com.agri_system.domain.after_sales.model.AfterSales.STATUS_REJECTED)
                        .equals(afterSales.getStatus()))) {
            throw new IllegalStateException("订单已有退货或售后申请，不能继续取货交付");
        }
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

    private Long resolveBackroomLocationId(Long storeId) {
        return locationRepository.findEnabledByStoreAndType(storeId,
                        InventoryLocation.LocationType.STORE_BACKROOM.name())
                .map(InventoryLocation::getId)
                .orElse(null);
    }

}
