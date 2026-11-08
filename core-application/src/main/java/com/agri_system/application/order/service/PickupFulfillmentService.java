package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.model.OrderPickupRequest;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPickupRequestRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.query.PickupEvidenceQuery;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final PickupEvidenceQuery pickupEvidence;
    private final com.agri_system.domain.store.repository.StaffRepository staffRepository;

    /** 货架无货或选择库房取货时创建后仓拣货请求。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long createRequest(Long orderId, Long orderItemId, Long sourceLocationId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        OrderItem item = itemRepository.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("订单明细不存在: " + orderItemId));
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
        if (assigned.stream().anyMatch(a -> !order.getStoreId().equals(a.getStoreId())
                || !item.getProductId().equals(a.getProductId()) || !item.getId().equals(a.getOrderItemId()))) {
            throw new IllegalStateException("出库分配与订单明细归属不一致");
        }
        BigDecimal assignedQuantity = assigned.stream().map(OrderItemBatchAllocation::getAllocatedBaseQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertCanHandOver(order);
        RemainingEvidence evidence = remainingEvidence(assigned, Map.of(orderItemId, orderId));
        requireRemaining(assigned, evidence);
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
     * <p>批量装配标签与出退库证据：包含请求查询固定最多 7 条查询，绝不按行回查。关联行缺失时标签字段为 null。</p>
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
        return assemble(storeId, requests);
    }

    /**
     * 手机整单清单：按原订单集合装配与工作项完全相同的事实。
     *
     * <p>只换取数范围，不复制余量、批次或暂停判断；订单集合为空时不发起查询。</p>
     */
    public List<PickupWorkItemVO> listPickupRequestsForOrders(Long storeId, List<Long> orderIds) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId 不能为空");
        }
        List<OrderPickupRequest> requests = pickupRequestRepository.findByStoreIdAndOrderIds(storeId, orderIds);
        if (requests.isEmpty()) {
            return List.of();
        }
        return assemble(storeId, requests);
    }

    private List<PickupWorkItemVO> assemble(Long storeId, List<OrderPickupRequest> requests) {
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
        RemainingEvidence evidence = remainingEvidence(allocationsByRequest.values().stream().flatMap(List::stream).toList(),
                requests.stream().collect(Collectors.toMap(OrderPickupRequest::getOrderItemId, OrderPickupRequest::getOrderId, (left, right) -> left)));
        return requests.stream()
                .map(request -> toPickupWorkItem(request, ordersById, productsById, locationsById, batchesById,
                        allocationsByRequest.getOrDefault(request.getId(), List.of()), evidence))
                .collect(Collectors.toList());
    }

    private PickupWorkItemVO toPickupWorkItem(OrderPickupRequest request, Map<Long, Order> ordersById,
                                              Map<Long, Product> productsById,
                                              Map<Long, InventoryLocation> locationsById, Map<Long, InventoryReceiptBatch> batchesById, List<OrderItemBatchAllocation> allocations, RemainingEvidence evidence) {
        PickupWorkItemVO vo = new PickupWorkItemVO();
        vo.setStoreId(request.getStoreId());
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
        vo.setFulfilledAt(request.getFulfilledAt());
        // 完成后的当前未退数量不是当时实际交货数量，历史页只保留原申请及确认事实。
        if (OrderPickupRequest.Status.PENDING.name().equals(request.getStatus())) {
            String blocked = handoverBlockReason(order);
            if (blocked == null && evidence.processingOrderIds().contains(request.getOrderId())) blocked = "订单有处理中售后，暂缓取货交付";
            if (blocked == null && !request.getStoreId().equals(order.getStoreId())) blocked = "请求与订单门店不一致";
            if (blocked == null && !validRequestAllocations(request, allocations)) blocked = "请求与实际出库记录不一致";
            if (blocked == null && allocations.stream().anyMatch(a -> !evidence.remaining().containsKey(a.getId()))) blocked = "订单缺少有效实际出库记录";
            BigDecimal remaining = totalRemaining(allocations, evidence);
            vo.setRemainingBaseQuantity(!allocations.isEmpty() && allocations.stream().allMatch(a -> evidence.remaining().containsKey(a.getId())) ? remaining : null);
            if (blocked == null && remaining.signum() <= 0) blocked = "原销售商品已全部回库，无剩余可交物料";
            vo.setCanFulfill(blocked == null);
            vo.setFulfillmentBlockReason(blocked);
            if(blocked==null)vo.setConfirmationKey(confirmationKey(request,order,allocations,evidence));
        }
        vo.setAllocations(allocations.stream().map(allocation -> {
            PickupWorkItemVO.AllocationVO line = new PickupWorkItemVO.AllocationVO();
            line.setId(allocation.getId()); line.setBatchId(allocation.getBatchId());
            InventoryReceiptBatch batch = batchesById.get(allocation.getBatchId());
            line.setInternalReceiptBatchNo(batch == null ? null : batch.getInternalReceiptBatchNo());
            if (batch != null && batch.getBaseUnitId() != null) {
                line.setBaseUnitName(pickupEvidence.findBaseUnitName(batch.getBaseUnitId(), batch.getProductId()));
            }
            line.setLocationId(allocation.getLocationId());
            InventoryLocation actualLocation = locationsById.get(allocation.getLocationId());
            line.setLocationName(actualLocation == null ? null : actualLocation.getName());
            line.setAllocatedBaseQuantity(allocation.getAllocatedBaseQuantity());
            if (OrderPickupRequest.Status.PENDING.name().equals(request.getStatus())) {
                line.setRemainingBaseQuantity(evidence.remaining().get(allocation.getId()));
            }
            return line;
        }).toList());
        var names = vo.getAllocations().stream().map(PickupWorkItemVO.AllocationVO::getBaseUnitName).toList();
        if (!names.isEmpty() && names.stream().allMatch(java.util.Objects::nonNull)
                && names.stream().distinct().count() == 1) vo.setBaseUnitName(names.get(0));
        return vo;
    }

    /** 手机/单项刷新在原订单锁内取得当前请求及原余量；不按旧列表猜已交数量。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PickupWorkItemVO previewPickupRequest(Long requestId,Long operatorId) {
        OrderPickupRequest locator=pickupRequestRepository.findById(requestId).orElseThrow(()->new IllegalArgumentException("库房取货请求不存在"));
        Order order=orderRepository.findByIdForUpdate(locator.getOrderId()).orElseThrow(()->new IllegalStateException("订单不存在"));
        OrderPickupRequest request=pickupRequestRepository.findByIdForUpdate(requestId).orElseThrow(()->new IllegalArgumentException("库房取货请求不存在"));
        if(!order.getId().equals(request.getOrderId())||!order.getStoreId().equals(request.getStoreId()))throw new IllegalStateException("取货请求与订单归属不一致");
        requireStoreEmployee(order.getStoreId(),operatorId);
        List<OrderItemBatchAllocation> assigned=allocationRepository.findByOrderItemId(request.getOrderItemId());
        var evidence=OrderPickupRequest.Status.PENDING.name().equals(request.getStatus())?remainingEvidence(assigned,Map.of(request.getOrderItemId(),order.getId())):new RemainingEvidence(Map.of(),Set.of());
        var products=productRepository.findById(request.getProductId()).map(p->Map.of(p.getId(),p)).orElseGet(Map::of);
        var locations=locationRepository.findByStoreId(order.getStoreId()).stream().collect(Collectors.toMap(InventoryLocation::getId,Function.identity()));
        var batches=receiptBatchRepository.findByStore(order.getStoreId()).stream().collect(Collectors.toMap(InventoryReceiptBatch::getId,Function.identity()));
        return toPickupWorkItem(request,Map.of(order.getId(),order),products,locations,batches,assigned,evidence);
    }

    private void requireStoreEmployee(Long storeId,Long operatorId) {
        var actor=operatorId==null?null:staffRepository.findIdentityById(operatorId).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted()||!java.util.Objects.equals(storeId,actor.getStoreId()))
            throw new IllegalArgumentException("需要本店有效员工身份核对提货");
    }

    private String confirmationKey(OrderPickupRequest request,Order order,List<OrderItemBatchAllocation> assigned,RemainingEvidence evidence) {
        var parts=new java.util.ArrayList<String>();
        parts.add(String.valueOf(request.getId()));parts.add(String.valueOf(request.getStoreId()));parts.add(String.valueOf(request.getOrderId()));
        parts.add(String.valueOf(request.getOrderItemId()));parts.add(String.valueOf(request.getProductId()));parts.add(String.valueOf(request.getSourceLocationId()));
        parts.add(request.getStatus());parts.add(String.valueOf(order.getOrderStatus()));parts.add(request.getRequestedBaseQuantity().stripTrailingZeros().toPlainString());
        assigned.stream().sorted(java.util.Comparator.comparing(OrderItemBatchAllocation::getId)).forEach(a->{
            parts.add(String.valueOf(a.getId()));parts.add(String.valueOf(a.getBatchId()));parts.add(String.valueOf(a.getLocationId()));parts.add(String.valueOf(a.getSourceMovementId()));
            parts.add(a.getAllocatedBaseQuantity().stripTrailingZeros().toPlainString());parts.add(evidence.remaining().get(a.getId()).stripTrailingZeros().toPlainString());
        });
        String raw=parts.stream().map(p->p.length()+":"+p).collect(Collectors.joining());
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException("提货核对摘要不可用",error);}
    }

    /** 核对已出库分配并确认交货，永不再次扣库存。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<OrderItemBatchAllocation> fulfill(Long pickupRequestId, PickupFulfillmentCommand command,
                                                  Long operatorId) {
        // 首读只定位父单；退款和交货均先争用原订单，锁后以当前读重取请求与物料。
        OrderPickupRequest locator = pickupRequestRepository.findById(pickupRequestId)
                .orElseThrow(() -> new IllegalArgumentException("库房取货请求不存在"));
        Order order = orderRepository.findByIdForUpdate(locator.getOrderId())
                .orElseThrow(() -> new IllegalStateException("订单不存在"));
        OrderPickupRequest request = pickupRequestRepository.findByIdForUpdate(pickupRequestId)
                .orElseThrow(() -> new IllegalArgumentException("库房取货请求不存在"));
        if (!order.getId().equals(request.getOrderId()) || !order.getStoreId().equals(request.getStoreId())) {
            throw new IllegalStateException("取货请求与订单归属不一致");
        }
        requireStoreEmployee(order.getStoreId(),operatorId);
        OrderItem item = itemRepository.findByIdForUpdate(request.getOrderItemId()).orElseThrow();
        if (!request.getOrderId().equals(item.getOrderId()) || !request.getProductId().equals(item.getProductId())) {
            throw new IllegalStateException("取货请求与订单明细不一致");
        }
        List<OrderItemBatchAllocation> assigned = allocationRepository.findByOrderItemId(request.getOrderItemId());
        if (OrderPickupRequest.Status.FULFILLED.name().equals(request.getStatus())) {
            return assigned.stream().filter(a -> request.getId().equals(a.getPickupRequestId())
                    && Set.of("SCANNED", "FULFILLMENT_CONFIRMED").contains(a.getEvidenceLevel())).toList();
        }
        if (!OrderPickupRequest.Status.PENDING.name().equals(request.getStatus())) throw new IllegalStateException("取货请求状态无效");
        if (assigned.isEmpty() || assigned.stream().anyMatch(a -> a.getSourceMovementId() == null)) throw new IllegalStateException("订单缺少实际出库记录");
        assertCanHandOver(order);
        if (assigned.stream().anyMatch(a -> !request.getId().equals(a.getPickupRequestId())
                || !request.getProductId().equals(a.getProductId()) || !request.getStoreId().equals(a.getStoreId()))) {
            throw new IllegalStateException("出库分配的请求归属不一致");
        }
        if (!validRequestAllocations(request, assigned)) throw new IllegalStateException("请求数量与实际出库记录不一致");
        RemainingEvidence evidence = remainingEvidence(assigned, Map.of(request.getOrderItemId(), order.getId()));
        requireRemaining(assigned, evidence);
        List<OrderItemBatchAllocation> remaining = assigned.stream()
                .filter(a -> evidence.remaining().get(a.getId()).signum() > 0).toList();
        if (command == null) throw new IllegalArgumentException("取货确认不能为空");
        boolean scanned = StringUtils.hasText(command.getScannedCode());
        if (command.getAllocationIds() != null) {
            List<Long> confirmed = command.getAllocationIds();
            Set<Long> confirmedIds = new java.util.HashSet<>(confirmed);
            Set<Long> assignedIds = remaining.stream().map(OrderItemBatchAllocation::getId).collect(Collectors.toSet());
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
            if (remaining.stream().anyMatch(a -> !actualBatch.equals(a.getBatchId())
                    || (command.getLocationId() != null && !command.getLocationId().equals(a.getLocationId())))) {
                throw new IllegalArgumentException("取货批次与已出库记录不一致，请逐项完整核对所有出库分配");
            }
        }
        if(command.getConfirmationKey()==null||!command.getConfirmationKey().equals(confirmationKey(request,order,assigned,evidence)))
            throw new IllegalArgumentException("提货商品或数量已变化，请重新核对最新清单");
        for (OrderItemBatchAllocation allocation : remaining) {
            allocation.setEvidenceLevel(scanned ? "SCANNED" : "FULFILLMENT_CONFIRMED");
            allocationRepository.save(allocation);
        }
        request.setStatus(OrderPickupRequest.Status.FULFILLED.name());
        request.setFulfilledAt(LocalDateTime.now()); request.setFulfilledBy(operatorId);
        pickupRequestRepository.save(request);
        return remaining;
    }

    /** 只读取库存移动事实，不取得库存锁；调用方已先锁原订单并使用实际 READ_COMMITTED。 */
    private RemainingEvidence remainingEvidence(List<OrderItemBatchAllocation> allocations, Map<Long, Long> orderIdsByItem) {
        if (allocations.isEmpty()) return new RemainingEvidence(Map.of(), Set.of());
        List<Long> sourceIds = allocations.stream().map(OrderItemBatchAllocation::getSourceMovementId).filter(java.util.Objects::nonNull).distinct().toList();
        List<Long> allocationIds = allocations.stream().map(OrderItemBatchAllocation::getId).toList();
        // 售后能力与移动事实一次批查，避免工作项逐行回查。
        List<Map<String, Object>> movements = pickupEvidence.findMovements(sourceIds, allocationIds);
        Map<Long, Map<String, Object>> byId = new java.util.HashMap<>();
        Set<Long> processing = new java.util.HashSet<>();
        for (Map<String, Object> movement : movements) {
            byId.put(longValue(movement.get("id")), movement);
            if (movement.get("processing_after_sales") instanceof Number active && active.intValue() == 1) {
                processing.add(longValue(movement.get("reference_id")));
            }
        }
        Map<Long, BigDecimal> remaining = new java.util.HashMap<>();
        for (OrderItemBatchAllocation allocation : allocations) {
            Map<String, Object> sale = byId.get(allocation.getSourceMovementId());
            if (sale == null || allocation.getAllocatedBaseQuantity() == null || allocation.getAllocatedBaseQuantity().signum() <= 0
                    || !"SALE".equals(sale.get("movement_type")) || !"ORDER".equals(sale.get("reference_type"))
                    || !java.util.Objects.equals(orderIdsByItem.get(allocation.getOrderItemId()), longValue(sale.get("reference_id")))
                    || !sameMaterial(allocation, sale) || !java.util.Objects.equals(allocation.getLocationId(), longValue(sale.get("from_location_id")))
                    || !(sale.get("base_quantity") instanceof BigDecimal quantity) || quantity.compareTo(allocation.getAllocatedBaseQuantity()) != 0) continue;
            BigDecimal returned = movements.stream().filter(m -> "RETURN".equals(m.get("movement_type"))
                    && "SALE_ALLOCATION".equals(m.get("reference_type")) && java.util.Objects.equals(allocation.getId(), longValue(m.get("reference_id")))
                    && sameMaterial(allocation, m) && m.get("base_quantity") instanceof BigDecimal q && q.signum() > 0)
                    .map(m -> (BigDecimal) m.get("base_quantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal value = allocation.getAllocatedBaseQuantity().subtract(returned);
            if (value.signum() >= 0) remaining.put(allocation.getId(), value);
        }
        return new RemainingEvidence(remaining, processing);
    }

    private static boolean sameMaterial(OrderItemBatchAllocation allocation, Map<String, Object> movement) {
        return allocation.getStoreId() != null && allocation.getProductId() != null && allocation.getBatchId() != null
                && allocation.getStoreId().equals(longValue(movement.get("store_id")))
                && allocation.getProductId().equals(longValue(movement.get("product_id")))
                && allocation.getBatchId().equals(longValue(movement.get("batch_id")));
    }

    private static Long longValue(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private record RemainingEvidence(Map<Long, BigDecimal> remaining, Set<Long> processingOrderIds) { }

    private BigDecimal totalRemaining(List<OrderItemBatchAllocation> assigned, RemainingEvidence evidence) {
        return assigned.stream().map(a -> evidence.remaining().getOrDefault(a.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireRemaining(List<OrderItemBatchAllocation> assigned, RemainingEvidence evidence) {
        if (assigned.stream().anyMatch(a -> !evidence.remaining().containsKey(a.getId()))) {
            throw new IllegalStateException("订单缺少有效实际出库记录或退货数量异常");
        }
        if (totalRemaining(assigned, evidence).signum() <= 0) throw new IllegalStateException("原销售商品已全部回库，无剩余可交物料");
    }

    private boolean validRequestAllocations(OrderPickupRequest request, List<OrderItemBatchAllocation> assigned) {
        return !assigned.isEmpty() && request.getRequestedBaseQuantity() != null && assigned.stream().allMatch(a ->
                request.getId().equals(a.getPickupRequestId()) && request.getOrderItemId().equals(a.getOrderItemId())
                        && request.getStoreId().equals(a.getStoreId())
                        && request.getProductId().equals(a.getProductId()) && a.getAllocatedBaseQuantity() != null)
                && assigned.stream().map(OrderItemBatchAllocation::getAllocatedBaseQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(request.getRequestedBaseQuantity()) == 0;
    }

    private String handoverBlockReason(Order order) {
        if (order == null || order.getOrderStatus() == null || !Set.of(2,3,4,5,6,9).contains(order.getOrderStatus())) {
            return "订单未支付、已取消或退款，不能继续取货交付";
        }
        return null;
    }

    private void assertCanHandOver(Order order) {
        String blocked = handoverBlockReason(order);
        if (blocked != null) throw new IllegalStateException(blocked);
        if (afterSalesRepository.findByOrderId(order.getId()).stream().anyMatch(afterSales ->
                Integer.valueOf(10).equals(afterSales.getStatus()) || Integer.valueOf(40).equals(afterSales.getStatus()))) {
            throw new IllegalStateException("订单有处理中售后，暂缓取货交付");
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
