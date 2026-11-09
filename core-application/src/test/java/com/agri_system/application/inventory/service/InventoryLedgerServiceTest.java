package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.order.service.OrderCostAllocationService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
import com.agri_system.domain.inventory.model.*;
import com.agri_system.domain.inventory.repository.*;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.product.model.*;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实运行候选批次 SQL 与 Spring 事务；仓储和移动写入边界用可观察的测试替身。 */
class InventoryLedgerServiceTest {
    private BatchBalanceService balances;
    private InventoryReceiptRepository receipts;
    private InventoryReceiptLineRepository lines;
    private InventoryReceiptBatchRepository batches;
    private ProductUnitService units;
    private OrderItemRepository items;
    private OrderItemBatchAllocationRepository allocations;
    private OrderCostAllocationService costs;
    private JdbcTemplate jdbc;
    private InventoryLedgerService service;
    private ProductUnit bag, piece;
    private final Map<Long, InventoryReceiptBatch> batchMap = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(100);

    @BeforeEach void setup() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE inventory(store_id BIGINT,product_id BIGINT,quantity DECIMAL(20,8))");
        jdbc.update("INSERT INTO inventory VALUES(1,5,3)");
        jdbc.execute("CREATE TABLE inventory_location(id BIGINT PRIMARY KEY,status INT)");
        jdbc.execute("CREATE TABLE inventory_receipt_batch(id BIGINT PRIMARY KEY,expiry_date DATE,received_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE inventory_batch_balance(batch_id BIGINT,location_id BIGINT,store_id BIGINT,product_id BIGINT,base_quantity DECIMAL(20,8),reserved_base_quantity DECIMAL(20,8))");
        jdbc.execute("CREATE TABLE inventory_movement(id BIGINT PRIMARY KEY,idempotency_key VARCHAR(255),movement_type VARCHAR(40),reference_type VARCHAR(40),reference_id BIGINT,base_quantity DECIMAL(20,8))");
        jdbc.execute("CREATE TABLE inventory_receipt_line(id BIGINT PRIMARY KEY,receipt_id BIGINT)");
        jdbc.execute("CREATE TABLE `order`(id BIGINT PRIMARY KEY,store_id BIGINT)");
        jdbc.execute("CREATE TABLE order_item(id BIGINT PRIMARY KEY,cost_price_snapshot DECIMAL(20,8))");
        jdbc.update("INSERT INTO inventory_location VALUES(10,1),(11,0)");
        jdbc.update("INSERT INTO `order` VALUES(50,1)");
        jdbc.update("INSERT INTO order_item VALUES(55,NULL)");
        balances = mock(BatchBalanceService.class); receipts = mock(InventoryReceiptRepository.class);
        lines = mock(InventoryReceiptLineRepository.class); batches = mock(InventoryReceiptBatchRepository.class);
        units = mock(ProductUnitService.class); items = mock(OrderItemRepository.class);
        allocations = mock(OrderItemBatchAllocationRepository.class); costs = mock(OrderCostAllocationService.class);
        ProductRepository products = mock(ProductRepository.class);
        Product product = new Product(); product.setMainUnit("袋");
        when(products.findById(5L)).thenReturn(Optional.of(product));
        bag = unit(20L, "袋", "10", false); piece = unit(21L, "个", "1", true);
        when(units.getUnits(5L)).thenReturn(List.of(bag, piece));
        when(units.findPersistedUnit(21L)).thenReturn(piece);
        when(units.requireEnabledUnit(eq(5L), eq("袋"), any())).thenReturn(bag);
        when(units.toBaseQuantity(eq(5L), eq("袋"), any())).thenAnswer(inv -> {
            BigDecimal quantity = inv.getArgument(2);
            return new UnitConversionSnapshot("袋", quantity, new BigDecimal("10"), quantity.multiply(new BigDecimal("10")));
        });
        when(batches.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(batchMap.get(inv.getArgument(0))));
        doAnswer(inv -> { ((InventoryReceipt) inv.getArgument(0)).setId(sequence.incrementAndGet()); return null; }).when(receipts).save(any());
        doAnswer(inv -> {
            InventoryReceiptLine line = inv.getArgument(0); line.setId(sequence.incrementAndGet());
            jdbc.update("INSERT INTO inventory_receipt_line VALUES(?,?)", line.getId(), line.getReceiptId()); return null;
        }).when(lines).save(any());
        doAnswer(inv -> {
            InventoryReceiptBatch batch = inv.getArgument(0); batch.setId(sequence.incrementAndGet());
            batchMap.put(batch.getId(), batch); return null;
        }).when(batches).save(any());
        when(balances.move(any())).thenAnswer(inv -> {
            BatchBalanceService.MoveRequest request = inv.getArgument(0);
            long id = sequence.incrementAndGet();
            jdbc.update("INSERT INTO inventory_movement VALUES(?,?,?,?,?,?)", id, request.idempotencyKey(), request.purpose().name(), request.referenceType(), request.referenceId(), request.baseQuantity());
            Long location = request.fromLocationId() == null ? request.toLocationId() : request.fromLocationId();
            BigDecimal delta = request.fromLocationId() == null ? request.baseQuantity() : request.baseQuantity().negate();
            jdbc.update("UPDATE inventory_batch_balance SET base_quantity=base_quantity+? WHERE batch_id=? AND location_id=?", delta, request.batchId(), location);
            jdbc.update("UPDATE inventory SET quantity=quantity+? WHERE store_id=1 AND product_id=5", delta.divide(BigDecimal.TEN));
            InventoryMovement movement = new InventoryMovement(); movement.setId(id); return movement;
        });
        InventoryLedgerService target = new InventoryLedgerService(balances, receipts, lines, batches, products, units, items, allocations, costs, jdbc);
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (InventoryLedgerService) factory.getProxy();
    }

    @Test void shortageExcludingReservationsProducesNoPartialSaleOrAllocation() {
        stock(1L, 10L, "30", "20", LocalDate.now().plusDays(1), "2");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.outbound(outbound("2", false), 9L));
        assertTrue(error.getMessage().contains("可用库存不足"));
        verify(balances, never()).move(any()); verifyNoInteractions(allocations, costs);
        assertDecimal("30", stockQuantity(1L)); assertEquals(0, movementCount());
    }

    @Test void dateIsAdvisoryWhileInactiveAndReservedStockStayExcluded() {
        stock(1L, 10L, "9", "0", LocalDate.now().minusDays(1), "1");
        stock(2L, 10L, "20", "5", LocalDate.now().plusDays(5), "2");
        stock(3L, 10L, "8", "0", LocalDate.now(), "3");
        stock(4L, 11L, "90", "0", LocalDate.now(), "4");
        stock(5L, 10L, "50", "0", null, "5");
        var result = service.outbound(outbound("2", false), 9L);
        ArgumentCaptor<BatchBalanceService.MoveRequest> requests = ArgumentCaptor.forClass(BatchBalanceService.MoveRequest.class);
        verify(balances, times(3)).move(requests.capture());
        assertEquals(List.of(1L, 3L, 2L), requests.getAllValues().stream().map(BatchBalanceService.MoveRequest::batchId).toList());
        assertDecimal("9", requests.getAllValues().get(0).baseQuantity());
        assertDecimal("8", requests.getAllValues().get(1).baseQuantity());
        assertDecimal("3", requests.getAllValues().get(2).baseQuantity());
        assertDecimal("0.9", result.get(0).getQuantity()); assertDecimal("10", result.get(0).getCostPrice());
        assertDecimal("0", stockQuantity(1L)); assertDecimal("90", stockQuantity(4L));
        assertDecimal("17", stockQuantity(2L)); assertDecimal("50", stockQuantity(5L));
    }

    @Test void orderSaleRecordsActualMovementAndBaseCostAndRejectsSecondDeduction() {
        stock(2L, 10L, "20", "0", LocalDate.now().plusDays(5), "2.5");
        orderItem("2");
        List<OrderItemBatchAllocation> saved = new ArrayList<>();
        when(allocations.findByOrderItemId(55L)).thenAnswer(inv -> List.copyOf(saved));
        doAnswer(inv -> { saved.add(inv.getArgument(0)); return null; }).when(allocations).save(any());
        service.outbound(outbound("2", true), 9L);
        assertEquals(1, saved.size());
        OrderItemBatchAllocation allocation = saved.get(0);
        assertEquals(55L, allocation.getOrderItemId()); assertEquals(2L, allocation.getBatchId());
        assertEquals(10L, allocation.getLocationId()); assertNotNull(allocation.getSourceMovementId());
        assertDecimal("20", allocation.getAllocatedBaseQuantity()); assertDecimal("50", allocation.getAllocatedCost());
        assertEquals("RULE_ALLOCATED", allocation.getEvidenceLevel()); assertEquals("FEFO_AVAILABLE", allocation.getAllocationRule());
        assertDecimal("25", jdbc.queryForObject("SELECT cost_price_snapshot FROM order_item WHERE id=55", BigDecimal.class));
        verify(costs).refreshOrderItemSnapshot(55L, 9L);
        assertThrows(IllegalStateException.class, () -> service.outbound(outbound("2", true), 9L));
        assertEquals(1, movementCount()); assertDecimal("0", stockQuantity(2L));
    }

    @Test void inboundConvertsMainUnitQuantityAndCostToBaseReceipt() {
        InventoryInCommand command = new InventoryInCommand(); command.setStoreId(1L); command.setProductId(5L);
        command.setUnit("袋"); command.setQuantity(new BigDecimal("2")); command.setCostPrice(new BigDecimal("25"));
        command.setProductionDate(LocalDate.now().minusDays(1)); command.setExpireDate(LocalDate.now().plusDays(5));
        Long id = service.inbound(command, 9L);
        InventoryReceiptBatch batch = batchMap.get(id);
        assertDecimal("20", batch.getBaseQuantity()); assertDecimal("2.5", batch.getBaseUnitCost());
        assertEquals("个", batch.getOriginalUnitName()); assertDecimal("20", batch.getOriginalQuantity());
        assertEquals(command.getProductionDate(), batch.getProductionDate()); assertEquals(command.getExpireDate(), batch.getExpiryDate());
        verify(balances).increaseFromReceipt(anyLong(), same(batch));
    }

    @Test void transferReturnsActualSourceSlicesAndPreservesTheirCostAndDatesInTargets() {
        LocalDate expiry = LocalDate.now().plusDays(10);
        stock(1L, 10L, "4", "0", expiry, "2");
        jdbc.update("INSERT INTO inventory_location VALUES(12,1)");
        jdbc.update("INSERT INTO inventory_batch_balance VALUES(1,12,1,5,6,0)");
        stock(2L, 10L, "30", "0", null, "3");
        batchMap.get(1L).setSupplierBatchNo("SUPPLIER-1");
        batchMap.get(1L).setProductionDate(LocalDate.now().minusDays(5));
        batchMap.get(2L).setInternalReceiptBatchNo("INTERNAL-2");
        InventoryLocation destination = new InventoryLocation(); destination.setId(20L);
        when(balances.defaultLocation(2L)).thenReturn(destination);

        var result = service.transfer(1L, 2L, 5L, new BigDecimal("2"), "袋", 9L, 80L);

        assertEquals(3, result.getBatchDetails().size());
        var sources = result.getBatchDetails();
        assertEquals(List.of(1L, 1L, 2L), sources.stream().map(d -> d.getBatchId()).toList());
        assertEquals(List.of("SUPPLIER-1", "SUPPLIER-1", "INTERNAL-2"), sources.stream().map(d -> d.getBatchNo()).toList());
        assertDecimal("0.4", sources.get(0).getQuantity());
        assertDecimal("0.6", sources.get(1).getQuantity());
        assertDecimal("1", sources.get(2).getQuantity());
        assertDecimal("20", sources.get(0).getCostPrice());
        assertDecimal("30", sources.get(2).getCostPrice());
        assertEquals(expiry, sources.get(0).getExpireDate());
        assertNull(sources.get(2).getExpireDate());
        assertEquals(3, result.getTransferBatchDetails().size());
        var targets = result.getTransferBatchDetails();
        assertEquals(List.of(1L, 1L, 2L), targets.stream().map(d -> d.getFromBatchId()).toList());
        assertDecimal("4", batchMap.get(targets.get(0).getToBatchId()).getBaseQuantity());
        assertDecimal("6", batchMap.get(targets.get(1).getToBatchId()).getBaseQuantity());
        assertDecimal("10", batchMap.get(targets.get(2).getToBatchId()).getBaseQuantity());
        var firstTarget = batchMap.get(targets.get(0).getToBatchId());
        assertEquals("SUPPLIER-1", firstTarget.getSupplierBatchNo());
        assertDecimal("2", firstTarget.getBaseUnitCost());
        assertEquals(LocalDate.now().minusDays(5), firstTarget.getProductionDate());
        assertEquals(expiry, firstTarget.getExpiryDate());
        var unknownDateTarget = batchMap.get(targets.get(2).getToBatchId());
        assertDecimal("3", unknownDateTarget.getBaseUnitCost());
        assertNull(unknownDateTarget.getProductionDate()); assertNull(unknownDateTarget.getExpiryDate());
    }

    @Test void refundUsesOriginalSoldRatioBatchCostDatesAndLocationDespiteUnitChange() {
        stock(2L, 10L, "0", "0", LocalDate.now().minusDays(1), "2.5");
        orderItem("2"); soldAllocation("20");
        // 当前单位比例变更，不得改变原单两袋对应二十个的事实。
        bag.setBaseQuantityPerUnit(new BigDecimal("100"));
        InventoryInCommand command = refund("1", 80L);
        command.setCostPrice(new BigDecimal("999"));
        assertEquals(2L, service.inbound(command, 9L));
        ArgumentCaptor<BatchBalanceService.MoveRequest> request = ArgumentCaptor.forClass(BatchBalanceService.MoveRequest.class);
        verify(balances).move(request.capture());
        assertEquals(2L, request.getValue().batchId()); assertEquals(10L, request.getValue().toLocationId());
        assertEquals(BatchBalanceService.MovementPurpose.RETURN, request.getValue().purpose());
        assertDecimal("10", request.getValue().baseQuantity()); assertDecimal("2.5", request.getValue().unitCost());
        assertEquals(LocalDate.now().minusDays(1), batchMap.get(2L).getExpiryDate());
        assertDecimal("10", stockQuantity(2L)); verifyNoInteractions(receipts, lines);
        verify(batches, never()).save(any()); verify(units, never()).toBaseQuantity(any(), any(), any());
    }

    @Test void returnMetadataUsesOriginalAllocationRatioAndNeverCurrentMainUnitCoefficient() {
        stock(2L, 10L, "0", "0", null, "2"); orderItem("2"); soldAllocation("20");
        bag.setBaseQuantityPerUnit(new BigDecimal("999"));
        var item = items.findByIdForUpdate(55L).orElseThrow();
        var metadata = service.returnQuantityMetadata(item);
        assertEquals("个", metadata.baseUnitName()); assertFalse(metadata.allowFractionalBaseQuantity());
        assertDecimal("10", metadata.originalToBaseRatio());
    }

    @Test void missingOriginalBatchBaseUnitNeverFallsBackToCurrentProductUnit() {
        stock(2L, 10L, "0", "0", null, "2"); orderItem("2"); soldAllocation("20");
        batchMap.get(2L).setBaseUnitId(null);
        var item = items.findByIdForUpdate(55L).orElseThrow();
        assertNull(service.returnQuantityMetadata(item));
        assertThrows(IllegalStateException.class, () -> service.inbound(refund("0.5", 80L), 9L));
        verify(balances, never()).move(any()); verifyNoInteractions(receipts, lines);
    }

    @Test void measuredOriginalBaseAllowsFractionalReturnWithoutUsingChangedMainUnit() {
        stock(2L, 10L, "0", "0", null, "2"); orderItem("2"); soldAllocation("20");
        piece.setUnitName("公斤"); bag.setBaseQuantityPerUnit(new BigDecimal("999"));
        service.inbound(refund("0.15", 80L), 9L);
        assertDecimal("1.5", stockQuantity(2L));
    }

    @Test void fractionalReturnThatSplitsSmallestPieceIsRejectedBeforeMovement() {
        stock(2L, 10L, "0", "0", null, "2"); orderItem("2"); soldAllocation("20");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.inbound(refund("0.15", 80L), 9L));
        assertTrue(error.getMessage().contains("整数"));
        verify(balances, never()).move(any()); assertDecimal("0", stockQuantity(2L));
    }

    @Test void countCannotSplitSmallestPieceEvenWhenDifferenceHasFourDecimals() {
        stock(2L, 10L, "30", "0", null, "2");
        InventoryAdjustCommand command = count("fractional-count", "3");
        command.setQuantityDiff(new BigDecimal("-0.15"));
        assertThrows(IllegalArgumentException.class, () -> service.adjust(command, 9L));
        verify(balances, never()).move(any()); assertDecimal("30", stockQuantity(2L));
    }

    @Test void repeatedRefundDocumentIsRejectedWithoutAddingStock() {
        stock(2L, 10L, "0", "0", null, "2"); orderItem("2"); soldAllocation("20");
        service.inbound(refund("1", 80L), 9L);
        assertThrows(IllegalStateException.class, () -> service.inbound(refund("1", 80L), 9L));
        assertDecimal("10", stockQuantity(2L)); assertEquals(1, movementCount());
    }

    @Test void cumulativeRefundLimitRollsBackAnyPartialRestore() {
        stock(2L, 10L, "0", "0", null, "2"); orderItem("2"); soldAllocation("20");
        service.inbound(refund("1.5", 80L), 9L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.inbound(refund("1", 81L), 9L));
        assertTrue(error.getMessage().contains("累计退货数量超过"));
        assertDecimal("15", stockQuantity(2L)); assertEquals(1, movementCount());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE idempotency_key LIKE 'RETURN:ORDER_REFUND:81:%'", Integer.class));
        service.inbound(refund("0.5", 82L), 9L);
        assertDecimal("20", stockQuantity(2L)); assertEquals(2, movementCount());
    }

    @Test void refundWithoutActualMovementEvidenceDoesNotInventStock() {
        orderItem("2");
        OrderItemBatchAllocation allocation = new OrderItemBatchAllocation(); allocation.setBatchId(2L);
        when(allocations.findByOrderItemId(55L)).thenReturn(List.of(allocation));
        assertThrows(IllegalStateException.class, () -> service.inbound(refund("1", 80L), 9L));
        verify(balances, never()).move(any()); verifyNoInteractions(receipts, lines); assertEquals(0, movementCount());
    }

    @Test void staleCountSnapshotRejectsBeforeAnyStockMovement() {
        stock(2L, 10L, "30", "0", null, "2");
        InventoryAdjustCommand command = count("count-a", "2");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.adjust(command, 9L));
        assertTrue(error.getMessage().contains("库存已发生变化"));
        assertDecimal("30", stockQuantity(2L)); assertEquals(0, movementCount());
        verify(balances, never()).move(any());
    }

    @Test void retryCountRequestCannotDeductTwiceEvenWithUpdatedSnapshot() {
        stock(2L, 10L, "30", "0", LocalDate.now().minusDays(1), "2");
        service.adjust(count("count-a", "3"), 9L);
        assertDecimal("20", stockQuantity(2L));
        // 盘亏允许处理过期实物，但重复请求即使携带新账面数量也不能重复扣减。
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.adjust(count("count-a", "2"), 9L));
        assertTrue(error.getMessage().contains("已经处理"));
        assertDecimal("20", stockQuantity(2L)); assertEquals(1, movementCount());
    }

    @Test void differentCountRequestWithSharedPrefixIsNotRejectedAsDuplicate() {
        assertDistinctCountRequests("count-ab", "count-a");
    }

    @Test void literalSqlWildcardCharactersInCountKeyDoNotMatchOtherRequests() {
        assertDistinctCountRequests("count-X", "count-%");
    }

    private void assertDistinctCountRequests(String first, String second) {
        stock(2L, 10L, "30", "0", null, "2");
        service.adjust(count(first, "3"), 9L);
        service.adjust(count(second, "2"), 9L);
        assertDecimal("10", stockQuantity(2L)); assertEquals(2, movementCount());
    }

    private InventoryAdjustCommand count(String key, String expected) {
        InventoryAdjustCommand command = new InventoryAdjustCommand(); command.setStoreId(1L); command.setProductId(5L);
        command.setRequestKey(key); command.setExpectedQuantity(new BigDecimal(expected));
        command.setQuantityDiff(BigDecimal.ONE.negate()); command.setType(2); command.setRemark("核对盘亏"); return command;
    }

    private ProductUnit unit(Long id, String name, String factor, boolean base) {
        ProductUnit unit = new ProductUnit(); unit.setId(id); unit.setProductId(5L); unit.setUnitName(name);
        unit.setBaseQuantityPerUnit(new BigDecimal(factor)); unit.setIsBaseUnit(base ? 1 : 0); return unit;
    }
    private void stock(Long id, Long location, String quantity, String reserved, LocalDate expiry, String cost) {
        InventoryReceiptBatch batch = new InventoryReceiptBatch(); batch.setId(id); batch.setStoreId(1L); batch.setProductId(5L);
        batch.setBaseUnitId(21L); batch.setBaseUnitCost(new BigDecimal(cost)); batch.setExpiryDate(expiry); batchMap.put(id, batch);
        jdbc.update("INSERT INTO inventory_receipt_batch VALUES(?,?,CURRENT_TIMESTAMP)", id, expiry);
        jdbc.update("INSERT INTO inventory_batch_balance VALUES(?,?,1,5,?,?)", id, location, new BigDecimal(quantity), new BigDecimal(reserved));
    }
    private InventoryOutCommand outbound(String quantity, boolean order) {
        InventoryOutCommand command = new InventoryOutCommand(); command.setStoreId(1L); command.setProductId(5L);
        command.setUnit("袋"); command.setQuantity(new BigDecimal(quantity)); command.setReferenceType(order ? "ORDER" : "OTHER_SALE");
        command.setReferenceId(50L); if (order) command.setReferenceLineId(55L); return command;
    }
    private void orderItem(String quantity) {
        OrderItem item = new OrderItem(); item.setId(55L); item.setOrderId(50L); item.setProductId(5L);
        item.setUnit("袋"); item.setQuantity(new BigDecimal(quantity));
        when(items.findByIdForUpdate(55L)).thenReturn(Optional.of(item));
    }
    private void soldAllocation(String quantity) {
        OrderItemBatchAllocation allocation = new OrderItemBatchAllocation(); allocation.setId(60L); allocation.setBatchId(2L);
        allocation.setLocationId(10L); allocation.setSourceMovementId(70L); allocation.setAllocatedBaseQuantity(new BigDecimal(quantity));
        allocation.setBatchUnitCost(new BigDecimal("2.5"));
        when(allocations.findByOrderItemId(55L)).thenReturn(List.of(allocation));
    }
    private InventoryInCommand refund(String quantity, Long document) {
        InventoryInCommand command = new InventoryInCommand(); command.setStoreId(1L); command.setProductId(5L); command.setUnit("袋");
        command.setSourceType("ORDER_REFUND"); command.setSourceId(document); command.setSourceLineNo(55); command.setQuantity(new BigDecimal(quantity)); return command;
    }
    private BigDecimal stockQuantity(Long id) { return jdbc.queryForObject("SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=?", BigDecimal.class, id); }
    private int movementCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movement", Integer.class); }
    private void assertDecimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
