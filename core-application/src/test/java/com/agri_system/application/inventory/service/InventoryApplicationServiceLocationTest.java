package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryMoveCommand;
import com.agri_system.application.inventory.dto.vo.InventoryLocationVO;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.product.model.ProductUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceLocationTest {
    @Mock
    private BatchBalanceService batchBalanceService;
    @Mock
    private InventoryReceiptBatchRepository receiptBatchRepository;
    @Mock
    private ProductUnitService productUnitService;
    @Mock
    private InventoryLocationRepository locationRepository;
    @InjectMocks
    private InventoryApplicationService service;

    @Test
    void moveConvertsOriginalUnitThroughProductUnitServiceAndPersistsBothSnapshots() {
        InventoryReceiptBatch batch = new InventoryReceiptBatch();
        batch.setId(100L);
        batch.setProductId(5L);
        batch.setBaseUnitCost(new BigDecimal("2.50"));
        when(receiptBatchRepository.findById(100L)).thenReturn(Optional.of(batch));
        ProductUnit unit = new ProductUnit();
        unit.setId(101L);
        when(productUnitService.requireEnabledUnit(5L, "箱", ProductUnitService.UnitOperation.INVENTORY))
                .thenReturn(unit);
        when(productUnitService.toBaseQuantity(5L, "箱", new BigDecimal("2")))
                .thenReturn(new UnitConversionSnapshot("箱", new BigDecimal("2"), new BigDecimal("24"), new BigDecimal("48")));
        when(batchBalanceService.move(any())).thenReturn(new InventoryMovement());

        service.move(moveCommand(), 9L);

        ArgumentCaptor<BatchBalanceService.MoveRequest> request = ArgumentCaptor.forClass(BatchBalanceService.MoveRequest.class);
        verify(batchBalanceService).move(request.capture());
        assertEquals(101L, request.getValue().originalUnitId());
        assertEquals("箱", request.getValue().originalUnitName());
        assertEquals(0, request.getValue().originalQuantity().compareTo(new BigDecimal("2")));
        assertEquals(0, request.getValue().baseQuantity().compareTo(new BigDecimal("48")));
        assertEquals(0, request.getValue().unitCost().compareTo(new BigDecimal("2.50")));
        assertEquals("IDEM-REPLENISH-1", request.getValue().idempotencyKey());
    }

    @Test
    void listLocationsRejectsMissingStoreId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.listLocations(null));

        assertEquals("storeId 不能为空", ex.getMessage());
    }

    @Test
    void listLocationsMapsEveryFieldToVO() {
        InventoryLocation location = new InventoryLocation();
        location.setId(31L);
        location.setStoreId(3L);
        location.setLocationType("STORE_BACKROOM");
        location.setCode("BR-01");
        location.setName("后仓01");
        location.setStatus(1);
        location.setCreatedAt(LocalDateTime.of(2026, 9, 27, 10, 0));
        when(locationRepository.findByStoreId(3L)).thenReturn(List.of(location));

        List<InventoryLocationVO> result = service.listLocations(3L);

        assertEquals(1, result.size());
        InventoryLocationVO vo = result.get(0);
        assertEquals(31L, vo.getId());
        assertEquals(3L, vo.getStoreId());
        assertEquals("STORE_BACKROOM", vo.getLocationType());
        assertEquals("BR-01", vo.getCode());
        assertEquals("后仓01", vo.getName());
        assertEquals(1, vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 27, 10, 0), vo.getCreatedAt());
    }

    private InventoryMoveCommand moveCommand() {
        InventoryMoveCommand command = new InventoryMoveCommand();
        command.setBatchId(100L);
        command.setFromLocationId(10L);
        command.setToLocationId(11L);
        command.setPurpose("REPLENISHMENT");
        command.setOriginalUnit("箱");
        command.setOriginalQuantity(new BigDecimal("2"));
        command.setRemark("补货");
        command.setIdempotencyKey("IDEM-REPLENISH-1");
        return command;
    }
}