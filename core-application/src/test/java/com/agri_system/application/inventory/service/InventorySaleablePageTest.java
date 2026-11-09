package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.query.InventoryQuery;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.vo.InventoryPageVO;
import com.agri_system.domain.inventory.vo.SaleableStock;
import com.agri_system.domain.product.model.ProductUnit;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventorySaleablePageTest {
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryBatchBalanceRepository batchBalances;
    @Mock ProductUnitService productUnitService;
    @InjectMocks InventoryApplicationService service;
    ProductUnit unit(long id,String name,String ratio,boolean base) {
        var u=new ProductUnit();u.setId(id);u.setUnitName(name);u.setStatus(1);u.setIsBaseUnit(base?1:0);u.setBaseQuantityPerUnit(new BigDecimal(ratio));return u;
    }
    InventoryPageVO row(long store) {
        var r=new InventoryPageVO();r.setProductId(10L);r.setStoreId(store);r.setMainUnit("箱");r.setTotalQuantity(BigDecimal.TEN);return r;
    }
    void page(List<InventoryPageVO> rows) {
        Page<InventoryPageVO> p=new Page<>(1,10);p.setRecords(rows);p.setTotalRow(rows.size());
        when(inventoryRepository.findPage(any(),any(),any(),any(),any(),any())).thenReturn(p);
        when(productUnitService.getUnits(10L)).thenReturn(List.of(unit(11,"瓶","1",true),unit(12,"箱","12",false)));
    }
    @Test void keepsOnHandAndReadsEachStoreOnlyForCurrentPageWithExactUnit() {
        var a=row(1);var b=row(2);page(List.of(a,b));
        when(batchBalances.findSaleableStock(1L,List.of(10L))).thenReturn(List.of(new SaleableStock(10L,11L,new BigDecimal("24"))));
        when(batchBalances.findSaleableStock(2L,List.of(10L))).thenReturn(List.of());
        var result=service.queryInventoryPage(new InventoryQuery());assertEquals(2,result.getRecords().size());
        assertEquals(BigDecimal.TEN,a.getTotalQuantity());assertEquals("2",a.getSaleableQuantity());assertEquals("箱",a.getSaleableUnit());
        assertEquals("24",a.getAvailableBaseQuantity());assertEquals("0",b.getSaleableQuantity());
        verify(batchBalances).findSaleableStock(1L,List.of(10L));verify(batchBalances).findSaleableStock(2L,List.of(10L));
    }
    @Test void inconsistentOriginalUnitIsUnknownAndNeverZero() {
        var a=row(1);page(List.of(a));
        when(batchBalances.findSaleableStock(1L,List.of(10L))).thenReturn(List.of(new SaleableStock(10L,99L,BigDecimal.ONE)));
        service.queryInventoryPage(new InventoryQuery());assertNull(a.getSaleableQuantity());assertNull(a.getAvailableBaseQuantity());
    }
    @Test void repeatingConversionShowsExactBaseQuantityRatherThanRoundedMainUnit() {
        var a=row(1);page(List.of(a));
        when(productUnitService.getUnits(10L)).thenReturn(List.of(unit(11,"瓶","1",true),unit(12,"箱","3",false)));
        when(batchBalances.findSaleableStock(1L,List.of(10L))).thenReturn(List.of(new SaleableStock(10L,11L,BigDecimal.ONE)));
        service.queryInventoryPage(new InventoryQuery());assertEquals("1",a.getSaleableQuantity());assertEquals("瓶",a.getSaleableUnit());
    }
}
