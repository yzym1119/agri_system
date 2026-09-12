package com.agri_system.domain.inventory.service;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBalanceFeatureStatusRepository;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryMovementRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchBalanceServiceTest {

    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryBatchBalanceRepository balanceRepository;
    @Mock
    private InventoryMovementRepository movementRepository;
    @Mock
    private InventoryReceiptBatchRepository receiptBatchRepository;
    @Mock
    private InventoryBalanceFeatureStatusRepository featureStatusRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    @Test
    void replenishmentMovesOneKnownBatchFromBackroomToSalesFloorWithoutChangingStoreTotal() {
        InventoryLocation backroom = location(10L, 1L, "STORE_BACKROOM");
        InventoryLocation floor = location(11L, 1L, "SALES_FLOOR");
        InventoryBatchBalance source = balance(100L, 1L, 10L, "72");
        InventoryBatchBalance destination = balance(100L, 1L, 11L, "0");
        when(locationRepository.findById(10L)).thenReturn(Optional.of(backroom));
        when(locationRepository.findById(11L)).thenReturn(Optional.of(floor));
        when(balanceRepository.findByBatchAndLocationForUpdate(100L, 10L)).thenReturn(Optional.of(source));
        when(balanceRepository.findByBatchAndLocationForUpdate(100L, 11L)).thenReturn(Optional.of(destination));
        when(featureStatusRepository.findByStoreAndProduct(1L, 5L))
                .thenReturn(Optional.of(featureEnabled()));

        service().move(move(100L, 10L, 11L, "2", "48"));

        assertEquals(0, source.getBaseQuantity().compareTo(new BigDecimal("24")));
        assertEquals(0, destination.getBaseQuantity().compareTo(new BigDecimal("48")));
        verify(balanceRepository).save(source);
        verify(balanceRepository).save(destination);
        verify(movementRepository).append(any());
        verify(inventoryRepository).findByStoreAndProductForUpdate(1L, 5L);
    }

    @Test
    void rejectsMovingMoreThanTheSourceBatchLocationBalance() {
        InventoryLocation backroom = location(10L, 1L, "STORE_BACKROOM");
        InventoryLocation floor = location(11L, 1L, "SALES_FLOOR");
        when(locationRepository.findById(10L)).thenReturn(Optional.of(backroom));
        when(locationRepository.findById(11L)).thenReturn(Optional.of(floor));
        when(balanceRepository.findByBatchAndLocationForUpdate(100L, 10L))
                .thenReturn(Optional.of(balance(100L, 1L, 10L, "72")));
        when(featureStatusRepository.findByStoreAndProduct(1L, 5L))
                .thenReturn(Optional.of(featureEnabled()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service().move(move(100L, 10L, 11L, "73", "73")));

        assertEquals("货位库存不足", exception.getMessage());
        verify(balanceRepository, never()).save(any());
        verify(movementRepository, never()).append(any());
    }

    @Test
    void receiptDoesNotCreateBatchLocationBalanceUntilSkuHasExplicitlyCutOver() {
        InventoryReceiptBatch receiptBatch = new InventoryReceiptBatch();
        receiptBatch.setId(100L);
        receiptBatch.setStoreId(1L);
        receiptBatch.setProductId(5L);
        receiptBatch.setBaseQuantity(new BigDecimal("48"));
        when(featureStatusRepository.findByStoreAndProduct(1L, 5L)).thenReturn(Optional.empty());

        service().increaseFromReceipt(20L, receiptBatch);

        verify(locationRepository, never()).findEnabledByStoreAndType(any(), any());
        verify(balanceRepository, never()).save(any());
    }

    @Test
    void replenishmentAndPickRequireBothLocationsAndTransferCannotUseMove() {
        IllegalArgumentException replenishment = assertThrows(IllegalArgumentException.class,
                () -> service().move(moveWithPurpose(BatchBalanceService.MovementPurpose.REPLENISHMENT, 10L, null)));
        assertEquals("补货和拣货必须同时指定源货位和目的货位", replenishment.getMessage());
        IllegalArgumentException pick = assertThrows(IllegalArgumentException.class,
                () -> service().move(moveWithPurpose(BatchBalanceService.MovementPurpose.PICK, null, 11L)));
        assertEquals("补货和拣货必须同时指定源货位和目的货位", pick.getMessage());
        IllegalArgumentException transfer = assertThrows(IllegalArgumentException.class,
                () -> service().move(moveWithPurpose(BatchBalanceService.MovementPurpose.TRANSFER, 10L, 11L)));
        assertEquals("跨店调拨必须拆成同一transferId的出库和入库两条事实,不能在单次move同时指定源和目的",
                transfer.getMessage());
        verify(balanceRepository, never()).save(any());
        verify(movementRepository, never()).append(any());
    }

    @Test
    void adjustmentRequiresDirectionAndNonReceiptDocument() {
        IllegalArgumentException noDirection = assertThrows(IllegalArgumentException.class,
                () -> service().move(new BatchBalanceService.MoveRequest(100L, null, 11L,
                        BatchBalanceService.MovementPurpose.COUNT_ADJUSTMENT, null, 101L, "箱",
                        new BigDecimal("1"), new BigDecimal("24"), new BigDecimal("10"),
                        "COUNT", 1L, 9L, "盘盈", null)));
        assertEquals("盘点调整或退货必须指定方向IN/OUT", noDirection.getMessage());
        IllegalArgumentException receiptRef = assertThrows(IllegalArgumentException.class,
                () -> service().move(new BatchBalanceService.MoveRequest(100L, null, 11L,
                        BatchBalanceService.MovementPurpose.COUNT_ADJUSTMENT, null, 101L, "箱",
                        new BigDecimal("1"), new BigDecimal("24"), new BigDecimal("10"),
                        "RECEIPT", 1L, 9L, "盘盈", BatchBalanceService.Direction.IN)));
        assertEquals("盘点调整或退货必须关联非收货业务单据", receiptRef.getMessage());
        verify(balanceRepository, never()).save(any());
    }

    private BatchBalanceService service() {
        return new BatchBalanceService(locationRepository, balanceRepository, movementRepository,
                receiptBatchRepository, featureStatusRepository, inventoryRepository);
    }

    private BatchBalanceService.MoveRequest move(Long batchId, Long fromLocationId, Long toLocationId,
                                                 String originalQuantity, String baseQuantity) {
        return new BatchBalanceService.MoveRequest(batchId, fromLocationId, toLocationId,
                BatchBalanceService.MovementPurpose.REPLENISHMENT, null,
                101L, "箱", new BigDecimal(originalQuantity), new BigDecimal(baseQuantity),
                new BigDecimal("10.00"), null, null, 9L, "补货", null);
    }

    private BatchBalanceService.MoveRequest moveWithPurpose(BatchBalanceService.MovementPurpose purpose,
                                                             Long fromLocationId, Long toLocationId) {
        return new BatchBalanceService.MoveRequest(100L, fromLocationId, toLocationId, purpose, "T-1",
                101L, "箱", new BigDecimal("1"), new BigDecimal("24"), new BigDecimal("10"),
                null, null, 9L, "测试", null);
    }

    private InventoryLocation location(Long id, Long storeId, String type) {
        InventoryLocation location = new InventoryLocation();
        location.setId(id);
        location.setStoreId(storeId);
        location.setLocationType(type);
        location.setStatus(1);
        return location;
    }

    private InventoryBatchBalance balance(Long batchId, Long storeId, Long locationId, String quantity) {
        InventoryBatchBalance balance = new InventoryBatchBalance();
        balance.setBatchId(batchId);
        balance.setProductId(5L);
        balance.setStoreId(storeId);
        balance.setLocationId(locationId);
        balance.setBaseQuantity(new BigDecimal(quantity));
        balance.setReservedBaseQuantity(BigDecimal.ZERO);
        return balance;
    }

    private com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus featureEnabled() {
        com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus status =
                new com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus();
        status.setBalanceMode("BATCH_LOCATION");
        return status;
    }
}
