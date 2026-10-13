package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.*;
import com.agri_system.application.inventory.dto.vo.BatchDetailVO;
import com.agri_system.domain.inventory.model.Inventory;
import com.agri_system.domain.inventory.model.InventoryTransferOrder;
import com.agri_system.domain.inventory.repository.*;
import com.agri_system.domain.inventory.service.InventoryDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 库存统一后所有业务入口都委派同一批次账，不再根据 SKU 模式选择旧库存。 */
@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceCutoverGuardTest {
    @Mock private InventoryLedgerService ledger;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryTransferOrderRepository transferOrderRepository;
    @Mock private InventoryTransferOrderDetailRepository transferOrderDetailRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private InventoryApplicationService service;

    @Test void inboundReturnsReceiptBatchFromUnifiedLedger() {
        InventoryInCommand command = new InventoryInCommand();
        command.setStoreId(1L); command.setProductId(5L);
        when(ledger.inbound(command, 9L)).thenReturn(77L);
        assertEquals(77L, service.inbound(command, 9L));
        verify(ledger).inbound(command, 9L);
        verifyNoInteractions(batchRepository, inventoryRepository);
    }

    @Test void outboundPreservesLineAndOperatorAndReturnsLedgerAllocation() {
        InventoryOutCommand command = new InventoryOutCommand();
        command.setReferenceLineId(55L);
        List<BatchDetailVO> result = List.of(new BatchDetailVO());
        when(ledger.outbound(command, 9L)).thenReturn(result);
        assertSame(result, service.outbound(command, 9L));
        verify(ledger).outbound(command, 9L);
        verifyNoInteractions(batchRepository, inventoryRepository);
    }

    @Test void adjustDelegatesToUnifiedLedger() {
        InventoryAdjustCommand command = new InventoryAdjustCommand();
        service.adjust(command, 9L);
        verify(ledger).adjust(command, 9L);
        verifyNoInteractions(batchRepository, inventoryRepository);
    }

    @Test void transferValidatesStoresAndPersistsReceiptBatchDetails() {
        InventoryTransferCommand command = new InventoryTransferCommand();
        command.setFromStoreId(1L); command.setToStoreId(2L); command.setProductId(5L);
        command.setQuantity(new BigDecimal("2")); command.setRequestKey("transfer-80");
        when(storeRepository.findById(1L)).thenReturn(Optional.of(new Store()));
        when(storeRepository.findById(2L)).thenReturn(Optional.of(new Store()));
        Product product = new Product(); product.setMainUnit("袋");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        doAnswer(invocation -> { ((InventoryTransferOrder) invocation.getArgument(0)).setId(80L); return null; })
                .when(transferOrderRepository).save(any());
        InventoryDomainService.TransferBatchDetail slice = new InventoryDomainService.TransferBatchDetail();
        slice.setFromBatchId(71L); slice.setToBatchId(72L); slice.setQuantity(new BigDecimal("2"));
        slice.setCostPrice(new BigDecimal("8"));
        InventoryDomainService.TransferDetail detail = new InventoryDomainService.TransferDetail();
        detail.setTransferBatchDetails(List.of(slice));
        when(ledger.transfer(1L, 2L, 5L, command.getQuantity(), "袋", 9L, 80L)).thenReturn(detail);
        assertSame(detail, service.transfer(command, 9L));
        verify(transferOrderDetailRepository).saveBatch(argThat(records -> records.size() == 1
                && records.get(0).getFromReceiptBatchId().equals(71L)
                && records.get(0).getToReceiptBatchId().equals(72L)
                && records.get(0).getTransferId().equals(80L)));
        assertEquals(80L, detail.getTransferId());
        verifyNoInteractions(batchRepository, inventoryRepository);
    }

    @Test void transferRejectsSameStoreBeforeWritingAnything() {
        InventoryTransferCommand command = new InventoryTransferCommand();
        command.setFromStoreId(1L); command.setToStoreId(1L);
        assertThrows(IllegalArgumentException.class, () -> service.transfer(command, 9L));
        verifyNoInteractions(ledger, transferOrderRepository, transferOrderDetailRepository);
    }

    @Test void missingSnapshotReturnsZeroAndReceiptBatchViewsWithoutLegacyWrite() {
        when(inventoryRepository.findByStoreAndProduct(1L, 5L)).thenReturn(Optional.empty());
        List<BatchDetailVO> batches = List.of(new BatchDetailVO());
        when(ledger.batchViews(1L, 5L)).thenReturn(batches);
        var detail = service.getInventoryDetail(1L, 5L);
        assertEquals(BigDecimal.ZERO, detail.getQuantity());
        assertSame(batches, detail.getBatches());
        verifyNoInteractions(batchRepository);
        verify(inventoryRepository).findByStoreAndProduct(1L, 5L);
        verifyNoMoreInteractions(inventoryRepository);
    }

    @Test void existingSnapshotAndSingleBatchReadUseUnifiedLedger() {
        Inventory inventory = new Inventory(); inventory.setQuantity(new BigDecimal("3"));
        when(inventoryRepository.findByStoreAndProduct(1L, 5L)).thenReturn(Optional.of(inventory));
        when(ledger.batchViews(1L, 5L)).thenReturn(List.of());
        assertEquals(inventory.getQuantity(), service.getInventoryDetail(1L, 5L).getQuantity());
        BatchDetailVO batch = new BatchDetailVO();
        when(ledger.batchView(77L)).thenReturn(batch);
        assertSame(batch, service.getBatchDetail(77L));
        verifyNoInteractions(batchRepository);
    }
}
