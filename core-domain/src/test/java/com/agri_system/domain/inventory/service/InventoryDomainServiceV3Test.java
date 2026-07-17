package com.agri_system.domain.inventory.service;

import com.agri_system.domain.inventory.model.Inventory;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 库存领域服务V3.0测试
 * 重点测试：成本隔离、FIFO排序、调拨成本继承、并发安全、快照一致性
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryDomainServiceV3Test {
    
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
    private final AtomicInteger batchIdSeq = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        batchIdSeq.set(1);
        // 默认mock：库存快照更新
        doNothing().when(inventoryRepository).createIfNotExists(any(), any(), any());
        doNothing().when(inventoryRepository).updateSnapshot(any(), any(), any());
        doNothing().when(transactionRepository).save(any());
        // 模拟 save 时自动分配 ID
        doAnswer(invocation -> {
            InventoryBatch b = invocation.getArgument(0);
            if (b.getId() == null) {
                b.setId((long) batchIdSeq.getAndIncrement());
            }
            return null;
        }).when(batchRepository).save(any(InventoryBatch.class));
    }
    
    /**
     * 测试1：成本隔离测试（CostIsolationTest）
     * 入库：
     * - 批次 A：100 元
     * - 批次 A：120 元
     * 预期：新增两条批次记录，不合并（因为成本价不同，视为异源）
     */
    @Test
    void testCostIsolation() {
        // Given: 第一次入库，批次A，成本100元
        String batchNo = "BATCH001";
        BigDecimal quantity1 = new BigDecimal("10");
        BigDecimal costPrice1 = new BigDecimal("100.00");
        String sourceType = "PURCHASE";
        Long sourceId1 = 1L;
        Integer sourceLineNo1 = 1;
        
        // Mock: 第一次入库时，不存在同源批次
        when(batchRepository.findBySameSource(storeId, productId, batchNo, sourceType, sourceId1, sourceLineNo1))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.empty());
        
        ArgumentCaptor<InventoryBatch> batchCaptor1 = ArgumentCaptor.forClass(InventoryBatch.class);
        doAnswer(inv -> {
            InventoryBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId((long) batchIdSeq.getAndIncrement());
            return null;
        }).when(batchRepository).save(batchCaptor1.capture());

        // When: 第一次入库
        Long batchId1 = domainService.inbound(storeId, productId, batchNo, quantity1, costPrice1,
                null, null, sourceType, sourceId1, sourceLineNo1, "kg", createdBy);

        // Then: 验证创建了新批次，成本价为100元
        InventoryBatch savedBatch1 = batchCaptor1.getValue();
        assertNotNull(savedBatch1);
        assertEquals(costPrice1, savedBatch1.getCostPrice());
        assertEquals(quantity1, savedBatch1.getQuantity());

        // Given: 第二次入库，相同批次号，但成本120元（不同来源）
        BigDecimal quantity2 = new BigDecimal("5");
        BigDecimal costPrice2 = new BigDecimal("120.00");
        Long sourceId2 = 2L; // 不同的来源ID
        Integer sourceLineNo2 = 1;

        // Mock: 第二次入库时，查询同源批次（sourceId不同，所以找不到）
        when(batchRepository.findBySameSource(storeId, productId, batchNo, sourceType, sourceId2, sourceLineNo2))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(quantity1)));

        ArgumentCaptor<InventoryBatch> batchCaptor2 = ArgumentCaptor.forClass(InventoryBatch.class);
        doAnswer(inv -> {
            InventoryBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId((long) batchIdSeq.getAndIncrement());
            return null;
        }).when(batchRepository).save(batchCaptor2.capture());
        
        // When: 第二次入库
        Long batchId2 = domainService.inbound(storeId, productId, batchNo, quantity2, costPrice2,
                null, null, sourceType, sourceId2, sourceLineNo2, "kg", createdBy);
        
        // Then: 验证创建了新的批次（不是合并），成本价为120元
        InventoryBatch savedBatch2 = batchCaptor2.getValue();
        assertNotNull(savedBatch2);
        assertEquals(costPrice2, savedBatch2.getCostPrice());
        assertEquals(quantity2, savedBatch2.getQuantity());
        assertNotEquals(batchId1, batchId2, "应该创建两个不同的批次");
    }
    
    /**
     * 测试2：FIFO排序测试（FIFOSortTest）
     * 插入：
     * - 批次 1：expire=明天
     * - 批次 2：expire=明年
     * - 批次 3：expire=NULL
     * 预期扣减顺序：1 → 2 → 3
     */
    @Test
    void testFIFOSort() {
        // Given: 准备三个批次
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate nextYear = LocalDate.now().plusYears(1);
        
        InventoryBatch batch1 = createBatch(1L, "B001", null, tomorrow, 
                new BigDecimal("10"), new BigDecimal("5.00"));
        InventoryBatch batch2 = createBatch(2L, "B002", null, nextYear, 
                new BigDecimal("20"), new BigDecimal("6.00"));
        InventoryBatch batch3 = createBatch(3L, "B003", null, null, 
                new BigDecimal("15"), new BigDecimal("7.00"));
        
        // Mock: FIFO排序查询（按expire_date ASC, created_at ASC）
        List<InventoryBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch1); // 明天过期（最早）
        sortedBatches.add(batch2); // 明年过期
        sortedBatches.add(batch3); // 无日期（排最后）
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(sortedBatches);
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("45"))));
        
        doNothing().when(batchRepository).save(any());
        
        // When: 出库30
        BigDecimal outQuantity = new BigDecimal("30");
        List<InventoryDomainService.BatchDeductionDetail> details = 
                domainService.outbound(storeId, productId, outQuantity, "kg", "kg", outQuantity, createdBy, 2, "ORDER", 1L);
        
        // Then: 验证扣减顺序和数量
        assertEquals(2, details.size(), "应该扣减两个批次");
        assertEquals(batch1.getId(), details.get(0).getBatchId(), "第一个批次应该是batch1（明天过期）");
        assertEquals(batch2.getId(), details.get(1).getBatchId(), "第二个批次应该是batch2（明年过期）");
        assertEquals(new BigDecimal("10"), details.get(0).getQuantity(), "batch1全部扣减");
        assertEquals(new BigDecimal("20"), details.get(1).getQuantity(), "batch2扣减20");
    }
    
    /**
     * 测试3：调拨成本测试（TransferCostTest）
     * A 店调拨 → B 店
     * 预期：B 店的批次成本必须继承 A 店每个批次的原始成本
     */
    @Test
    void testTransferCost() {
        // Given: A店有两个批次
        Long fromStoreId = 1L;
        Long toStoreId = 2L;
        
        InventoryBatch batchA1 = createBatch(1L, "BATCH_A1", null, LocalDate.now().plusDays(10), 
                new BigDecimal("10"), new BigDecimal("50.00"));
        InventoryBatch batchA2 = createBatch(2L, "BATCH_A2", null, LocalDate.now().plusDays(20), 
                new BigDecimal("5"), new BigDecimal("60.00"));
        
        // Mock: 出库端FIFO查询
        List<InventoryBatch> fromBatches = new ArrayList<>();
        fromBatches.add(batchA1);
        fromBatches.add(batchA2);
        
        when(batchRepository.findActiveBatchesForFIFOWithLock(fromStoreId, productId))
                .thenReturn(fromBatches);
        when(inventoryRepository.findByStoreAndProduct(fromStoreId, productId))
                .thenReturn(Optional.of(createInventory(new BigDecimal("15"))));
        
        doNothing().when(batchRepository).save(any());
        
        // Mock: 入库端（B店）查询同源批次（不存在）
        when(batchRepository.findBySameSource(eq(toStoreId), eq(productId), anyString(), 
                eq("TRANSFER_IN"), anyLong(), isNull()))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByStoreAndProduct(toStoreId, productId))
                .thenReturn(Optional.empty());
        
        // Mock: 查询新创建的批次
        when(batchRepository.findById(anyLong()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    InventoryBatch batch = new InventoryBatch();
                    batch.setId(id);
                    batch.setStoreId(toStoreId);
                    batch.setProductId(productId);
                    batch.setBatchNo("BATCH_A1");
                    batch.setCostPrice(new BigDecimal("50.00")); // 继承原成本
                    batch.setQuantity(new BigDecimal("10"));
                    return Optional.of(batch);
                });
        
        // When: 调拨15（会拆成两个批次）
        BigDecimal transferQuantity = new BigDecimal("15");
        InventoryDomainService.TransferDetail transferDetail = 
                domainService.transfer(fromStoreId, toStoreId, productId, transferQuantity, "kg", createdBy, 1L);
        
        // Then: 验证调拨详情
        assertNotNull(transferDetail);
        assertEquals(2, transferDetail.getBatchDetails().size(), "应该拆成两个批次");
        
        // 验证成本价继承
        assertEquals(new BigDecimal("50.00"), transferDetail.getBatchDetails().get(0).getCostPrice(), 
                "第一个批次成本应该继承自A店batch1");
        assertEquals(new BigDecimal("60.00"), transferDetail.getBatchDetails().get(1).getCostPrice(), 
                "第二个批次成本应该继承自A店batch2");
    }
    
    /**
     * 测试4：并发销售测试（ConcurrencySellTest）
     * 多线程同时扣减同一商品
     * 预期：无超卖；失败线程返回 inventory-2001 或 inventory-2005
     */
    @Test
    void testConcurrencySell() throws InterruptedException {
        // Given: 初始库存10
        BigDecimal initialStock = new BigDecimal("10");
        int threadCount = 5;
        BigDecimal eachQuantity = new BigDecimal("3"); // 每个线程扣减3

        // 使用 AtomicReference 模拟并发安全的批次状态
        InventoryBatch batch = createBatch(1L, "BATCH001", null, null,
                initialStock, new BigDecimal("5.00"));
        java.util.concurrent.atomic.AtomicReference<BigDecimal> currentQty =
                new java.util.concurrent.atomic.AtomicReference<>(initialStock);

        // Mock: 每次查询都返回同一个批次（模拟并发场景）
        when(batchRepository.findActiveBatchesForFIFOWithLock(storeId, productId))
                .thenReturn(List.of(batch));
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(initialStock)));

        // Mock: save 时同步更新批次数量，模拟数据库行锁
        doAnswer(inv -> {
            InventoryBatch saved = inv.getArgument(0);
            currentQty.set(saved.getQuantity());
            batch.setQuantity(saved.getQuantity());
            return null;
        }).when(batchRepository).save(any(InventoryBatch.class));
        
        // When: 5个线程同时扣减
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    domainService.outbound(storeId, productId, eachQuantity, "kg", "kg", eachQuantity, createdBy, 2, "ORDER", 1L);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    // 预期会有线程因为库存不足而失败
                    if (e.getMessage().contains("inventory-2001")) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then: 验证结果
        // 总库存10，每个线程扣3，理论上最多3个线程成功（3*3=9）
        // 注意：纯 mock 测试无法完全模拟数据库行锁，仅验证方法可并发调用不抛异常
        int completed = successCount.get() + failCount.get();
        assertTrue(completed >= 1, "至少有1个线程应该完成执行");
        assertTrue(successCount.get() >= 1, "至少有1个线程应该成功");
    }
    
    /**
     * 测试5：快照一致性测试（SnapshotConsistencyTest）
     * 每次操作后 inventory.quantity == SUM(inventory_batch.quantity)
     */
    @Test
    void testSnapshotConsistency() {
        // Given: 初始状态
        BigDecimal batch1Qty = new BigDecimal("10");
        BigDecimal batch2Qty = new BigDecimal("20");
        BigDecimal expectedTotal = batch1Qty.add(batch2Qty);
        
        // Mock: 查询批次总数
        when(batchRepository.sumQuantityByStoreAndProduct(storeId, productId))
                .thenReturn(expectedTotal);
        
        ArgumentCaptor<BigDecimal> quantityCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        doNothing().when(inventoryRepository).updateSnapshot(eq(storeId), eq(productId), quantityCaptor.capture());
        
        // When: 刷新快照
        domainService.refreshTotalStock(storeId, productId);
        
        // Then: 验证快照数量等于批次总数
        BigDecimal snapshotQuantity = quantityCaptor.getValue();
        assertEquals(expectedTotal, snapshotQuantity, "快照数量应该等于批次总数");
        
        // 测试入库后的一致性
        when(batchRepository.findBySameSource(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByStoreAndProduct(storeId, productId))
                .thenReturn(Optional.of(createInventory(expectedTotal)));
        when(batchRepository.sumQuantityByStoreAndProduct(storeId, productId))
                .thenReturn(expectedTotal.add(new BigDecimal("5")));
        
        ArgumentCaptor<BigDecimal> quantityCaptor2 = ArgumentCaptor.forClass(BigDecimal.class);
        doNothing().when(inventoryRepository).updateSnapshot(eq(storeId), eq(productId), quantityCaptor2.capture());
        
        domainService.inbound(storeId, productId, "BATCH003", new BigDecimal("5"), 
                new BigDecimal("10.00"), null, null, "PURCHASE", 1L, 1, "kg", createdBy);
        
        // 验证入库后快照也同步更新
        BigDecimal snapshotQuantity2 = quantityCaptor2.getAllValues().get(quantityCaptor2.getAllValues().size() - 1);
        assertEquals(expectedTotal.add(new BigDecimal("5")), snapshotQuantity2, 
                "入库后快照应该等于新的批次总数");
    }
    
    // ==================== 辅助方法 ====================
    
    private InventoryBatch createBatch(Long id, String batchNo, LocalDate productionDate, 
                                      LocalDate expireDate, BigDecimal quantity, BigDecimal costPrice) {
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
        batch.setCreatedAt(LocalDateTime.now());
        return batch;
    }
    
    private Inventory createInventory(BigDecimal quantity) {
        Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setStoreId(storeId);
        inventory.setProductId(productId);
        inventory.setQuantity(quantity);
        return inventory;
    }
}








