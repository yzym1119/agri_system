package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;
import com.agri_system.infrastructure.inventory.mapper.InventoryBalanceFeatureStatusMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 库存模式切换状态仓储单元测试：按 (storeId, productId) upsert 与激活/回滚语义。
 * 真实 upsert 语义在 10c 以 MySQL 端到端覆盖。
 */
@ExtendWith(MockitoExtension.class)
class InventoryBalanceFeatureStatusRepositoryImplTest {

    @Mock
    private InventoryBalanceFeatureStatusMapper mapper;

    @InjectMocks
    private InventoryBalanceFeatureStatusRepositoryImpl repository;

    @Test
    void saveWithoutExistingRow_insertsWithTimestamps() {
        // Given: 该门店 SKU 尚无切换状态行
        when(mapper.findByStoreAndProduct(1L, 2L)).thenReturn(null);
        InventoryBalanceFeatureStatus status = row(1L, 2L);
        status.setBalanceMode(InventoryBalanceFeatureStatus.LEGACY);

        // When
        InventoryBalanceFeatureStatus saved = repository.save(status);

        // Then: 走 insert，createdAt/updatedAt 均被赋值
        verify(mapper, times(1)).insert(saved);
        verify(mapper, never()).update(any());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(InventoryBalanceFeatureStatus.LEGACY, saved.getBalanceMode());
    }

    @Test
    void saveWithExistingRow_updatesPreservingIdAndCreatedAt() {
        // Given: 已有状态行
        LocalDateTime createdAt = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        InventoryBalanceFeatureStatus existing = row(1L, 2L);
        existing.setId(10L);
        existing.setBalanceMode(InventoryBalanceFeatureStatus.LEGACY);
        existing.setCreatedAt(createdAt);
        when(mapper.findByStoreAndProduct(1L, 2L)).thenReturn(existing);

        InventoryBalanceFeatureStatus incoming = row(1L, 2L);
        incoming.setBalanceMode(InventoryBalanceFeatureStatus.BATCH_LOCATION);

        // When
        InventoryBalanceFeatureStatus saved = repository.save(incoming);

        // Then: 走 update，id 取既有行，createdAt 保留，updatedAt 刷新
        verify(mapper, times(1)).update(incoming);
        verify(mapper, never()).insert(any());
        assertEquals(10L, saved.getId());
        assertEquals(createdAt, saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(saved.getUpdatedAt().isAfter(createdAt));
    }

    @Test
    void enableWithoutExistingRow_insertsBatchLocationMode() {
        // Given: 尚无状态行
        when(mapper.findByStoreAndProduct(3L, 4L)).thenReturn(null);

        // When
        InventoryBalanceFeatureStatus saved = repository.enable(3L, 4L, 99L);

        // Then: 走 insert，写入 BATCH_LOCATION 与 cutover 留痕
        verify(mapper, times(1)).insert(saved);
        verify(mapper, never()).update(any());
        assertEquals(3L, saved.getStoreId());
        assertEquals(4L, saved.getProductId());
        assertEquals(InventoryBalanceFeatureStatus.BATCH_LOCATION, saved.getBalanceMode());
        assertEquals(99L, saved.getCutoverBy());
        assertNotNull(saved.getCutoverAt());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void enableWithExistingRow_updatesAndRefreshesCutoverStamp() {
        // Given: 已激活行（幂等：再次激活只刷新 cutover 时间戳）
        LocalDateTime oldCutoverAt = LocalDateTime.of(2020, 5, 6, 7, 8, 9);
        InventoryBalanceFeatureStatus existing = row(3L, 4L);
        existing.setId(20L);
        existing.setBalanceMode(InventoryBalanceFeatureStatus.BATCH_LOCATION);
        existing.setCutoverAt(oldCutoverAt);
        existing.setCutoverBy(1L);
        existing.setCreatedAt(oldCutoverAt);
        when(mapper.findByStoreAndProduct(3L, 4L)).thenReturn(existing);

        // When
        InventoryBalanceFeatureStatus saved = repository.enable(3L, 4L, 99L);

        // Then: 走 update，id 与 createdAt 保留，cutoverAt/cutoverBy 刷新为本次操作
        verify(mapper, times(1)).update(existing);
        verify(mapper, never()).insert(any());
        assertEquals(20L, saved.getId());
        assertEquals(99L, saved.getCutoverBy());
        assertTrue(saved.getCutoverAt().isAfter(oldCutoverAt));
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void enableWithNullArguments_throwsIllegalArgumentException() {
        // When / Then: storeId / productId / operatorId 任一为空都拒绝，且不触达 mapper
        assertThrows(IllegalArgumentException.class, () -> repository.enable(null, 4L, 99L));
        assertThrows(IllegalArgumentException.class, () -> repository.enable(3L, null, 99L));
        assertThrows(IllegalArgumentException.class, () -> repository.enable(3L, 4L, null));
        verifyNoInteractions(mapper);
    }

    @Test
    void saveRollbackToLegacy_preservesCutoverEvidence() {
        // Given: 已激活行；回滚 = 读取该行后改回 LEGACY 再保存（同一写路径）
        LocalDateTime cutoverAt = LocalDateTime.of(2020, 5, 6, 7, 8, 9);
        InventoryBalanceFeatureStatus enabled = row(5L, 6L);
        enabled.setId(30L);
        enabled.setBalanceMode(InventoryBalanceFeatureStatus.BATCH_LOCATION);
        enabled.setCutoverAt(cutoverAt);
        enabled.setCutoverBy(77L);
        enabled.setCreatedAt(cutoverAt);
        when(mapper.findByStoreAndProduct(5L, 6L)).thenReturn(enabled);

        enabled.setBalanceMode(InventoryBalanceFeatureStatus.LEGACY);

        // When
        InventoryBalanceFeatureStatus saved = repository.save(enabled);

        // Then: 回退为 LEGACY，cutover 留痕不被清空，updatedAt 记录回滚时间
        verify(mapper, times(1)).update(enabled);
        verify(mapper, never()).insert(any());
        assertEquals(InventoryBalanceFeatureStatus.LEGACY, saved.getBalanceMode());
        assertEquals(cutoverAt, saved.getCutoverAt());
        assertEquals(77L, saved.getCutoverBy());
        assertTrue(saved.getUpdatedAt().isAfter(cutoverAt));
    }

    private static InventoryBalanceFeatureStatus row(Long storeId, Long productId) {
        InventoryBalanceFeatureStatus status = new InventoryBalanceFeatureStatus();
        status.setStoreId(storeId);
        status.setProductId(productId);
        return status;
    }
}
