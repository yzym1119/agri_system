package com.agri_system.application.purchase.service;

import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
import com.agri_system.application.purchase.dto.command.PurchaseCreateCommand;
import com.agri_system.application.purchase.dto.command.PurchaseInboundCommand;
import com.agri_system.application.purchase.dto.command.PurchaseItemDTO;
import com.agri_system.application.purchase.dto.command.PurchaseSubmitCommand;
import com.agri_system.application.purchase.dto.command.ReceivePurchaseCommand;
import com.agri_system.application.purchase.dto.command.ReceiptBatchCommand;
import com.agri_system.application.purchase.exception.PurchaseErrorCode;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptLineRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.purchase.model.PurchaseOrder;
import com.agri_system.domain.purchase.model.PurchaseOrderItem;
import com.agri_system.domain.purchase.repository.PurchaseItemRepository;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import com.agri_system.domain.purchase.service.PurchaseDomainService;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 采购应用服务单元测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class PurchaseApplicationServiceTest {
    
    @Mock
    private PurchaseRepository purchaseRepository;
    
    @Mock
    private PurchaseItemRepository itemRepository;
    
    @Mock
    private PurchaseDomainService domainService;
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private StoreRepository storeRepository;
    
    @Mock
    private ProductUnitService productUnitService;

    @Mock
    private InventoryReceiptRepository receiptRepository;

    @Mock
    private InventoryReceiptLineRepository receiptLineRepository;

    @Mock
    private InventoryReceiptBatchRepository receiptBatchRepository;

    @Mock
    private ObjectProvider<BatchBalanceService> batchBalanceServiceProvider;

    @Mock
    private BatchBalanceService batchBalanceService;
    
    @InjectMocks
    private PurchaseApplicationService purchaseService;
    
    private PurchaseCreateCommand createCommand;
    private Store store;
    private Product product;
    
    @BeforeEach
    void setUp() {
        // 准备门店
        store = new Store();
        store.setId(1L);
        store.setName("测试门店");
        
        // 准备商品
        product = new Product();
        product.setId(1L);
        product.setCode("P001");
        product.setName("测试商品");
        product.setAvgCostPrice(BigDecimal.ZERO);
        
        // 准备创建命令
        createCommand = new PurchaseCreateCommand();
        createCommand.setStoreId(1L);
        createCommand.setSupplierId(1L);
        createCommand.setSupplierName("测试供应商");
        
        PurchaseItemDTO item = new PurchaseItemDTO();
        item.setProductId(1L);
        item.setQty(new BigDecimal("100.0000"));
        item.setPrice(new BigDecimal("10.50"));
        
        createCommand.setItems(Arrays.asList(item));
        createCommand.setRemark("测试采购单");
    }
    
    @Test
    void testCreate_Success() {
        // Given: 门店和商品都存在
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(domainService.generateOrderNo()).thenReturn("PO20240101000001");
        
        // create() 直接返回 order.getId()；单测里 save 是 mock，用 doAnswer 回填 id
        doAnswer(invocation -> {
            PurchaseOrder saved = invocation.getArgument(0);
            saved.setId(1L);
            return null;
        }).when(purchaseRepository).save(any(PurchaseOrder.class));
        
        // When: 创建采购单
        Long orderId = purchaseService.create(createCommand, 1L);
        
        // Then: 验证保存被调用
        verify(purchaseRepository, times(1)).save(any(PurchaseOrder.class));
        verify(itemRepository, times(1)).saveBatch(anyList());
        assertNotNull(orderId);
    }
    
    @Test
    void testCreate_StoreNotFound_ThrowsException() {
        // Given: 门店不存在
        when(storeRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When & Then: 应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            purchaseService.create(createCommand, 1L);
        });
        assertTrue(exception.getMessage().contains(PurchaseErrorCode.PRODUCT_OR_STORE_NOT_FOUND));
    }
    
    @Test
    void testCreate_ProductNotFound_ThrowsException() {
        // Given: 门店存在，商品不存在
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When & Then: 应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            purchaseService.create(createCommand, 1L);
        });
        assertTrue(exception.getMessage().contains(PurchaseErrorCode.PRODUCT_OR_STORE_NOT_FOUND));
    }
    
    @Test
    void testSubmit_Success() {
        // Given: 采购单存在且状态为待提交
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(1); // 待提交
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanAudit(order)).thenReturn(true);
        
        PurchaseSubmitCommand command = new PurchaseSubmitCommand();
        command.setOrderId(1L);
        
        // When: 提交采购单
        purchaseService.submit(command);
        
        // Then: 验证状态已更新为待入库
        verify(purchaseRepository, times(1)).save(argThat(o -> {
            PurchaseOrder po = (PurchaseOrder) o;
            return po.getStatus() == 2; // 待入库
        }));
    }
    
    @Test
    void testSubmit_OrderNotFound_ThrowsException() {
        // Given: 采购单不存在
        when(purchaseRepository.findById(1L)).thenReturn(Optional.empty());
        
        PurchaseSubmitCommand command = new PurchaseSubmitCommand();
        command.setOrderId(1L);
        
        // When & Then: 应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            purchaseService.submit(command);
        });
        assertTrue(exception.getMessage().contains(PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }
    
    @Test
    void testInbound_Success() {
        // Given: 采购单存在且状态为待入库
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setPurchaseNo("PO20240101000001");
        order.setStoreId(1L);
        order.setStatus(2); // 待入库
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setProductId(1L);
        item.setUnit("件");
        item.setQuantity(new BigDecimal("100.0000"));
        item.setBuyPrice(new BigDecimal("10.50"));
        when(itemRepository.findByOrderId(1L)).thenReturn(Arrays.asList(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(1L))
                .thenReturn(BigDecimal.ZERO, new BigDecimal("100.00000000"));
        
        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        
        // When: 入库
        purchaseService.inbound(command, 1L);
        
        // Then: legacy 入口转换为单收货批次，不再把采购单号伪装成供应商批号
        ArgumentCaptor<com.agri_system.domain.inventory.model.InventoryReceiptBatch> receiptBatchCaptor =
                ArgumentCaptor.forClass(com.agri_system.domain.inventory.model.InventoryReceiptBatch.class);
        verify(receiptBatchRepository).save(receiptBatchCaptor.capture());
        assertNull(receiptBatchCaptor.getValue().getSupplierBatchNo());
        assertTrue(receiptBatchCaptor.getValue().getInternalReceiptBatchNo().startsWith("INTERNAL-RECEIPT-"));
        
        // 验证采购单状态已更新为已入库
        verify(purchaseRepository, times(1)).save(argThat(o -> {
            PurchaseOrder po = (PurchaseOrder) o;
            return po.getStatus() == 3; // 已入库
        }));
        
        // 收货事实未接入 Task 4 余额前，不更新 avgCostPrice 展示缓存
        verify(productRepository, never()).save(any());
    }

    @Test
    void legacyInboundLocksOrderBeforeReadingItemsAndDoesNotUsePlainOrderLookup() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(BigDecimal.ZERO);

        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        purchaseService.inbound(command, 9L);

        InOrder reads = inOrder(purchaseRepository, itemRepository);
        reads.verify(purchaseRepository).findByIdForUpdate(1L);
        reads.verify(itemRepository).findByOrderId(1L);
        verify(purchaseRepository, never()).findById(1L);
        verify(purchaseRepository, times(1)).findByIdForUpdate(1L);
    }
    
    @Test
    void testInbound_AlreadyInbound_ThrowsException() {
        // Given: 采购单已入库
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(3); // 已入库
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        
        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        
        // When & Then: 应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            purchaseService.inbound(command, 1L);
        });
        assertTrue(exception.getMessage().contains(PurchaseErrorCode.PURCHASE_ORDER_ALREADY_INBOUND));
    }
    
    @Test
    void testInbound_DoesNotUpdateUnreliableAvgCostPriceCache() {
        // Given: 采购单存在，旧库存快照不属于新收货事实链
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setPurchaseNo("PO20240101000001");
        order.setStoreId(1L);
        order.setStatus(2); // 待入库
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setProductId(1L);
        item.setUnit("件");
        item.setQuantity(new BigDecimal("100.0000"));
        item.setBuyPrice(new BigDecimal("12.00")); // 新进价12.00
        when(itemRepository.findByOrderId(1L)).thenReturn(Arrays.asList(item));
        
        Product existingProduct = new Product();
        existingProduct.setId(1L);
        existingProduct.setAvgCostPrice(new BigDecimal("10.00")); // 旧平均成本10.00
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(1L)).thenReturn(BigDecimal.ZERO);
        
        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        
        // When: 入库
        purchaseService.inbound(command, 1L);
        
        // Then: Task 4 余额事实就绪前，收货不写不可靠的展示缓存
        assertEquals(new BigDecimal("10.00"), existingProduct.getAvgCostPrice());
        verify(productRepository, never()).save(any());
    }

    @Test
    void receivesOnePurchaseLineAsTwoSupplierBatchesWithDifferentDatesAndCosts() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(BigDecimal.ZERO);
        when(batchBalanceServiceProvider.getIfAvailable()).thenReturn(batchBalanceService);

        AtomicLong ids = new AtomicLong(100L);
        doAnswer(invocation -> {
            invocation.getArgument(0, com.agri_system.domain.inventory.model.InventoryReceipt.class)
                    .setId(ids.getAndIncrement());
            return null;
        }).when(receiptRepository).save(any());
        doAnswer(invocation -> {
            invocation.getArgument(0, com.agri_system.domain.inventory.model.InventoryReceiptLine.class)
                    .setId(ids.getAndIncrement());
            return null;
        }).when(receiptLineRepository).save(any());
        doAnswer(invocation -> {
            invocation.getArgument(0, com.agri_system.domain.inventory.model.InventoryReceiptBatch.class)
                    .setId(ids.getAndIncrement());
            return null;
        }).when(receiptBatchRepository).save(any());

        Long receiptId = purchaseService.receive(receiveCommand(
                batch("SUP-X", "6", "10.00", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
                batch("SUP-Y", "4", "12.00", LocalDate.of(2026, 2, 1), LocalDate.of(2027, 2, 1))), 9L);

        ArgumentCaptor<com.agri_system.domain.inventory.model.InventoryReceiptBatch> batchCaptor =
                ArgumentCaptor.forClass(com.agri_system.domain.inventory.model.InventoryReceiptBatch.class);
        verify(receiptBatchRepository, times(2)).save(batchCaptor.capture());
        assertEquals(2, batchCaptor.getAllValues().size());
        assertEquals("SUP-X", batchCaptor.getAllValues().get(0).getSupplierBatchNo());
        assertEquals(new BigDecimal("6.00000000"), batchCaptor.getAllValues().get(0).getOriginalQuantity());
        assertEquals(new BigDecimal("6.00000000"), batchCaptor.getAllValues().get(0).getBaseQuantity());
        assertEquals(new BigDecimal("10.00000000"), batchCaptor.getAllValues().get(0).getBaseUnitCost());
        assertEquals("KNOWN", batchCaptor.getAllValues().get(0).getDateStatus());
        assertEquals("SUP-Y", batchCaptor.getAllValues().get(1).getSupplierBatchNo());
        assertEquals(new BigDecimal("12.00000000"), batchCaptor.getAllValues().get(1).getBaseUnitCost());
        assertEquals("KNOWN", batchCaptor.getAllValues().get(1).getDateStatus());
        verify(batchBalanceService, times(2)).increaseFromReceipt(eq(receiptId), any());
        InOrder orderOfFacts = inOrder(receiptRepository, receiptLineRepository, receiptBatchRepository, batchBalanceService);
        orderOfFacts.verify(receiptRepository).save(any());
        orderOfFacts.verify(receiptLineRepository).save(any());
        orderOfFacts.verify(receiptBatchRepository, times(2)).save(any());
        orderOfFacts.verify(batchBalanceService, times(2)).increaseFromReceipt(eq(receiptId), any());
    }

    @Test
    void recordsUnknownDateInsteadOfTreatingMissingExpiryAsSafe() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(BigDecimal.ZERO);
        doAnswer(invocation -> {
            invocation.getArgument(0, com.agri_system.domain.inventory.model.InventoryReceipt.class).setId(100L);
            return null;
        }).when(receiptRepository).save(any());
        doAnswer(invocation -> {
            invocation.getArgument(0, com.agri_system.domain.inventory.model.InventoryReceiptLine.class).setId(101L);
            return null;
        }).when(receiptLineRepository).save(any());

        purchaseService.receive(receiveCommand(batch(null, "3", "10.00", null, null)), 9L);

        ArgumentCaptor<com.agri_system.domain.inventory.model.InventoryReceiptBatch> batchCaptor =
                ArgumentCaptor.forClass(com.agri_system.domain.inventory.model.InventoryReceiptBatch.class);
        verify(receiptBatchRepository).save(batchCaptor.capture());
        assertEquals("UNKNOWN", batchCaptor.getValue().getDateStatus());
        assertNull(batchCaptor.getValue().getProductionDate());
        assertNull(batchCaptor.getValue().getExpiryDate());
        assertTrue(batchCaptor.getValue().getInternalReceiptBatchNo().startsWith("INTERNAL-RECEIPT-"));
    }

    @Test
    void keepsPurchaseOrderPendingWhenReceiptIsPartial() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(BigDecimal.ZERO);

        purchaseService.receive(receiveCommand(batch("PARTIAL", "4", "10.00", null, null)), 9L);

        assertEquals(2, order.getStatus());
        verify(purchaseRepository, never()).save(order);
    }

    @Test
    void completesOnlyAfterPersistedReceiptBatchesReachPlannedQuantity() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        List<com.agri_system.domain.inventory.model.InventoryReceiptBatch> persistedBatches = new java.util.ArrayList<>();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        doAnswer(invocation -> persistedBatches.stream()
                .map(com.agri_system.domain.inventory.model.InventoryReceiptBatch::getBaseQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .when(receiptBatchRepository).sumBaseQuantityByPurchaseOrderItemId(11L);
        doAnswer(invocation -> {
            persistedBatches.add(invocation.getArgument(0));
            return null;
        }).when(receiptBatchRepository).save(any());

        purchaseService.receive(receiveCommand(batch("FIRST", "6", "10.00", null, null)), 9L);

        assertEquals(2, order.getStatus());
        verify(purchaseRepository, never()).save(order);

        purchaseService.receive(receiveCommand(batch("SECOND", "4", "10.00", null, null)), 9L);

        assertEquals(3, order.getStatus());
        verify(purchaseRepository).save(order);
        assertEquals(new BigDecimal("10.00000000"), persistedBatches.stream()
                .map(com.agri_system.domain.inventory.model.InventoryReceiptBatch::getBaseQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void locksPurchaseOrderBeforeCheckingReceiptStatusAndTotals() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(BigDecimal.ZERO);

        purchaseService.receive(receiveCommand(batch("LOCKED", "1", "10.00", null, null)), 9L);

        InOrder orderOfReads = inOrder(purchaseRepository, itemRepository, receiptBatchRepository);
        orderOfReads.verify(purchaseRepository).findByIdForUpdate(1L);
        orderOfReads.verify(itemRepository).findByOrderId(1L);
        orderOfReads.verify(receiptBatchRepository).sumBaseQuantityByPurchaseOrderItemId(11L);
        verify(purchaseRepository, never()).findById(1L);
    }

    @Test
    void storesNonBaseUnitConversionSnapshotForReceiptBatch() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        item.setUnit("箱");
        item.setQuantity(BigDecimal.ONE);
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubCaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(BigDecimal.ZERO);

        ReceiptBatchCommand receiptBatch = batch("CASE-10", "1", "100.00", null, null);
        receiptBatch.setOriginalUnit("箱");
        purchaseService.receive(receiveCommand(receiptBatch), 9L);

        ArgumentCaptor<com.agri_system.domain.inventory.model.InventoryReceiptBatch> captor =
                ArgumentCaptor.forClass(com.agri_system.domain.inventory.model.InventoryReceiptBatch.class);
        verify(receiptBatchRepository).save(captor.capture());
        assertEquals(new BigDecimal("1.00000000"), captor.getValue().getOriginalQuantity());
        assertEquals(new BigDecimal("100.00000000"), captor.getValue().getOriginalUnitCost());
        assertEquals(new BigDecimal("10.00000000"), captor.getValue().getBaseQuantity());
        assertEquals(new BigDecimal("10.00000000"), captor.getValue().getBaseUnitCost());
    }

    @Test
    void rejectsProductionDateWithoutExpiryBeforeWritingFacts() {
        assertRejectedDateBatch(batch("DATE", "1", "10.00", LocalDate.of(2026, 1, 1), null));
    }

    @Test
    void rejectsInvertedProductionAndExpiryDatesBeforeWritingFacts() {
        assertRejectedDateBatch(batch("DATE", "1", "10.00", LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 1)));
    }

    @Test
    void rejectsOverReceiptWithoutExplicitExceptionReasonBeforePersistingFacts() {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();
        when(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(11L)).thenReturn(new BigDecimal("8"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> purchaseService.receive(receiveCommand(batch("OVER", "3", "10.00", null, null)), 9L));

        assertTrue(exception.getMessage().contains("exceptionReason"));
        verifyNoInteractions(receiptRepository, receiptLineRepository);
        verify(receiptBatchRepository, never()).save(any());
    }

    private PurchaseOrder pendingInboundOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStoreId(1L);
        order.setSupplierId(2L);
        order.setPurchaseNo("PO-1");
        order.setStatus(2);
        return order;
    }

    private PurchaseOrderItem purchaseItem() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(11L);
        item.setPurchaseOrderId(1L);
        item.setProductId(1L);
        item.setUnit("件");
        item.setQuantity(new BigDecimal("10"));
        item.setBuyPrice(new BigDecimal("9.00"));
        return item;
    }

    private void stubPurchaseUnitConversion() {
        com.agri_system.domain.product.model.ProductUnit unit = new com.agri_system.domain.product.model.ProductUnit();
        unit.setId(31L);
        unit.setProductId(1L);
        unit.setUnitName("件");
        unit.setBaseQuantityPerUnit(BigDecimal.ONE);
        unit.setIsBaseUnit(1);
        unit.setAllowPurchase(1);
        unit.setStatus(1);
        when(productUnitService.requireEnabledUnit(1L, "件", ProductUnitService.UnitOperation.PURCHASE))
                .thenReturn(unit);
        when(productUnitService.getUnits(1L)).thenReturn(List.of(unit));
        when(productUnitService.toBaseQuantity(eq(1L), eq("件"), any(BigDecimal.class))).thenAnswer(invocation -> {
            BigDecimal originalQuantity = invocation.getArgument(2);
            return new UnitConversionSnapshot("件", originalQuantity, BigDecimal.ONE,
                    originalQuantity.setScale(8));
        });
    }

    private void stubCaseUnitConversion() {
        com.agri_system.domain.product.model.ProductUnit baseUnit = new com.agri_system.domain.product.model.ProductUnit();
        baseUnit.setId(31L);
        baseUnit.setProductId(1L);
        baseUnit.setUnitName("个");
        baseUnit.setBaseQuantityPerUnit(BigDecimal.ONE);
        baseUnit.setIsBaseUnit(1);
        baseUnit.setAllowPurchase(1);
        baseUnit.setStatus(1);
        com.agri_system.domain.product.model.ProductUnit caseUnit = new com.agri_system.domain.product.model.ProductUnit();
        caseUnit.setId(32L);
        caseUnit.setProductId(1L);
        caseUnit.setUnitName("箱");
        caseUnit.setBaseQuantityPerUnit(new BigDecimal("10"));
        caseUnit.setAllowPurchase(1);
        caseUnit.setStatus(1);
        when(productUnitService.requireEnabledUnit(1L, "箱", ProductUnitService.UnitOperation.PURCHASE))
                .thenReturn(caseUnit);
        when(productUnitService.getUnits(1L)).thenReturn(List.of(baseUnit, caseUnit));
        when(productUnitService.toBaseQuantity(1L, "箱", BigDecimal.ONE))
                .thenReturn(new UnitConversionSnapshot("箱", BigDecimal.ONE, new BigDecimal("10"), new BigDecimal("10.00000000")));
    }

    private void assertRejectedDateBatch(ReceiptBatchCommand receiptBatch) {
        PurchaseOrder order = pendingInboundOrder();
        PurchaseOrderItem item = purchaseItem();
        when(purchaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        stubPurchaseUnitConversion();

        assertThrows(IllegalArgumentException.class,
                () -> purchaseService.receive(receiveCommand(receiptBatch), 9L));

        verifyNoInteractions(receiptRepository, receiptLineRepository);
        verify(receiptBatchRepository, never()).save(any());
    }

    private ReceivePurchaseCommand receiveCommand(ReceiptBatchCommand... batches) {
        ReceivePurchaseCommand command = new ReceivePurchaseCommand();
        command.setPurchaseOrderId(1L);
        ReceivePurchaseCommand.ReceiptLineCommand line = new ReceivePurchaseCommand.ReceiptLineCommand();
        line.setPurchaseOrderItemId(11L);
        line.setProductId(1L);
        line.setBatches(Arrays.asList(batches));
        command.setLines(List.of(line));
        return command;
    }

    private ReceiptBatchCommand batch(String supplierBatchNo, String quantity, String unitCost,
                                      LocalDate productionDate, LocalDate expiryDate) {
        ReceiptBatchCommand batch = new ReceiptBatchCommand();
        batch.setSupplierBatchNo(supplierBatchNo);
        batch.setOriginalUnit("件");
        batch.setOriginalQuantity(new BigDecimal(quantity));
        batch.setOriginalUnitCost(new BigDecimal(unitCost));
        batch.setProductionDate(productionDate);
        batch.setExpiryDate(expiryDate);
        return batch;
    }
}
