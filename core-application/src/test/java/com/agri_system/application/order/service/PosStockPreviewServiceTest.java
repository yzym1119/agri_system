package com.agri_system.application.order.service;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.productpackage.service.PackagePricingService;
import com.agri_system.application.productpackage.dto.vo.PackagePriceQuote;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.vo.SaleableStock;
import com.agri_system.domain.product.model.*;
import com.agri_system.domain.product.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosStockPreviewServiceTest {
    ProductRepository products=mock(ProductRepository.class);
    ProductUnitRepository unitRepository=mock(ProductUnitRepository.class);
    PackagePricingService packages=mock(PackagePricingService.class);
    InventoryBatchBalanceRepository balances=mock(InventoryBatchBalanceRepository.class);
    PosStockPreviewService service=new PosStockPreviewService(new ProductUnitService(unitRepository,products),packages,balances,products);
    com.agri_system.application.support.EmployeeIdentityTestScope actor;
    @BeforeEach void setup() {
        actor=new com.agri_system.application.support.EmployeeIdentityTestScope(7L,1L,false);
        var product=new Product();product.setId(10L);product.setName("肥料");product.setMainUnit("箱");product.setStatus(1);
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(unitRepository.findEnabledByProductId(10L)).thenReturn(List.of(unit(11L,"袋","1",true),unit(12L,"箱","10",false)));
        when(balances.findSaleableStock(1L,List.of(10L))).thenReturn(List.of(new SaleableStock(10L,11L,new BigDecimal("12"))));
        var material=new PackagePriceQuote.Item(10L,"肥料",BigDecimal.ONE,"箱",12L,BigDecimal.ONE,BigDecimal.TEN,0,BigDecimal.ONE);
        when(packages.quote(20L,null,1L,BigDecimal.ONE,null)).thenReturn(new PackagePriceQuote("Q",20L,"套餐",BigDecimal.ONE,BigDecimal.TEN,null,false,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,List.of(material)));
    }
    @AfterEach void close(){if(actor!=null)actor.close();}
    ProductUnit unit(long id,String name,String ratio,boolean base){var u=new ProductUnit();u.setId(id);u.setProductId(10L);u.setUnitName(name);u.setBaseQuantityPerUnit(new BigDecimal(ratio));u.setStatus(1);u.setIsBaseUnit(base?1:0);u.setAllowSale(1);return u;}
    OrderItemDTO single(String amount,String name){var i=new OrderItemDTO();i.setItemType(1);i.setProductId(10L);i.setQuantity(new BigDecimal(amount));i.setUnit(name);return i;}
    OrderItemDTO pack(){var i=new OrderItemDTO();i.setItemType(2);i.setPackageId(20L);i.setQuantity(BigDecimal.ONE);i.setPackageQuoteId("Q");return i;}
    PosStockCheckCommand command(OrderItemDTO... rows){var c=new PosStockCheckCommand();c.setStoreId(1L);c.setItems(List.of(rows));return c;}
    @Test void sumsSingleAndPackageInRealBaseUnitsInsteadOfCheckingEachRow() {
        assertTrue(service.preview(command(single("5","袋"))).sufficient());
        assertTrue(service.preview(command(pack())).sufficient());
        var result=service.preview(command(single("5","袋"),pack()));assertFalse(result.sufficient());assertEquals("1",result.storeId());
        assertTrue(result.issues().get(0).contains("15"));assertTrue(result.issues().get(0).contains("12"));
    }
    @Test void duplicatePackagesAndDifferentSaleUnitsShareOneDemand() {
        assertFalse(service.preview(command(pack(),pack())).sufficient());
        assertTrue(service.preview(command(single("1","箱"),single("2","袋"))).sufficient());
    }
    @Test void missingOriginalUnitIsUnknownNotAZeroOrAnApproval() {
        when(balances.findSaleableStock(1L,List.of(10L))).thenReturn(List.of(new SaleableStock(10L,99L,BigDecimal.TEN)));
        var result=service.preview(command(single("1","袋")));assertFalse(result.sufficient());assertTrue(result.issues().get(0).contains("待核对"));
    }
    @Test void rejectsForeignStoreAndChangedPackageQuoteBeforeReadingBalances() {
        var c=command(pack());c.setStoreId(2L);assertThrows(IllegalArgumentException.class,()->service.preview(c));
        var stale=pack();stale.setPackageQuoteId("OLD");assertThrows(IllegalArgumentException.class,()->service.preview(command(stale)));
        verifyNoInteractions(balances);
    }
    @Test void rejectsIllegalFractionalBaseAndDoesNotReserveAnything() {
        assertThrows(IllegalArgumentException.class,()->service.preview(command(single("0.01","箱"))));
        assertTrue(service.preview(command(single("0.5","箱"))).sufficient());
        verify(balances).findSaleableStock(1L,List.of(10L));verifyNoMoreInteractions(balances);
    }
}
