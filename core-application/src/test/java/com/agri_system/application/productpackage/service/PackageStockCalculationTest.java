package com.agri_system.application.productpackage.service;

import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.productpackage.dto.query.ProductPackageQuery;
import com.agri_system.application.productpackage.dto.vo.ProductPackageDetailVO;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.agri_system.domain.inventory.vo.SaleableStock;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 调用真实套餐列表服务，检验基准库存、持久化单位及逐套餐故障隔离。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackageStockCalculationTest {
    @Mock ProductPackageRepository packages;
    @Mock ProductPackageItemRepository components;
    @Mock ProductRepository products;
    @Mock InventoryRepository legacyInventory;
    @Mock InventoryBatchBalanceRepository balances;
    @Mock ProductUnitService units;
    @InjectMocks ProductPackageApplicationService service;

    @BeforeEach void productsAndUnits() {
        for (long id : List.of(1L, 2L)) {
            Product product = new Product(); product.setId(id); product.setStatus(1);
            product.setName("物料" + id); product.setMainUnit("箱"); product.setAuxUnit("瓶");
            product.setMainToAuxRatio(new BigDecimal("999")); // 旧字段不得影响计算。
            when(products.findById(id)).thenReturn(Optional.of(product));
            unit(id, "瓶", "1"); unit(id, "箱", "20"); unit(id, "件", "60");
        }
        when(balances.sumAvailableBaseQuantityGroupedByProduct(10L))
                .thenReturn(Map.of(1L, new BigDecimal("200"), 2L, new BigDecimal("50")));
        when(balances.findSaleableStock(eq(10L),anyList())).thenReturn(List.of(
                new SaleableStock(1L,101L,new BigDecimal("200")),new SaleableStock(2L,102L,new BigDecimal("50"))));
        // 历史主单位库存故意不同，新可售套数必须由可用基准库存决定。
        when(legacyInventory.findInventoryByStoreAndProducts(anyLong(), anyList()))
                .thenReturn(Map.of(1L, new BigDecimal("999"), 2L, new BigDecimal("999")));
    }

    @Test void mainUnitUsesPersistentBaseCoefficient() {
        listing(1L, List.of(component(1L, "箱", "2")));
        assertEquals(new BigDecimal("5"), query().get(0).getStock());
    }

    @Test void auxiliaryUnitUsesSameBaseQuantityWithoutLegacyRatio() {
        listing(1L, List.of(component(1L, "瓶", "5")));
        assertEquals(new BigDecimal("40"), query().get(0).getStock());
    }

    @Test void thirdUnitAndOtherProductsUseMinimumWholePackageCount() {
        listing(1L, List.of(component(1L, "件", "1"), component(2L, "瓶", "20")));
        assertEquals(new BigDecimal("2"), query().get(0).getStock());
    }

    @Test void legacyRepeatedSkuDemandIsAggregatedBeforeDividingStock() {
        listing(1L, List.of(component(1L, "箱", "1"), component(1L, "瓶", "5")));
        assertEquals(new BigDecimal("8"), query().get(0).getStock());
    }

    @Test void unknownUnitOnlyMakesItsPackageUnavailable() {
        listing(1L, List.of(component(1L, "未知单位", "1")));
        listing(2L, List.of(component(2L, "瓶", "5")));
        when(units.requireEnabledUnit(1L, "未知单位", ProductUnitService.UnitOperation.SALE))
                .thenThrow(new IllegalArgumentException("单位不存在"));
        var result=query();assertNull(result.get(0).getStock());assertEquals(new BigDecimal("10"),result.get(1).getStock());
    }

    @Test void fractionalUnitDoesNotRoundUpAvailableStockIntoAnExtraPackage() {
        unit(1L, "小瓶", "0.0004");
        ProductUnit measured=new ProductUnit();measured.setId(101L);measured.setStatus(1);measured.setIsBaseUnit(1);measured.setUnitName("升");measured.setBaseQuantityPerUnit(BigDecimal.ONE);
        when(units.getUnits(1L)).thenReturn(List.of(measured));
        when(balances.sumAvailableBaseQuantityGroupedByProduct(10L))
                .thenReturn(Map.of(1L, new BigDecimal("0.0007")));
        when(balances.findSaleableStock(eq(10L),anyList())).thenReturn(List.of(new SaleableStock(1L,101L,new BigDecimal("0.0007"))));
        listing(1L, List.of(component(1L, "小瓶", "1")));
        assertEquals(BigDecimal.ONE, query().get(0).getStock());
    }

    @Test void actualSaleableQueryReplacesUnfilteredSummaryAndChecksOriginalUnit() {
        listing(1L,List.of(component(1L,"箱","2")));
        when(balances.findSaleableStock(eq(10L),anyList())).thenReturn(List.of(new SaleableStock(1L,101L,new BigDecimal("80"))));
        assertEquals(new BigDecimal("2"),query().get(0).getStock());
        verify(balances).findSaleableStock(10L,List.of(1L));
        verify(balances,never()).sumAvailableBaseQuantityGroupedByProduct(anyLong());
        when(balances.findSaleableStock(eq(10L),anyList())).thenReturn(List.of(new SaleableStock(1L,999L,new BigDecimal("80"))));
        assertNull(query().get(0).getStock(),"原批次单位冲突必须未知，不能混加");
    }

    @Test void missingUnitsAreUnknownAndHealthyPackagesSurviveInEitherOrder() {
        listing(1L,List.of(component(1L,"箱","1")));
        listing(2L,List.of(component(2L,"瓶","5")));
        when(units.getUnits(1L)).thenThrow(new IllegalStateException("无启用单位"));
        when(units.requireEnabledUnit(1L,"箱",ProductUnitService.UnitOperation.SALE)).thenThrow(new IllegalStateException("无启用单位"));
        var first=query();assertNull(first.get(0).getStock());assertEquals(new BigDecimal("10"),first.get(1).getStock());
        java.util.Collections.reverse(listed);
        var second=query();assertEquals(new BigDecimal("10"),second.get(0).getStock());assertNull(second.get(1).getStock());
    }
    @Test void queryFailureIsNotZeroStockAndAbsentStoreIsUnknown() {
        listing(1L,List.of(component(1L,"瓶","1")));
        when(balances.findSaleableStock(eq(10L),anyList())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class,this::query);
        assertNull(service.queryPackages(new ProductPackageQuery()).getRecords().get(0).getStock());
    }
    @Test void indivisibleBaseCannotFormFractionalPackageMaterial() {
        unit(1L,"小瓶","0.0004");listing(1L,List.of(component(1L,"小瓶","1")));
        assertNull(query().get(0).getStock());
    }

    private final java.util.ArrayList<ProductPackage> listed = new java.util.ArrayList<>();
    private void listing(long id, List<ProductPackageItem> items) {
        ProductPackage pack = new ProductPackage(); pack.setId(id); pack.setName("套餐" + id);
        pack.setStatus(1); pack.setSuggestedRetailPrice(BigDecimal.TEN); listed.add(pack);
        Page<ProductPackage> page = new Page<>(1, 10); page.setRecords(listed); page.setTotalRow(listed.size());
        when(packages.findPage(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);
        when(components.findItemsByPackageId(id)).thenReturn(items);
    }
    private List<ProductPackageDetailVO> query() {
        ProductPackageQuery query = new ProductPackageQuery(); query.setStoreId(10L);
        return service.queryPackages(query).getRecords();
    }
    private ProductPackageItem component(long id, String name, String quantity) {
        ProductPackageItem item = new ProductPackageItem(); item.setProductId(id);
        item.setUnit(name); item.setQuantity(new BigDecimal(quantity)); return item;
    }
    private void unit(long id, String name, String coefficient) {
        ProductUnit unit = new ProductUnit(); unit.setUnitName(name);
        unit.setId(100L+id);unit.setStatus(1);unit.setIsBaseUnit("瓶".equals(name)?1:0);
        if("瓶".equals(name))when(units.getUnits(id)).thenReturn(List.of(unit));
        unit.setBaseQuantityPerUnit(new BigDecimal(coefficient));
        when(units.requireEnabledUnit(id, name, ProductUnitService.UnitOperation.SALE)).thenReturn(unit);
    }
}
