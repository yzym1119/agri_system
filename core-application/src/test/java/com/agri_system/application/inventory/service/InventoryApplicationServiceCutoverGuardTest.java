package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.command.InventoryTransferCommand;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Critical 2 安全闸门:已切换到批次货位余额模式的 SKU 禁止再走 legacy 入库/出库/调拨/盘点,
 * 否则会与新链路的批次余额权威来源背离并双写库存快照。守卫在方法入口即触发,
 * 因此只需 mock {@link BatchBalanceService},其余依赖不会被触达。
 */
@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceCutoverGuardTest {

    @Mock
    private BatchBalanceService batchBalanceService;
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
}
