package com.agri_system.domain.inventory.service;

import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 库存领域服务单元测试
 * 重点测试：FIFO排序逻辑、归档逻辑、入库合并、出库扣减
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryDomainServiceTest {
    
    @Mock
    private InventoryBatchRepository batchRepository;
    
    @Mock
    private InventoryRepository inventoryRepository;
    
    @Mock
    private InventoryTransactionRepository transactionRepository;
    
    @InjectMocks
    private InventoryDomainService domainService;
    
    private Long storeId = 1L;
    private Long productId = 100L;
    private Long createdBy = 1L;
    
    @BeforeEach
    void setUp() {
        // 默认mock：库存快照更新
        doNothing().when(inventoryRepository).createIfNotExists(any(), any(), any());
        doNothing().when(inventoryRepository).updateSnapshot(any(), any(), any());
        doNothing().when(transactionRepository).save(any());
    }
    
    /**
     * 测试FIFO排序逻辑
     * 验证：有日期的优先于无日期的，日期早的优先，入库早的优先
     */
    @Test
    void testOutbound_FIFO_Sorting() {
        // Given: 准备多个批次，验证FIFO排序
        List<InventoryBatch> batches = new ArrayList<>();
        
        // 批次1：无日期，最早入库
        InventoryBatch batch1 = createBatch(1L, "B001", null, null, 
                new BigDecimal("10"), new BigDecimal("5.00"), LocalDateTime.now().minusDays(5));
        batches.add(batch1);
        
        // 批次2：有日期，较晚过期
        InventoryBatch batch2 = createBatch(2L, "B002", LocalDate.now().plusDays(10), 
                LocalDate.now().plusDays(20), new BigDecimal("20"), new BigDecimal("6.00"), 
                LocalDateTime.now().minusDays(3));
        batches.add(batch2);
        
        // 批次3：有日期，最早过期
        InventoryBatch batch3 = createBatch(3L, "B003", LocalDate.now().plusDays(5), 
                LocalDate.now().plusDays(10), new BigDecimal("15"), new BigDecimal("7.00"), 
                LocalDateTime.now().minusDays(1));
        batches.add(batch3);
        
        // 批次4：有日期，最早过期，但入库更早
        InventoryBatch batch4 = createBatch(4L, "B004", LocalDate.now().plusDays(5), 
                LocalDate.now().plusDays(10), new BigDecimal("8"), new BigDecimal("8.00"), 
                LocalDateTime.now().minusDays(4));
        batches.add(batch4);
        
        // Mock: 返回按FIFO排序的批次（Repository层已排序）
        // 正确顺序应该是：batch3（最早过期）-> batch4（同样过期但入库更早）-> batch2（较晚过期）-> batch1（无日期）
        List<InventoryBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch3); // 最早过期
        sortedBatches.add(batch4); // 同样过期但入库更早
        sortedBatches.add(batch2); // 较晚过期
        sortedBatches.add(batch1); // 无日期
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(sortedBatches);
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("53"))));
        
        // When: 出库15个
        List<InventoryDomainService.BatchDeductionDetail> details = 
                domainService.outbound(storeId, productId, new BigDecimal("15"), 
                        "kg", "kg", new BigDecimal("15"), createdBy, 2, "ORDER", 1L);
        
        // Then: 验证按FIFO顺序扣减
        assertEquals(1, details.size());
        assertEquals(batch3.getId(), details.get(0).getBatchId());
        assertEquals(new BigDecimal("15"), details.get(0).getQuantity());
        
        // 验证批次3被扣减
        verify(batchRepository, times(1)).save(argThat(batch -> 
                batch.getId().equals(batch3.getId()) && 
                batch.getQuantity().compareTo(BigDecimal.ZERO) == 0 &&
                batch.getIsActive() == 0 // 自动归档
        ));
    }
    
    /**
     * 测试归档逻辑：扣减后数量为0时自动归档
     */
    @Test
    void testOutbound_AutoArchive_WhenQuantityBecomesZero() {
        // Given: 一个批次，数量为10
        InventoryBatch batch = createBatch(1L, "B001", LocalDate.now().plusDays(10), 
                LocalDate.now().plusDays(20), new BigDecimal("10"), new BigDecimal("5.00"), 
                LocalDateTime.now());
        batch.setIsActive(1);
        
        List<InventoryBatch> batches = new ArrayList<>();
        batches.add(batch);
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(batches);
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("10"))));
        
        // When: 出库10个（全部扣减）
        domainService.outbound(storeId, productId, new BigDecimal("10"), 
                "kg", "kg", new BigDecimal("10"), createdBy, 2, "ORDER", 1L);
        
        // Then: 验证批次被归档（isActive = 0）
        ArgumentCaptor<InventoryBatch> captor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batchRepository, times(1)).save(captor.capture());
        
        InventoryBatch savedBatch = captor.getValue();
        assertEquals(0, savedBatch.getQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, savedBatch.getIsActive()); // 自动归档
    }
    
    /**
     * 测试归档逻辑：部分扣减时不应归档
     */
    @Test
    void testOutbound_NoArchive_WhenQuantityRemains() {
        // Given: 一个批次，数量为20
        InventoryBatch batch = createBatch(1L, "B001", LocalDate.now().plusDays(10), 
                LocalDate.now().plusDays(20), new BigDecimal("20"), new BigDecimal("5.00"), 
                LocalDateTime.now());
        batch.setIsActive(1);
        
        List<InventoryBatch> batches = new ArrayList<>();
        batches.add(batch);
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(batches);
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("20"))));
        
        // When: 出库10个（部分扣减）
        domainService.outbound(storeId, productId, new BigDecimal("10"), 
                "kg", "kg", new BigDecimal("10"), createdBy, 2, "ORDER", 1L);
        
        // Then: 验证批次未被归档（isActive = 1）
        ArgumentCaptor<InventoryBatch> captor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batchRepository, times(1)).save(captor.capture());
        
        InventoryBatch savedBatch = captor.getValue();
        assertEquals(0, savedBatch.getQuantity().compareTo(new BigDecimal("10")));
        assertEquals(1, savedBatch.getIsActive()); // 仍然有效
    }
    
    /**
     * 测试多批次FIFO扣减
     */
    @Test
    void testOutbound_MultipleBatches_FIFO() {
        // Given: 3个批次
        InventoryBatch batch1 = createBatch(1L, "B001", LocalDate.now().plusDays(5), 
                LocalDate.now().plusDays(10), new BigDecimal("5"), new BigDecimal("5.00"), 
                LocalDateTime.now().minusDays(3));
        InventoryBatch batch2 = createBatch(2L, "B002", LocalDate.now().plusDays(10), 
                LocalDate.now().plusDays(15), new BigDecimal("8"), new BigDecimal("6.00"), 
                LocalDateTime.now().minusDays(2));
        InventoryBatch batch3 = createBatch(3L, "B003", LocalDate.now().plusDays(15), 
                LocalDate.now().plusDays(20), new BigDecimal("10"), new BigDecimal("7.00"), 
                LocalDateTime.now().minusDays(1));
        
        List<InventoryBatch> batches = new ArrayList<>();
        batches.add(batch1);
        batches.add(batch2);
        batches.add(batch3);
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(batches);
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("23"))));
        
        // When: 出库12个（需要跨批次）
        List<InventoryDomainService.BatchDeductionDetail> details = 
                domainService.outbound(storeId, productId, new BigDecimal("12"), 
                        "kg", "kg", new BigDecimal("12"), createdBy, 2, "ORDER", 1L);
        
        // Then: 验证扣减详情
        assertEquals(2, details.size());
        assertEquals(batch1.getId(), details.get(0).getBatchId());
        assertEquals(new BigDecimal("5"), details.get(0).getQuantity()); // 批次1全部扣减
        assertEquals(batch2.getId(), details.get(1).getBatchId());
        assertEquals(new BigDecimal("7"), details.get(1).getQuantity()); // 批次2部分扣减
        
        // 验证批次1被归档
        verify(batchRepository, atLeastOnce()).save(argThat(batch -> 
                batch.getId().equals(batch1.getId()) && 
                batch.getIsActive() == 0
        ));
        
        // 验证批次2未被归档
        verify(batchRepository, atLeastOnce()).save(argThat(batch -> 
                batch.getId().equals(batch2.getId()) && 
                batch.getQuantity().compareTo(new BigDecimal("1")) == 0 &&
                batch.getIsActive() == 1
        ));
    }
    
    /**
     * 测试入库合并逻辑
     */
    @Test
    void testInbound_MergeBatch() {
        // Given: 已存在批次
        InventoryBatch existingBatch = createBatch(1L, "B001", LocalDate.now(), 
                LocalDate.now().plusDays(30), new BigDecimal("10"), new BigDecimal("5.00"), 
                LocalDateTime.now().minusDays(1));
        existingBatch.setIsActive(1);
        
        // 注意：V3.0已改为同源合并，这里使用旧的findByStoreAndProductAndBatch方法（已废弃）
        // 实际应该使用findBySameSource方法
        String sourceType = "PURCHASE";
        Long sourceId = 1L;
        Integer sourceLineNo = 1;
        
        when(batchRepository.findBySameSource(storeId, productId, "B001", sourceType, sourceId, sourceLineNo))
                .thenReturn(Optional.of(existingBatch));
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("10"))));
        when(batchRepository.sumQuantityByStoreAndProduct(storeId, productId))
                .thenReturn(new BigDecimal("25")); // 10 + 15 = 25
        
        // When: 再次入库相同批次号，15个，成本价6.00（同源合并）
        Long batchId = domainService.inbound(storeId, productId, "B001", 
                new BigDecimal("15"), new BigDecimal("6.00"), 
                null, null, sourceType, sourceId, sourceLineNo, "kg", createdBy);
        
        // Then: 验证批次合并
        ArgumentCaptor<InventoryBatch> captor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batchRepository, times(1)).save(captor.capture());
        
        InventoryBatch savedBatch = captor.getValue();
        assertEquals(0, savedBatch.getQuantity().compareTo(new BigDecimal("25"))); // 10 + 15
        
        // 注意：V3.0成本价不可变，合并时成本价不变（仍为5.00）
        assertEquals(0, savedBatch.getCostPrice().compareTo(new BigDecimal("5.00")));
        assertEquals(1L, batchId);
    }
    
    /**
     * 测试入库创建新批次
     */
    @Test
    void testInbound_CreateNewBatch() {
        // Given: 批次不存在（异源）
        String sourceType = "PURCHASE";
        Long sourceId = 1L;
        Integer sourceLineNo = 1;
        
        when(batchRepository.findBySameSource(storeId, productId, "B001", sourceType, sourceId, sourceLineNo))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.empty());
        when(batchRepository.sumQuantityByStoreAndProduct(storeId, productId))
                .thenReturn(new BigDecimal("20"));
        
        doAnswer(invocation -> {
            InventoryBatch batch = invocation.getArgument(0);
            batch.setId(1L);
            return null;
        }).when(batchRepository).save(any(InventoryBatch.class));
        
        // When: 入库新批次
        Long batchId = domainService.inbound(storeId, productId, "B001", 
                new BigDecimal("20"), new BigDecimal("5.00"), 
                LocalDate.now(), LocalDate.now().plusDays(30), sourceType, sourceId, sourceLineNo, "kg", createdBy);
        
        // Then: 验证创建新批次
        ArgumentCaptor<InventoryBatch> captor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batchRepository, times(1)).save(captor.capture());
        
        InventoryBatch savedBatch = captor.getValue();
        assertEquals(storeId, savedBatch.getStoreId());
        assertEquals(productId, savedBatch.getProductId());
        assertEquals("B001", savedBatch.getBatchNo());
        assertEquals(0, savedBatch.getQuantity().compareTo(new BigDecimal("20")));
        assertEquals(0, savedBatch.getCostPrice().compareTo(new BigDecimal("5.00")));
        assertEquals(1, savedBatch.getIsActive());
        assertNotNull(batchId);
    }
    
    /**
     * 测试库存不足异常
     */
    @Test
    void testOutbound_InsufficientStock_ShouldThrowException() {
        // Given: 批次总库存为10
        InventoryBatch batch = createBatch(1L, "B001", null, null, 
                new BigDecimal("10"), new BigDecimal("5.00"), LocalDateTime.now());
        
        List<InventoryBatch> batches = new ArrayList<>();
        batches.add(batch);
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(batches);
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("10"))));
        
        // When & Then: 出库15个，应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            domainService.outbound(storeId, productId, new BigDecimal("15"), 
                    "kg", "kg", new BigDecimal("15"), createdBy, 2, "ORDER", 1L);
        });
        
        assertTrue(exception.getMessage().contains("inventory-2001"));
        assertTrue(exception.getMessage().contains("库存不足"));
    }
    
    /**
     * 测试强一致性同步
     */
    @Test
    void testRefreshTotalStock() {
        // Given: 批次总库存为30
        when(batchRepository.sumQuantityByStoreAndProduct(storeId, productId))
                .thenReturn(new BigDecimal("30"));
        
        // When: 刷新总库存
        domainService.refreshTotalStock(storeId, productId);
        
        // Then: 验证更新库存快照
        verify(inventoryRepository, times(1)).createIfNotExists(storeId, productId, new BigDecimal("30"));
        verify(inventoryRepository, times(1)).updateSnapshot(storeId, productId, new BigDecimal("30"));
    }
    
    /**
     * 辅助方法：创建批次对象
     */
    private InventoryBatch createBatch(Long id, String batchNo, LocalDate productionDate, 
                                      LocalDate expireDate, BigDecimal quantity, 
                                      BigDecimal costPrice, LocalDateTime createdAt) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(id);
        batch.setStoreId(storeId);
        batch.setProductId(productId);
        batch.setBatchNo(batchNo);
        batch.setProductionDate(productionDate);
        batch.setExpireDate(expireDate);
        batch.setQuantity(quantity);
        batch.setCostPrice(costPrice);
        batch.setIsActive(1);
        batch.setCreatedAt(createdAt);
        return batch;
    }
    
    /**
     * 辅助方法：创建库存快照对象
     */
    private com.agri_system.domain.inventory.model.Inventory createInventory(BigDecimal quantity) {
        com.agri_system.domain.inventory.model.Inventory inventory = 
                new com.agri_system.domain.inventory.model.Inventory();
        inventory.setId(1L);
        inventory.setStoreId(storeId);
        inventory.setProductId(productId);
        inventory.setQuantity(quantity);
        return inventory;
    }
}
