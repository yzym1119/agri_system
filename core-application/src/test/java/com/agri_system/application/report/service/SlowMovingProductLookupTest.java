package com.agri_system.application.report.service;

import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlowMovingProductLookupTest {
    @Mock InventoryBatchMapper batches;
    @Mock ProductRepository products;
    @InjectMocks ReportAnalysisService service;

    private InventoryBatch batch(long productId, String no, String qty, String cost, long days) {
        var batch=new InventoryBatch();batch.setProductId(productId);batch.setBatchNo(no);
        batch.setQuantity(new BigDecimal(qty));batch.setCostPrice(new BigDecimal(cost));
        batch.setProductionDate(LocalDate.now().minusDays(days));return batch;
    }
    private Product product(long id, String name) {
        var product=new Product();product.setId(id);product.setName(name);return product;
    }

    @Test void multipleBatchesUseOneDistinctProductReadAndPreserveRowsAndAmounts() {
        when(batches.currentBalances(3L)).thenReturn(List.of(batch(1,"B1","2","3",190),batch(1,"B2","4","5",200),batch(2,"B3","1","9",210),batch(9,"NEW","99","99",10)));
        when(products.findByIds(List.of(1L,2L))).thenReturn(List.of(product(2,"种子"),product(1,"肥料")));
        var rows=service.slowMovingInventory(3L);
        assertEquals(List.of("B3","B2","B1"),rows.stream().map(r->r.getBatchNo()).toList());
        assertEquals(List.of("种子","肥料","肥料"),rows.stream().map(r->r.getProductName()).toList());
        assertEquals(0,new BigDecimal("20").compareTo(rows.get(1).getValue()));
        assertEquals(0,new BigDecimal("6").compareTo(rows.get(2).getValue()));
        verify(products).findByIds(List.of(1L,2L));
    }

    @Test void noAgedStockDoesNotQueryProductCatalog() {
        when(batches.currentBalances(3L)).thenReturn(List.of(batch(1,"NEW","1","2",10)));
        assertTrue(service.slowMovingInventory(3L).isEmpty());verifyNoInteractions(products);
    }

    @Test void missingOrUnnamedProductsKeepFallbackAndNextReadRefreshesNames() {
        when(batches.currentBalances(null)).thenReturn(List.of(batch(1,"A","1","2",190),batch(2,"B","1","2",191)));
        when(products.findByIds(List.of(1L,2L))).thenReturn(List.of(product(1,null))).thenReturn(List.of(product(1,"新名称"),product(2,"补全名称")));
        var first=service.slowMovingInventory(null);
        assertEquals(List.of("商品2","商品1"),first.stream().map(r->r.getProductName()).toList());
        var fresh=service.slowMovingInventory(null);
        assertEquals(List.of("补全名称","新名称"),fresh.stream().map(r->r.getProductName()).toList());
        verify(products,times(2)).findByIds(List.of(1L,2L));
    }
}
