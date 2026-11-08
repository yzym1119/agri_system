package com.agri_system.application.order.service;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.product.dto.vo.ProductDetailVO;
import com.agri_system.application.product.service.ProductApplicationService;
import com.agri_system.application.productpackage.service.ProductPackageApplicationService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PosCatalogApplicationServiceTest {
    final ProductApplicationService products=mock(ProductApplicationService.class);
    final ProductPackageApplicationService packages=mock(ProductPackageApplicationService.class);
    final InventoryApplicationService inventory=mock(InventoryApplicationService.class);
    final CustomerRepository customers=mock(CustomerRepository.class);
    final PosCatalogApplicationService service=new PosCatalogApplicationService(products,packages,inventory,customers);

    @Test void catalogExcludesDisabledProductsAndNeverExposesPurchaseCost() {
        var enabled=new ProductDetailVO(); enabled.setId(1L); enabled.setStatus(1); enabled.setName("正常商品");
        enabled.setMainUnit("袋"); enabled.setAvgCostPrice(new BigDecimal("12")); enabled.setRemark("内部采购备注");
        Page<ProductDetailVO> page=new Page<>(); page.setRecords(List.of(enabled)); page.setTotalRow(1);
        when(products.queryProducts(any())).thenAnswer(inv->{
            com.agri_system.application.product.dto.query.ProductQuery query=inv.getArgument(0);
            assertEquals(1,query.getStatus()); return page;
        });
        var result=service.products(1,100);
        assertEquals(1,result.getRecords().size());
        var sale=result.getRecords().get(0);
        assertEquals("袋",sale.getMainUnit()); assertNull(sale.getAvgCostPrice()); assertNull(sale.getRemark());
        assertEquals(new BigDecimal("12"),enabled.getAvgCostPrice(),"不能修改共用来源对象");
    }

    @Test void disabledFirstPageMustNotHideEnabledProductsOrInflateTotal() {
        var disabled=new ProductDetailVO(); disabled.setId(2L); disabled.setStatus(2);
        var enabled=new ProductDetailVO(); enabled.setId(1L); enabled.setStatus(1);
        when(products.queryProducts(any())).thenAnswer(inv->{
            com.agri_system.application.product.dto.query.ProductQuery query=inv.getArgument(0);
            Integer status=query.getStatus();
            // 模拟数据库先按状态筛选再分页：停用商品排第一，可售商品排第二。
            var matching=java.util.stream.Stream.of(disabled,enabled)
                    .filter(product->status==null || status.equals(product.getStatus())).toList();
            var page=new Page<ProductDetailVO>(); page.setPageNumber(1); page.setPageSize(1);
            page.setTotalRow(matching.size()); page.setRecords(matching.subList(0,1)); return page;
        });
        var result=service.products(1,1);
        assertEquals(List.of(1L),result.getRecords().stream().map(ProductDetailVO::getId).toList());
        assertEquals(1,result.getTotalRow(),"目录总数必须只计可售商品");
    }

    @Test void nonAdminCannotReadAnotherStoresStockOrPackageAvailability() {
        try(var user=new com.agri_system.application.support.EmployeeIdentityTestScope(9L,1L,false)) {
            assertThrows(IllegalArgumentException.class,()->service.inventory(2L,1,100));
            assertThrows(IllegalArgumentException.class,()->service.packages(2L,1,100));
            verifyNoInteractions(inventory,packages);
        }
    }

    @Test void staffStoreIsUsedWhenCallerOmitsIt() {
        try(var user=new com.agri_system.application.support.EmployeeIdentityTestScope(9L,1L,false)) {
            when(inventory.queryInventoryPage(any())).thenAnswer(inv->{
                com.agri_system.application.inventory.dto.query.InventoryQuery q=inv.getArgument(0);
                assertEquals(1L,q.getStoreId()); return new Page<>();
            });
            service.inventory(null,1,100); verify(inventory).queryInventoryPage(any());
        }
    }

    @Test void anonymousIdentityCannotUseScopedReads() {
        try(var user=new com.agri_system.application.support.EmployeeIdentityTestScope(null,null,false)) {
            assertThrows(IllegalArgumentException.class,()->service.inventory(1L,1,100));
            verifyNoInteractions(inventory);
        }
    }

    @Test void posCustomerSearchOnlyCarriesOrderContextAndRealVipValidity() {
        Customer vip=new Customer();
        vip.setId(9101L); vip.setCode("C001"); vip.setName("张三"); vip.setPhone("13900000001");
        vip.setAddress("联调地址1号"); vip.setType(2); vip.setVipLevel(1); vip.setStatus(1);
        vip.setVipEndDate(java.time.LocalDate.now().plusDays(10));
        when(customers.findActiveByKeyword("张",20)).thenReturn(List.of(vip));
        var rows=service.customers("张",20);
        assertEquals(1,rows.size());
        assertEquals("张三",rows.get(0).name());
        assertEquals("13900000001",rows.get(0).phone());
        assertEquals("联调地址1号",rows.get(0).address());
        assertTrue(rows.get(0).vipActive(),"会员有效期按真实到期日判定");
        vip.setVipEndDate(java.time.LocalDate.now().minusDays(1));
        assertFalse(service.customers("张",20).get(0).vipActive(),"过期会员不得报成会员价");
    }
}
