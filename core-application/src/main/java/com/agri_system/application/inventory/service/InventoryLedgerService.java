package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.*;
import com.agri_system.application.inventory.dto.vo.BatchDetailVO;
import com.agri_system.application.order.service.OrderCostAllocationService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.inventory.model.*;
import com.agri_system.domain.inventory.repository.*;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.inventory.service.InventoryDomainService;
import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.service.QuantityGranularityPolicy;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.agri_system.domain.inventory.service.BatchBalanceService.Direction.*;
import static com.agri_system.domain.inventory.service.BatchBalanceService.MovementPurpose.*;

/** 所有业务库存入口共用的批次账。数量计算使用基准单位，旧接口展示使用商品主单位。 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InventoryLedgerService {
    private final BatchBalanceService balances;
    private final InventoryReceiptRepository receipts;
    private final InventoryReceiptLineRepository lines;
    private final InventoryReceiptBatchRepository batches;
    private final ProductRepository products;
    private final ProductUnitService units;
    private final OrderItemRepository items;
    private final OrderItemBatchAllocationRepository allocations;
    private final OrderCostAllocationService costs;
    private final JdbcTemplate jdbc;

    public void lockProducts(Long storeId, List<Long> productIds) {
        productIds.stream().distinct().sorted().forEach(id -> balances.lockStock(storeId, id));
    }

    /** 开单前只读核对，避免先写订单或收款再由出库拒绝不可拆数量。 */
    public void validateSaleQuantity(Long productId, String unitName, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("销售数量必须大于0且最多4位有效小数");
        }
        units.requireEnabledUnit(productId, unitName, ProductUnitService.UnitOperation.SALE);
        units.toBaseQuantity(productId, unitName, quantity);
    }

    public List<BatchDetailVO> outbound(InventoryOutCommand command, Long operatorId) {
        String unitName = command.getUnit() == null ? mainUnit(command.getProductId()) : command.getUnit();
        ProductUnit unit = units.requireEnabledUnit(command.getProductId(), unitName, ProductUnitService.UnitOperation.SALE);
        BigDecimal quantity = units.toBaseQuantity(command.getProductId(), unitName, command.getQuantity()).baseQuantity();
        Long itemId = command.getReferenceLineId();
        if ("ORDER".equals(command.getReferenceType())) {
            if (itemId == null) throw new IllegalArgumentException("销售出库必须关联订单明细");
            OrderItem item = items.findByIdForUpdate(itemId).orElseThrow(() -> new IllegalArgumentException("订单明细不存在"));
            if (!Objects.equals(item.getOrderId(), command.getReferenceId())
                    || !Objects.equals(item.getProductId(), command.getProductId())
                    || item.getQuantity().compareTo(command.getQuantity()) != 0
                    || !Objects.equals(item.getUnit(), unitName)) {
                throw new IllegalArgumentException("出库与订单明细不一致");
            }
            Long orderStore = jdbc.queryForObject("SELECT store_id FROM `order` WHERE id=?", Long.class, item.getOrderId());
            if (!Objects.equals(orderStore, command.getStoreId())) throw new IllegalArgumentException("出库门店与订单不一致");
            if (!allocations.findByOrderItemId(itemId).isEmpty()) throw new IllegalStateException("该订单明细已出库，不能重复扣减");
        }
        if (command.getReferenceId() == null || command.getReferenceType() == null) throw new IllegalArgumentException("出库必须关联业务单据");
        balances.lockStock(command.getStoreId(), command.getProductId());
        List<Slice> selected = select(command.getStoreId(), command.getProductId(), quantity, true);
        List<BatchDetailVO> result = new ArrayList<>();
        String key = command.getReferenceType() + ":" + command.getReferenceId() + ":" + (itemId == null ? command.getProductId() : itemId);
        for (Slice slice : selected) {
            InventoryReceiptBatch batch = batch(slice.batchId());
            InventoryMovement movement = move(batch, slice.locationId(), null, SALE, slice.quantity(), unit,
                    command.getReferenceType(), command.getReferenceId(), operatorId, "销售出库", "SALE:" + key + ":" + slice.batchId() + ":" + slice.locationId());
            if (itemId != null) {
                OrderItemBatchAllocation allocation = new OrderItemBatchAllocation();
                allocation.setOrderItemId(itemId); allocation.setProductId(batch.getProductId()); allocation.setStoreId(batch.getStoreId());
                allocation.setBatchId(batch.getId()); allocation.setLocationId(slice.locationId());
                allocation.setAllocatedBaseQuantity(slice.quantity()); allocation.setBatchUnitCost(batch.getBaseUnitCost());
                allocation.setAllocatedCost(slice.quantity().multiply(batch.getBaseUnitCost()));
                allocation.setEvidenceLevel("RULE_ALLOCATED"); allocation.setAllocationRule("FEFO_AVAILABLE");
                allocation.setAllocationRuleVersion("1"); allocation.setSourceMovementId(movement.getId());
                allocation.setCreatedAt(LocalDateTime.now()); allocation.setCreatedBy(operatorId);
                allocations.save(allocation);
            }
            result.add(view(batch, slice.quantity()));
        }
        if (itemId != null) {
            costs.refreshOrderItemSnapshot(itemId, operatorId);
            BigDecimal totalCost = selected.stream().map(s -> batch(s.batchId()).getBaseUnitCost().multiply(s.quantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            jdbc.update("UPDATE order_item SET cost_price_snapshot=? WHERE id=?", totalCost.divide(command.getQuantity(), 8, RoundingMode.HALF_UP), itemId);
        }
        return result;
    }

    /** 售后直接恢复原销售批次，不能新建一个丢失日期和成本的退款批次。 */
    public Long inbound(InventoryInCommand command, Long operatorId) {
        if (command.getSourceType() != null && (command.getSourceType().contains("REFUND") || command.getSourceType().contains("AFTER_SALES") || "SALES_RETURN".equals(command.getSourceType()))) {
            return returnSold(command, operatorId);
        }
        String unitName = command.getUnit() == null ? mainUnit(command.getProductId()) : command.getUnit();
        ProductUnit unit = units.requireEnabledUnit(command.getProductId(), unitName, ProductUnitService.UnitOperation.INVENTORY);
        BigDecimal quantity = units.toBaseQuantity(command.getProductId(), unitName, command.getQuantity()).baseQuantity();
        if (command.getCostPrice() == null || command.getCostPrice().signum() <= 0) throw new IllegalArgumentException("入库成本必须大于0");
        balances.lockStock(command.getStoreId(), command.getProductId());
        InventoryReceiptBatch batch = createBatch(command.getStoreId(), command.getProductId(), command.getBatchNo(), quantity,
                command.getCostPrice().divide(unit.getBaseQuantityPerUnit(), 8, RoundingMode.HALF_UP),
                command.getProductionDate(), command.getExpireDate(), operatorId, command.getRemark());
        balances.increaseFromReceipt(receiptId(batch), batch);
        return batch.getId();
    }

    private Long returnSold(InventoryInCommand command, Long operatorId) {
        if (command.getSourceLineNo() == null || command.getSourceId() == null) throw new IllegalArgumentException("退货必须关联原订单明细和退款单据");
        long itemId = command.getSourceLineNo().longValue();
        OrderItem item = items.findByIdForUpdate(itemId).orElseThrow(() -> new IllegalArgumentException("原订单明细不存在"));
        if (!Objects.equals(item.getProductId(), command.getProductId())) throw new IllegalArgumentException("退货商品与原单不一致");
        Long store = jdbc.queryForObject("SELECT store_id FROM `order` WHERE id=?", Long.class, item.getOrderId());
        if (!Objects.equals(store, command.getStoreId())) throw new IllegalArgumentException("退货必须回原销售门店");
        balances.lockStock(store, item.getProductId());
        List<OrderItemBatchAllocation> sold = allocations.findByOrderItemId(itemId);
        if (sold.isEmpty() || sold.stream().anyMatch(a -> a.getBatchId() == null || a.getSourceMovementId() == null)) {
            throw new IllegalStateException("原单缺少实际出库记录，不能自动回库，请核对历史库存");
        }
        // 原订单行单位与原出库分配是退货唯一换算来源。
        if (command.getUnit() != null && !Objects.equals(command.getUnit(), item.getUnit())) {
            throw new IllegalArgumentException("退货必须使用原销售单位");
        }
        BigDecimal requested = validateReturnQuantity(item, command.getQuantity());
        String operation = command.getSourceType() + ":" + command.getSourceId() + ":" + itemId;
        Integer previous = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE LEFT(idempotency_key,?)=?", Integer.class, ("RETURN:" + operation + ":").length(), "RETURN:" + operation + ":");
        if (previous != null && previous > 0) throw new IllegalStateException("该退货单据已经回库，不能重复提交");
        BigDecimal remaining = requested;
        Long first = null;
        for (OrderItemBatchAllocation allocation : sold) {
            BigDecimal returned = jdbc.queryForList("SELECT base_quantity FROM inventory_movement WHERE movement_type='RETURN' AND reference_type='SALE_ALLOCATION' AND reference_id=? ORDER BY id FOR UPDATE", BigDecimal.class, allocation.getId())
                    .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal available = allocation.getAllocatedBaseQuantity().subtract(returned);
            if (available.signum() <= 0) continue;
            BigDecimal take = remaining.min(available);
            InventoryReceiptBatch batch = batch(allocation.getBatchId());
            ProductUnit base = historicalBaseUnit(batch);
            QuantityGranularityPolicy.requireBaseQuantity(base.getUnitName(), take);
            move(batch, null, allocation.getLocationId(), RETURN, take, base, "SALE_ALLOCATION", allocation.getId(), operatorId,
                    "原单退货 " + operation, "RETURN:" + operation + ":" + allocation.getId());
            if (first == null) first = batch.getId();
            remaining = remaining.subtract(take);
            if (remaining.signum() == 0) break;
        }
        if (remaining.signum() != 0) throw new IllegalArgumentException("累计退货数量超过原单实际出库数量");
        return first;
    }

    /** 实际出库证据不足时不使用当前商品单位补猜历史。 */
    public ReturnQuantityMetadata returnQuantityMetadata(OrderItem item) {
        if (item == null || item.getId() == null || item.getQuantity() == null || item.getQuantity().signum() <= 0) return null;
        List<OrderItemBatchAllocation> sold = allocations.findByOrderItemId(item.getId());
        if (sold == null || sold.isEmpty()) return null;
        String baseName = null;
        Long baseId = null;
        BigDecimal soldBase = BigDecimal.ZERO;
        for (OrderItemBatchAllocation allocation : sold) {
            if (allocation.getBatchId() == null || allocation.getSourceMovementId() == null
                    || allocation.getAllocatedBaseQuantity() == null || allocation.getAllocatedBaseQuantity().signum() <= 0) return null;
            InventoryReceiptBatch batch = batches.findById(allocation.getBatchId()).orElse(null);
            if (batch == null || !Objects.equals(batch.getProductId(), item.getProductId())) return null;
            ProductUnit base = units.findPersistedUnit(batch.getBaseUnitId());
            if (base == null || !base.isBase() || !Objects.equals(base.getProductId(), item.getProductId())
                    || base.getUnitName() == null || base.getUnitName().isBlank()) return null;
            if (baseId != null && !baseId.equals(base.getId())) return null;
            baseId = base.getId(); baseName = base.getUnitName();
            soldBase = soldBase.add(allocation.getAllocatedBaseQuantity());
        }
        BigDecimal ratio = null;
        try { ratio = soldBase.divide(item.getQuantity(), 8, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ignored) { /* 原销售比例无法精确显示，不能生成近似提示。 */ }
        return new ReturnQuantityMetadata(baseName, QuantityGranularityPolicy.allowsFractionalBaseQuantity(baseName),
                ratio, soldBase, item.getQuantity());
    }

    public BigDecimal validateReturnQuantity(OrderItem item, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("退货数量必须大于0且最多4位有效小数");
        }
        ReturnQuantityMetadata metadata = returnQuantityMetadata(item);
        if (metadata == null) throw new IllegalStateException("原单缺少实际出库基准单位证据，不能自动退货，请核对历史库存");
        BigDecimal requested;
        try { requested = quantity.multiply(metadata.soldBaseQuantity()).divide(metadata.soldQuantity(), 8, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("退货换算数量超出基准精度，不能舍入", error); }
        return QuantityGranularityPolicy.requireBaseQuantity(metadata.baseUnitName(), requested);
    }

    public record ReturnQuantityMetadata(String baseUnitName, boolean allowFractionalBaseQuantity,
            BigDecimal originalToBaseRatio, BigDecimal soldBaseQuantity, BigDecimal soldQuantity) {}

    private ProductUnit historicalBaseUnit(InventoryReceiptBatch batch) {
        ProductUnit base = units.findPersistedUnit(batch.getBaseUnitId());
        if (base == null || !base.isBase() || !Objects.equals(base.getProductId(), batch.getProductId())) {
            throw new IllegalStateException("原出库批次缺少持久化基准单位");
        }
        return base;
    }

    public void adjust(InventoryAdjustCommand command, Long operatorId) {
        if (command.getQuantityDiff() == null || command.getQuantityDiff().signum() == 0) throw new IllegalArgumentException("盘点差异不能为0");
        if (command.getType() == null || (command.getType() != 1 && command.getType() != 2)
                || (command.getType() == 1) != (command.getQuantityDiff().signum() > 0)) throw new IllegalArgumentException("盘点类型与数量方向不一致");
        if (command.getRemark() == null || command.getRemark().isBlank()) throw new IllegalArgumentException("盘点必须填写原因");
        ProductUnit unit = units.requireEnabledUnit(command.getProductId(), mainUnit(command.getProductId()), ProductUnitService.UnitOperation.INVENTORY);
        BigDecimal difference = command.getQuantityDiff().multiply(unit.getBaseQuantityPerUnit());
        balances.lockStock(command.getStoreId(), command.getProductId());
        String operation = command.getRequestKey();
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("盘点请求标识不能为空");
        if (command.getExpectedQuantity() == null) throw new IllegalArgumentException("盘点前账面数量不能为空");
        BigDecimal current = jdbc.queryForObject("SELECT quantity FROM inventory WHERE store_id=? AND product_id=? FOR UPDATE", BigDecimal.class, command.getStoreId(), command.getProductId());
        if (current.compareTo(command.getExpectedQuantity()) != 0) throw new IllegalStateException("库存已发生变化，请刷新后重新盘点");
        if (command.getActualQuantity() != null) {
            if (command.getActualQuantity().signum() < 0) throw new IllegalArgumentException("实盘数量不能为负");
            ProductUnit countedUnit = units.requireEnabledUnit(command.getProductId(), command.getUnit() == null ? mainUnit(command.getProductId()) : command.getUnit(), ProductUnitService.UnitOperation.INVENTORY);
            BigDecimal currentBase = jdbc.queryForList("SELECT base_quantity FROM inventory_batch_balance WHERE store_id=? AND product_id=? FOR UPDATE", BigDecimal.class, command.getStoreId(), command.getProductId())
                    .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal actualBase = QuantityGranularityPolicy.requireBaseQuantity(
                    baseUnit(command.getProductId()).getUnitName(), command.getActualQuantity().multiply(countedUnit.getBaseQuantityPerUnit()));
            difference = actualBase.subtract(currentBase);
            if (difference.signum() == 0) throw new IllegalArgumentException("实盘数量与库存一致，无需调整");
            if ((command.getType() == 1) != (difference.signum() > 0)) throw new IllegalArgumentException("实盘数量与盘点方向不一致");
        }
        ProductUnit base = baseUnit(command.getProductId());
        QuantityGranularityPolicy.requireBaseQuantity(base.getUnitName(), difference.abs());
        BigDecimal quantity = difference.abs();
        Integer previous = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE idempotency_key=? OR LEFT(idempotency_key,?)=?", Integer.class, "COUNT:" + operation, ("COUNT:" + operation + ":").length(), "COUNT:" + operation + ":");
        if (previous != null && previous > 0) throw new IllegalStateException("该盘点请求已经处理，不能重复提交");
        if (difference.signum() > 0) {
            BigDecimal cost = products.findById(command.getProductId()).orElseThrow().getAvgCostPrice();
            if (cost == null || cost.signum() <= 0) throw new IllegalArgumentException("盘盈商品缺少有效成本，请先完成收货或设置成本");
            InventoryReceiptBatch batch = createBatch(command.getStoreId(), command.getProductId(), "盘盈-" + operation, quantity,
                    cost.divide(unit.getBaseQuantityPerUnit(), 8, RoundingMode.HALF_UP), null, null, operatorId, command.getRemark());
            move(batch, null, balances.defaultLocation(command.getStoreId()).getId(), COUNT_ADJUSTMENT, quantity, unit,
                    "COUNT", receiptId(batch), operatorId, command.getRemark(), "COUNT:" + operation);
        } else {
            for (Slice slice : select(command.getStoreId(), command.getProductId(), quantity, false)) {
                move(batch(slice.batchId()), slice.locationId(), null, COUNT_ADJUSTMENT, slice.quantity(), unit,
                        "COUNT", slice.batchId(), operatorId, command.getRemark(), "COUNT:" + operation + ":" + slice.batchId() + ":" + slice.locationId());
            }
        }
    }

    public InventoryDomainService.TransferDetail transfer(Long from, Long to, Long product, BigDecimal quantity, String unitName, Long operator, Long transferId) {
        if (Objects.equals(from, to)) throw new IllegalArgumentException("调拨门店必须不同");
        ProductUnit unit = units.requireEnabledUnit(product, unitName, ProductUnitService.UnitOperation.TRANSFER);
        BigDecimal baseQuantity = units.toBaseQuantity(product, unit.getUnitName(), quantity).baseQuantity();
        balances.lockStock(Math.min(from, to), product); balances.lockStock(Math.max(from, to), product);
        List<InventoryDomainService.TransferBatchDetail> result = new ArrayList<>();
        List<InventoryDomainService.BatchDeductionDetail> sourceDetails = new ArrayList<>();
        for (Slice slice : select(from, product, baseQuantity, true)) {
            InventoryReceiptBatch source = batch(slice.batchId());
            InventoryReceiptBatch target = createBatch(to, product, source.getSupplierBatchNo(), slice.quantity(), source.getBaseUnitCost(),
                    source.getProductionDate(), source.getExpiryDate(), operator, "调拨单 " + transferId);
            balances.transfer(new BatchBalanceService.TransferRequest(source.getId(), target.getId(), slice.locationId(),
                    balances.defaultLocation(to).getId(), transferId + ":" + slice.batchId() + ":" + slice.locationId(),
                    unit.getId(), unit.getUnitName(), slice.quantity().divide(unit.getBaseQuantityPerUnit(), 8, RoundingMode.HALF_UP),
                    slice.quantity(), "TRANSFER", transferId, operator, "跨店调拨"));
            InventoryDomainService.TransferBatchDetail detail = new InventoryDomainService.TransferBatchDetail();
            detail.setFromBatchId(source.getId()); detail.setToBatchId(target.getId());
            detail.setQuantity(slice.quantity().divide(unit.getBaseQuantityPerUnit(), 8, RoundingMode.HALF_UP));
            detail.setCostPrice(source.getBaseUnitCost().multiply(unit.getBaseQuantityPerUnit())); result.add(detail);
            // 与实际货位分配逐项对应，数量与成本均按本次调拨单位返回，不查当前批次余额猜测。
            InventoryDomainService.BatchDeductionDetail sourceDetail = new InventoryDomainService.BatchDeductionDetail();
            sourceDetail.setBatchId(source.getId());
            sourceDetail.setBatchNo(source.getSupplierBatchNo() == null ? source.getInternalReceiptBatchNo() : source.getSupplierBatchNo());
            sourceDetail.setExpireDate(source.getExpiryDate());
            sourceDetail.setQuantity(detail.getQuantity()); sourceDetail.setCostPrice(detail.getCostPrice());
            sourceDetails.add(sourceDetail);
        }
        InventoryDomainService.TransferDetail detail = new InventoryDomainService.TransferDetail();
        detail.setFromStoreId(from); detail.setToStoreId(to); detail.setProductId(product); detail.setQuantity(quantity);
        detail.setTransferBatchDetails(result); detail.setBatchDetails(sourceDetails); return detail;
    }

    /** 先锁所有候选余额，再按到期日期分配；不足时不产生部分出库。 */
    private List<Slice> select(Long store, Long product, BigDecimal quantity, boolean excludeExpired) {
        List<Slice> candidates = jdbc.query("""
                SELECT b.batch_id,b.location_id,b.base_quantity-b.reserved_base_quantity AS available
                FROM inventory_batch_balance b JOIN inventory_receipt_batch r ON r.id=b.batch_id
                WHERE b.store_id=? AND b.product_id=? AND EXISTS(SELECT 1 FROM inventory_location l WHERE l.id=b.location_id AND l.status=1) AND b.base_quantity>b.reserved_base_quantity
                """ + (excludeExpired ? " AND (r.expiry_date IS NULL OR r.expiry_date>=CURRENT_DATE) " : "") + """
                ORDER BY r.expiry_date IS NULL, r.expiry_date, r.received_at, r.id, b.location_id FOR UPDATE
                """, (rs, n) -> new Slice(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3)), store, product);
        List<Slice> selected = new ArrayList<>(); BigDecimal remaining = quantity;
        for (Slice slice : candidates) {
            BigDecimal take = remaining.min(slice.quantity()); selected.add(new Slice(slice.batchId(), slice.locationId(), take));
            remaining = remaining.subtract(take); if (remaining.signum() == 0) break;
        }
        if (remaining.signum() != 0) throw new IllegalArgumentException("可用库存不足，已排除预留及不可销售批次");
        return selected;
    }

    public List<BatchDetailVO> batchViews(Long store, Long product) {
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        jdbc.query("SELECT batch_id,SUM(base_quantity) qty FROM inventory_batch_balance WHERE store_id=? AND product_id=? GROUP BY batch_id",
                rs -> { quantities.put(rs.getLong(1), rs.getBigDecimal(2)); }, store, product);
        return quantities.entrySet().stream().map(e -> view(batch(e.getKey()), e.getValue())).toList();
    }

    public BatchDetailVO batchView(Long id) {
        InventoryReceiptBatch batch = batch(id);
        BigDecimal quantity = jdbc.queryForObject("SELECT COALESCE(SUM(base_quantity),0) FROM inventory_batch_balance WHERE batch_id=?", BigDecimal.class, id);
        return view(batch, quantity);
    }

    private BatchDetailVO view(InventoryReceiptBatch batch, BigDecimal baseQuantity) {
        ProductUnit main = units.getUnits(batch.getProductId()).stream().filter(u -> u.getUnitName().equals(mainUnit(batch.getProductId()))).findFirst().orElseThrow();
        BatchDetailVO vo = new BatchDetailVO(); vo.setId(batch.getId()); vo.setStoreId(batch.getStoreId()); vo.setProductId(batch.getProductId());
        vo.setBatchNo(batch.getSupplierBatchNo() == null ? batch.getInternalReceiptBatchNo() : batch.getSupplierBatchNo());
        vo.setQuantity(baseQuantity.divide(main.getBaseQuantityPerUnit(), 8, RoundingMode.HALF_UP));
        vo.setCostPrice(batch.getBaseUnitCost().multiply(main.getBaseQuantityPerUnit()));
        vo.setProductionDate(batch.getProductionDate()); vo.setExpireDate(batch.getExpiryDate()); vo.setCreatedAt(batch.getCreatedAt());
        vo.setIsActive(1); vo.setIsExpired(batch.getExpiryDate()!=null && batch.getExpiryDate().isBefore(LocalDate.now()));
        vo.setIsExpiringSoon(batch.getExpiryDate()!=null && !vo.getIsExpired() && !batch.getExpiryDate().isAfter(LocalDate.now().plusDays(7)));
        return vo;
    }

    private InventoryMovement move(InventoryReceiptBatch batch, Long from, Long to, BatchBalanceService.MovementPurpose purpose,
            BigDecimal quantity, ProductUnit unit, String refType, Long refId, Long operator, String remark, String key) {
        return balances.move(new BatchBalanceService.MoveRequest(batch.getId(), from, to, purpose, null, unit.getId(), unit.getUnitName(),
                quantity.divide(unit.getBaseQuantityPerUnit(),8,RoundingMode.HALF_UP), quantity, batch.getBaseUnitCost(), refType, refId,
                operator, remark, from == null ? IN : OUT, key));
    }

    private InventoryReceiptBatch createBatch(Long store, Long product, String batchNo, BigDecimal quantity, BigDecimal cost,
            LocalDate production, LocalDate expiry, Long operator, String remark) {
        if ((production != null && expiry != null && production.isAfter(expiry))) throw new IllegalArgumentException("生产日期不能晚于有效期");
        ProductUnit base = baseUnit(product); LocalDateTime now = LocalDateTime.now();
        InventoryReceipt receipt = new InventoryReceipt(); receipt.setReceiptNo("IN-" + UUID.randomUUID()); receipt.setStoreId(store);
        receipt.setStatus("RECEIVED"); receipt.setReceivedAt(now); receipt.setReceiverId(operator); receipt.setRemark(remark); receipt.setCreatedAt(now); receipt.setUpdatedAt(now); receipts.save(receipt);
        InventoryReceiptLine line = new InventoryReceiptLine(); line.setReceiptId(receipt.getId()); line.setLineNo(1); line.setStoreId(store); line.setProductId(product); line.setCreatedAt(now); lines.save(line);
        InventoryReceiptBatch batch = new InventoryReceiptBatch(); batch.setReceiptLineId(line.getId()); batch.setStoreId(store); batch.setProductId(product);
        batch.setSupplierBatchNo(batchNo); batch.setInternalReceiptBatchNo("B-" + UUID.randomUUID()); batch.setOriginalUnitId(base.getId()); batch.setOriginalUnitName(base.getUnitName());
        batch.setOriginalQuantity(quantity); batch.setOriginalUnitCost(cost); batch.setBaseUnitId(base.getId()); batch.setBaseQuantity(quantity); batch.setBaseUnitCost(cost);
        batch.setDateStatus(production == null && expiry == null ? "UNKNOWN" : production == null || expiry == null ? "PARTIAL" : "KNOWN"); batch.setProductionDate(production); batch.setExpiryDate(expiry); batch.setReceiverId(operator); batch.setReceivedAt(now); batch.setCreatedAt(now); batches.save(batch);
        return batch;
    }
    private Long receiptId(InventoryReceiptBatch batch) { return jdbc.queryForObject("SELECT receipt_id FROM inventory_receipt_line WHERE id=?", Long.class, batch.getReceiptLineId()); }
    private InventoryReceiptBatch batch(Long id) { return batches.findById(id).orElseThrow(() -> new IllegalArgumentException("库存批次不存在")); }
    private ProductUnit baseUnit(Long product) { return units.getUnits(product).stream().filter(ProductUnit::isBase).findFirst().orElseThrow(() -> new IllegalArgumentException("商品缺少基准单位")); }
    private String mainUnit(Long product) { return products.findById(product).orElseThrow(() -> new IllegalArgumentException("商品不存在")).getMainUnit(); }
    private record Slice(Long batchId, Long locationId, BigDecimal quantity) { }
}
