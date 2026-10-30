package com.agri_system.application.purchase.service;

import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.inventory.model.*;
import com.agri_system.domain.inventory.repository.*;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.purchase.repository.*;
import com.agri_system.domain.purchase.service.PurchaseDomainService;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiptSubmissionQueryTest {
    InventoryReceiptRepository receipts; InventoryReceiptLineRepository lines; InventoryReceiptBatchRepository batches;
    ProductUnitService units;PurchaseApplicationService service;
    com.agri_system.domain.store.repository.StaffRepository staff;com.agri_system.domain.store.model.Staff actor;
    @BeforeEach void setup() {
        receipts=mock(InventoryReceiptRepository.class);lines=mock(InventoryReceiptLineRepository.class);batches=mock(InventoryReceiptBatchRepository.class);
        units=mock(ProductUnitService.class);
        staff=mock(com.agri_system.domain.store.repository.StaffRepository.class);actor=new com.agri_system.domain.store.model.Staff();actor.setId(7L);actor.setStoreId(1L);actor.setStatus(1);
        when(staff.findIdentityById(7L)).thenReturn(Optional.of(actor));
        service=spy(new PurchaseApplicationService(mock(PurchaseRepository.class),mock(PurchaseItemRepository.class),mock(PurchaseDomainService.class),
                mock(ProductRepository.class),mock(StoreRepository.class),mock(SupplierRepository.class),units,receipts,lines,batches,mock(BatchBalanceService.class),staff));
    }
    InventoryReceipt receipt(){var r=new InventoryReceipt();r.setId(90L);r.setStoreId(1L);r.setReceiverId(7L);r.setPurchaseOrderId(8L);r.setStatus("RECEIVED");r.setRequestKey("original-key");r.setReceiptNo("RCV90");return r;}
    @Test void missingReceiptMeansUnknownNotNotReceived() {
        when(receipts.findByRequestKeyAndOwner("original-key",1L,7L)).thenReturn(Optional.empty());
        var result=service.getReceiptSubmission("original-key",7L);
        assertEquals("UNKNOWN",result.status());assertNull(result.receiptId());assertEquals("original-key",result.requestKey());
        assertEquals("1",result.storeId());assertTrue(result.lines().isEmpty());verifyNoInteractions(lines,batches);
    }
    @Test void completedResultUsesOriginalReceiptFactsIncludingTwoSameSkuBatchesAndUnknownDates() {
        when(receipts.findByRequestKeyAndOwner("original-key",1L,7L)).thenReturn(Optional.of(receipt()));
        var line=new InventoryReceiptLine();line.setId(91L);line.setReceiptId(90L);line.setStoreId(1L);line.setPurchaseOrderItemId(81L);line.setProductId(10L);
        when(lines.findByReceiptId(90L)).thenReturn(List.of(line));
        var first=batch(92,"0.5","120.00000000");var second=batch(93,"1","125");second.setExpiryDate(LocalDate.of(2027,1,1));second.setDateStatus("PARTIAL");
        when(batches.findByReceiptId(90L)).thenReturn(List.of(first,second));
        var result=service.getReceiptSubmission("original-key",7L);
        assertEquals("COMPLETED",result.status());assertEquals("90",result.receiptId());assertEquals("8",result.purchaseOrderId());
        assertEquals("81",result.lines().get(0).purchaseOrderItemId());assertEquals(2,result.lines().get(0).batches().size());
        var original=result.lines().get(0).batches().get(0);assertEquals("0.5",original.originalQuantity());assertEquals("120",original.originalUnitCost());
        assertNull(original.productionDate());assertNull(original.expiryDate());assertEquals("箱",original.originalUnitName());
        assertEquals("2027-01-01",result.lines().get(0).batches().get(1).expiryDate());
    }
    InventoryReceiptBatch batch(long id,String quantity,String cost) {
        var b=new InventoryReceiptBatch();b.setId(id);b.setReceiptLineId(91L);b.setStoreId(1L);b.setProductId(10L);b.setInternalReceiptBatchNo("B-"+id);
        b.setOriginalUnitName("箱");b.setOriginalQuantity(new BigDecimal(quantity));b.setOriginalUnitCost(new BigDecimal(cost));b.setDateStatus("UNKNOWN");return b;
    }
    @Test void currentActorAndStoreAreRequiredAndForeignResultIsNeverExposed() {
        assertThrows(RuntimeException.class,()->service.getReceiptSubmission("original-key",99L));
        actor.setStoreId(null);assertThrows(RuntimeException.class,()->service.getReceiptSubmission("original-key",7L));
        actor.setStoreId(1L);
        var foreign=receipt();foreign.setReceiverId(99L);
        when(receipts.findByRequestKeyAndOwner("original-key",1L,7L)).thenReturn(Optional.of(foreign));
        assertThrows(RuntimeException.class,()->service.getReceiptSubmission("original-key",7L));verifyNoInteractions(lines,batches);
    }
    @Test void invalidKeyAndIncompleteFactsCannotBeReportedCompleted() {
        assertThrows(IllegalArgumentException.class,()->service.getReceiptSubmission(" ",7L));
        assertThrows(IllegalArgumentException.class,()->service.getReceiptSubmission("a".repeat(65),7L));
        when(receipts.findByRequestKeyAndOwner("original-key",1L,7L)).thenReturn(Optional.of(receipt()));
        when(lines.findByReceiptId(90L)).thenReturn(List.of());when(batches.findByReceiptId(90L)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.getReceiptSubmission("original-key",7L));
    }
    @Test void mobileDetailCarriesPersistedUnitsRemainingQuantityAndServerGranularity() {
        var detail=new com.agri_system.application.purchase.dto.vo.PurchaseDetailVO();detail.setId(8L);detail.setStoreId(1L);detail.setStatus(2);detail.setOrderNo("PO8");
        var item=new com.agri_system.application.purchase.dto.vo.PurchaseItemVO();item.setId(81L);item.setProductId(10L);item.setProductCode("P10");item.setProductName("测试商品");item.setQty(new BigDecimal("2"));item.setUnit("箱");detail.setItems(List.of(item));
        doReturn(detail).when(service).receivingDetail(8L);
        var bottle=unit(100,"瓶","1",1);var box=unit(101,"箱","12",0);box.setBarcode("BOX10");
        when(units.getUnits(10L)).thenReturn(List.of(bottle,box));
        when(batches.sumBaseQuantityByPurchaseOrderItemId(81L)).thenReturn(new BigDecimal("6"));
        var r=service.mobileReceivingDetail(8L,7L);var row=r.items().get(0);
        assertEquals("2",row.orderedQuantity());assertEquals("6",row.receivedBaseQuantity());assertEquals("18",row.remainingBaseQuantity());
        assertEquals("瓶",row.baseUnitName());assertFalse(row.allowFractionalBaseQuantity());assertEquals("12",row.units().get(1).baseQuantityPerUnit());
        assertTrue(row.scanCodes().containsAll(List.of("P10","BOX10")));
        box.setBaseQuantityPerUnit(new BigDecimal("3"));when(batches.sumBaseQuantityByPurchaseOrderItemId(81L)).thenReturn(BigDecimal.ONE);
        var split=service.mobileReceivingDetail(8L,7L).items().get(0);assertEquals("1",split.receivedBaseQuantity());assertEquals("5",split.remainingBaseQuantity());
        detail.setStoreId(2L);assertThrows(RuntimeException.class,()->service.mobileReceivingDetail(8L,7L));
    }
    com.agri_system.domain.product.model.ProductUnit unit(long id,String name,String ratio,int base) {
        var u=new com.agri_system.domain.product.model.ProductUnit();u.setId(id);u.setProductId(10L);u.setUnitName(name);u.setBaseQuantityPerUnit(new BigDecimal(ratio));u.setIsBaseUnit(base);u.setAllowPurchase(1);u.setStatus(1);return u;
    }
    @Test void mobileReceivingListIsBoundToCurrentStoreAndPendingStatus() {
        doAnswer(invocation->{
            com.agri_system.application.purchase.dto.query.PurchasePageQuery q=invocation.getArgument(0);
            assertEquals(1L,q.getStoreId());assertEquals(2,q.getStatus());assertEquals(2,q.getPageNum());assertEquals(20,q.getPageSize());
            var page=new com.mybatisflex.core.paginate.Page<com.agri_system.application.purchase.dto.vo.PurchasePageVO>();page.setTotalRow(41);
            var order=new com.agri_system.application.purchase.dto.vo.PurchasePageVO();order.setId(8L);order.setStoreId(1L);order.setOrderNo("PO8");page.setRecords(List.of(order));return page;
        }).when(service).page(any());
        var result=service.mobileReceivingPage(2,"PO",7L);assertEquals("1",result.storeId());assertTrue(result.hasMore());assertEquals("8",result.orders().get(0).id());
        assertThrows(IllegalArgumentException.class,()->service.mobileReceivingPage(0,"",7L));
    }
}
