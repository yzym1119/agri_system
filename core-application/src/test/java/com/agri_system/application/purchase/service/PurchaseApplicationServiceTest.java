package com.agri_system.application.purchase.service;

import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.purchase.dto.command.PurchaseCreateCommand;
import com.agri_system.application.purchase.dto.command.PurchaseInboundCommand;
import com.agri_system.application.purchase.dto.command.PurchaseItemDTO;
import com.agri_system.application.purchase.dto.command.PurchaseSubmitCommand;
import com.agri_system.application.purchase.exception.PurchaseErrorCode;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

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
    private InventoryApplicationService inventoryService;
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private StoreRepository storeRepository;
    
    @Mock
    private InventoryBatchRepository batchRepository;
    
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
        
        PurchaseOrder savedOrder = new PurchaseOrder();
        savedOrder.setId(1L);
        savedOrder.setPurchaseNo("PO20240101000001");
        when(purchaseRepository.findById(any())).thenReturn(Optional.of(savedOrder));
        
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
        when(domainService.checkCanSubmit(order)).thenReturn(true);
        
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
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("100.0000"));
        item.setBuyPrice(new BigDecimal("10.50"));
        when(itemRepository.findByOrderId(1L)).thenReturn(Arrays.asList(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.sumQuantityByProduct(1L)).thenReturn(BigDecimal.ZERO);
        when(inventoryService.inbound(any(InventoryInCommand.class), eq(1L))).thenReturn(1L);
        
        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        
        // When: 入库
        purchaseService.inbound(command, 1L);
        
        // Then: 验证库存服务被调用，且批次号 = 采购单号
        ArgumentCaptor<InventoryInCommand> captor = ArgumentCaptor.forClass(InventoryInCommand.class);
        verify(inventoryService, times(1)).inbound(captor.capture(), eq(1L));
        
        InventoryInCommand capturedCommand = captor.getValue();
        assertEquals("PO20240101000001", capturedCommand.getBatchNo()); // 批次号 = 采购单号
        assertEquals("PURCHASE", capturedCommand.getSourceType());
        assertEquals(1L, capturedCommand.getSourceId());
        assertEquals(1, capturedCommand.getSourceLineNo());
        
        // 验证采购单状态已更新为已入库
        verify(purchaseRepository, times(1)).save(argThat(o -> {
            PurchaseOrder po = (PurchaseOrder) o;
            return po.getStatus() == 3; // 已入库
        }));
        
        // 验证商品平均成本已更新
        verify(productRepository, times(1)).save(argThat(p -> {
            Product prod = (Product) p;
            return prod.getAvgCostPrice() != null && 
                   prod.getAvgCostPrice().compareTo(new BigDecimal("10.50")) == 0;
        }));
    }
    
    @Test
    void testInbound_AlreadyInbound_ThrowsException() {
        // Given: 采购单已入库
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setStatus(3); // 已入库
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(order));
        
        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        
        // When & Then: 应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            purchaseService.inbound(command, 1L);
        });
        assertTrue(exception.getMessage().contains(PurchaseErrorCode.PURCHASE_ORDER_ALREADY_INBOUND));
    }
    
    @Test
    void testInbound_UpdateAvgCostPrice_WithExistingStock() {
        // Given: 采购单存在，商品已有库存
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setPurchaseNo("PO20240101000001");
        order.setStoreId(1L);
        order.setStatus(2); // 待入库
        when(purchaseRepository.findById(1L)).thenReturn(Optional.of(order));
        when(domainService.checkCanInbound(order)).thenReturn(true);
        
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(1L);
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("100.0000"));
        item.setBuyPrice(new BigDecimal("12.00")); // 新进价12.00
        when(itemRepository.findByOrderId(1L)).thenReturn(Arrays.asList(item));
        
        Product existingProduct = new Product();
        existingProduct.setId(1L);
        existingProduct.setAvgCostPrice(new BigDecimal("10.00")); // 旧平均成本10.00
        when(productRepository.findById(1L)).thenReturn(Optional.of(existingProduct));
        when(batchRepository.sumQuantityByProduct(1L)).thenReturn(new BigDecimal("200.0000")); // 已有200库存
        when(inventoryService.inbound(any(InventoryInCommand.class), eq(1L))).thenReturn(1L);
        
        PurchaseInboundCommand command = new PurchaseInboundCommand();
        command.setOrderId(1L);
        
        // When: 入库
        purchaseService.inbound(command, 1L);
        
        // Then: 验证平均成本计算正确
        // 公式: (10.00 * 200 + 12.00 * 100) / (200 + 100) = (2000 + 1200) / 300 = 10.67
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(1)).save(productCaptor.capture());
        
        Product savedProduct = productCaptor.getValue();
        BigDecimal expectedAvgCost = new BigDecimal("10.67");
        assertTrue(savedProduct.getAvgCostPrice().compareTo(expectedAvgCost) == 0 ||
                   savedProduct.getAvgCostPrice().compareTo(new BigDecimal("10.66")) == 0); // 允许四舍五入差异
    }
}
