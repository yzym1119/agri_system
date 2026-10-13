package com.agri_system.application.productpackage.service;

import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.repository.ProductUnitRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackagePricingServiceTest {
    private final ProductPackageRepository packages = mock(ProductPackageRepository.class);
    private final ProductPackageItemRepository items = mock(ProductPackageItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductUnitRepository unitRepository = mock(ProductUnitRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ProductUnitService units = new ProductUnitService(unitRepository, products);
    private final ProductPricingService pricing = new ProductPricingService(customers, products, units);
    private final PackagePricingService service = new PackagePricingService(packages, items, products, units, pricing);
    private ProductPackage pack;

    @BeforeEach void setup() {
        pack = new ProductPackage(); pack.setId(7L); pack.setName("农资组合"); pack.setStatus(1);
        pack.setSuggestedRetailPrice(new BigDecimal("80")); pack.setVipPrice(new BigDecimal("70")); pack.setMinPrice(new BigDecimal("60"));
        when(packages.findById(7L)).thenReturn(Optional.of(pack));
        product(1L, "60", 1); product(2L, "40", 0);
        when(items.findItemsByPackageId(7L)).thenReturn(List.of(item(1L, "1"), item(2L, "1")));
    }
    @Test void packagePriceIsIndependentFromSumOfProductPrices() {
        var quote = quote("1", null);
        assertThat(quote.totalAmount()).isEqualByComparingTo("80");
        assertThat(quote.items()).extracting(i -> i.totalPrice()).containsExactly(new BigDecimal("48.00"), new BigDecimal("32.00"));
        assertThat(quote.specialFundEligibleAmount()).isEqualByComparingTo("48");
    }
    @Test void twoSetsExpandRealQuantitiesAndUsePackagePrice() {
        var quote = quote("2", null);
        assertThat(quote.totalAmount()).isEqualByComparingTo("160");
        assertThat(quote.items().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(quote.items().get(0).totalPrice()).isEqualByComparingTo("96");
    }
    @Test void fractionalSetsCannotBeSold() {
        assertThatThrownBy(() -> quote("1.5", null)).hasMessageContaining("整数");
    }
    @Test void zeroSetsCannotBeSold() {
        assertThatThrownBy(() -> quote("0", null)).hasMessageContaining("套数");
    }
    @Test void vipPriceUsesRealUnexpiredCustomer() {
        var customer = new com.agri_system.domain.customer.model.Customer(); customer.setType(2); customer.setVipLevel(4);
        customer.setVipEndDate(LocalDate.now().plusDays(1));
        when(customers.findById(10L)).thenReturn(Optional.of(customer));
        assertThat(service.quote(7L,10L,1L,BigDecimal.ONE,null).totalAmount()).isEqualByComparingTo("70");
        customer.setVipEndDate(LocalDate.now().minusDays(1));
        assertThat(service.quote(7L,10L,1L,BigDecimal.ONE,null).totalAmount()).isEqualByComparingTo("80");
    }
    @Test void centRemainderIsDeterministicAndTotalsExactly() {
        product(1L,"1",1); product(2L,"1",0); product(3L,"1",0);
        when(items.findItemsByPackageId(7L)).thenReturn(List.of(item(1L,"1"), item(2L,"1"), item(3L,"1")));
        var quote = quote("1", "1.00");
        assertThat(quote.items()).extracting(i -> i.totalPrice()).containsExactly(new BigDecimal("0.34"),new BigDecimal("0.33"),new BigDecimal("0.33"));
        assertThat(quote.items().stream().map(i -> i.totalPrice()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1");
    }
    @Test void indivisibleUnitPriceDoesNotChangeAllocatedLineAmount() {
        when(items.findItemsByPackageId(7L)).thenReturn(List.of(item(1L,"7")));
        var quote = quote("1", "10.00");
        assertThat(quote.items().get(0).totalPrice()).isEqualByComparingTo("10");
        assertThat(quote.items().get(0).unitPrice()).isEqualByComparingTo("1.428571428571");
    }
    @Test void lowValueChildMayBeZeroButStillHasRealQuantity() {
        var quote = quote("1", "0.01");
        assertThat(quote.items().get(1).totalPrice()).isEqualByComparingTo("0");
        assertThat(quote.items().get(1).quantity()).isEqualByComparingTo("1");
    }
    @Test void downPackageCannotBeQuoted() {
        pack.setStatus(0);
        assertThatThrownBy(() -> quote("1", null)).hasMessageContaining("上架");
    }
    @Test void changedCompositionInvalidatesQuoteEvenAtSamePackagePrice() {
        var before = quote("1", null);
        when(items.findItemsByPackageId(7L)).thenReturn(List.of(item(1L,"2"),item(2L,"1")));
        assertThat(quote("1", null).quoteId()).isNotEqualTo(before.quoteId());
    }
    @Test void requestedDealPriceAndQuantityDoNotChangeContentSignature() {
        assertThat(quote("2", "50.00").quoteId()).isEqualTo(quote("1", null).quoteId());
    }
    @Test void inactiveProductPreventsSale() {
        Product product = products.findById(1L).orElseThrow(); product.setStatus(2);
        assertThatThrownBy(() -> quote("1", null)).hasMessageContaining("停用");
    }
    private com.agri_system.application.productpackage.dto.vo.PackagePriceQuote quote(String qty, String price) {
        return service.quote(7L,null,1L,new BigDecimal(qty),price==null?null:new BigDecimal(price));
    }
    private ProductPackageItem item(Long id, String quantity) {
        var item = new ProductPackageItem(); item.setId(id); item.setProductId(id); item.setQuantity(new BigDecimal(quantity));
        item.setUnit("瓶"); item.setSortOrder(id.intValue()); return item;
    }
    private void product(Long id, String price, int special) {
        Product product = new Product(); product.setId(id);product.setName("商品"+id);product.setMainUnit("瓶");
        product.setStatus(1); product.setSuggestedRetailPrice(new BigDecimal(price));product.setAllowSpecialFund(special);
        when(products.findById(id)).thenReturn(Optional.of(product));
        ProductUnit unit = new ProductUnit();unit.setId(id);unit.setProductId(id);unit.setUnitName("瓶");
        unit.setBaseQuantityPerUnit(BigDecimal.ONE);unit.setIsBaseUnit(1);unit.setStatus(1);unit.setAllowSale(1);
        when(unitRepository.findEnabledByProductId(id)).thenReturn(List.of(unit));
    }
}
