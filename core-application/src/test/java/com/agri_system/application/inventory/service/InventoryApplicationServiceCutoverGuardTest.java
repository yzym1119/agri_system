package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.command.InventoryTransferCommand;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.inventory.service.InventoryDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

/**
 * Critical 2 安全闸门:已切换到批次货位余额模式的 SKU 禁止再走 legacy 入库/出库/调拨/盘点,
 * 否则会与新链路的批次余额权威来源背离并双写库存快照。变更入口的守卫在方法入口即触发(抛异常),
 * 只需 mock {@link BatchBalanceService};读详情 getInventoryDetail 的守卫是“惰性物化时条件跳过 legacy 写入”,
 * 会触达快照/批次/门店/商品仓储,故一并 mock 这些依赖以验证不回退 refreshTotalStock。
 */
@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceCutoverGuardTest {

    @Mock
    private BatchBalanceService batchBalanceService;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryDomainService domainService;
    @Mock
    private InventoryBatchRepository batchRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private InventoryApplicationService service;

    @Test
    void rejectsLegacyInboundWhenSkuHasCutOverToBatchLocation() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(true);
        InventoryInCommand command = new InventoryInCommand();
        command.setStoreId(1L);
        command.setProductId(5L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.inbound(command, 9L));
        assertEquals("该SKU已切换到批次货位余额模式,禁止走传统入库路径,请使用新单位链路", exception.getMessage());
    }

    @Test
    void rejectsLegacyOutboundWhenSkuHasCutOverToBatchLocation() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(true);
        InventoryOutCommand command = new InventoryOutCommand();
        command.setStoreId(1L);
        command.setProductId(5L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.outbound(command, 9L));
        assertEquals("该SKU已切换到批次货位余额模式,禁止走传统出库路径,请使用新单位链路", exception.getMessage());
    }

    @Test
    void rejectsLegacyTransferWhenSourceStoreSkuHasCutOver() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(true);
        InventoryTransferCommand command = new InventoryTransferCommand();
        command.setFromStoreId(1L);
        command.setToStoreId(2L);
        command.setProductId(5L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(command, 9L));
        assertEquals("该SKU已切换到批次货位余额模式,禁止走传统调拨路径,请使用新单位链路", exception.getMessage());
    }

    @Test
    void rejectsLegacyTransferWhenOnlyDestinationStoreSkuHasCutOver() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(false);
        when(batchBalanceService.isBatchLocationEnabled(2L, 5L)).thenReturn(true);
        InventoryTransferCommand command = new InventoryTransferCommand();
        command.setFromStoreId(1L);
        command.setToStoreId(2L);
        command.setProductId(5L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(command, 9L));
        assertEquals("该SKU已切换到批次货位余额模式,禁止走传统调拨路径,请使用新单位链路", exception.getMessage());
    }

    @Test
    void rejectsLegacyAdjustWhenSkuHasCutOverToBatchLocation() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(true);
        InventoryAdjustCommand command = new InventoryAdjustCommand();
        command.setStoreId(1L);
        command.setProductId(5L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.adjust(command, 9L));
        assertEquals("该SKU已切换到批次货位余额模式,禁止走传统盘点路径,请使用新单位链路", exception.getMessage());
    }

    @Test
    void skipsLegacySnapshotMaterializationInDetailWhenSkuHasCutOver() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(true);
        when(inventoryRepository.findByStoreAndProduct(1L, 5L)).thenReturn(Optional.empty());
        // 已切换 SKU 的快照缺失时,读详情不得回退 legacy refreshTotalStock(会用 inventory_batch 写出背离快照)
        service.getInventoryDetail(1L, 5L);
        verify(domainService, never()).refreshTotalStock(1L, 5L);
    }

    @Test
    void materializesLegacySnapshotInDetailWhenSkuNotCutOver() {
        when(batchBalanceService.isBatchLocationEnabled(1L, 5L)).thenReturn(false);
        when(inventoryRepository.findByStoreAndProduct(1L, 5L)).thenReturn(Optional.empty());
        // 未切换 SKU 保持原惰性物化行为(回归保护)
        service.getInventoryDetail(1L, 5L);
        verify(domainService, times(1)).refreshTotalStock(1L, 5L);
    }
}
