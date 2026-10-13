package com.agri_system.application.product.service;

import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductPrice;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductPriceRepository;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.repository.ProductUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 经营契约：基准单位价格、唯一换算、会员有效性与禁止隐式兜底。 */
class ProductPricingServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductPriceRepository rules = mock(ProductPriceRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ProductUnitRepository units = mock(ProductUnitRepository.class);
    private final ProductUnitService unitService = new ProductUnitService(units, products);
    private final ProductPricingService pricing = new ProductPricingService(customers, products, unitService);
    private Product product;

    @BeforeEach
    void setup() {
        product = new Product();
        product.setId(1L); product.setName("农资"); product.setMainUnit("箱");
        product.setAuxUnit("瓶"); product.setMainToAuxRatio(new BigDecimal("3"));
        product.setSuggestedRetailPrice(new BigDecimal("10"));
        product.setVipPrice(new BigDecimal("9")); product.setMinPrice(new BigDecimal("8"));
        product.setOnlinePrice(new BigDecimal("99"));
        when(products.findById(1L)).thenReturn(Optional.of(product));
        List<ProductUnit> configured = List.of(unit(11L, "瓶", "1", true),
                unit(12L, "箱", "3", false), unit(13L, "件", "12", false));
        when(units.findByProductId(1L)).thenReturn(configured);
        when(units.findEnabledByProductId(1L)).thenReturn(configured);
    }

    @Test void quotesAllUnitsUsingPersistedCoefficient() {
        assertThat(quote("瓶", 1).suggestedUnitPrice()).isEqualByComparingTo("10");
        assertThat(quote("箱", 1).suggestedUnitPrice()).isEqualByComparingTo("30");
        assertThat(quote("件", 1).suggestedUnitPrice()).isEqualByComparingTo("120");
        assertThat(quote("件", 1).saleUnitId()).isEqualTo(13L);
    }
    @Test void productLegacyRatioDoesNotOverridePersistedUnit() {
        product.setMainToAuxRatio(new BigDecimal("999"));
        assertThat(quote("箱", 1).suggestedUnitPrice()).isEqualByComparingTo("30");
    }
    @Test void floorUsesSameUnitAsQuote() {
        assertThat(quote("件", 1).referenceFloor()).isEqualByComparingTo("96");
    }
    @Test void minimumPriceIsNeverASellingPrice() {
        product.setSuggestedRetailPrice(null); product.setVipPrice(null);
        assertThatThrownBy(() -> quote("瓶", 1)).hasMessageContaining("零售价");
    }
    @Test void onlinePriceIsNeverAnImplicitFallback() {
        product.setSuggestedRetailPrice(null); product.setVipPrice(null); product.setMinPrice(null);
        assertThatThrownBy(() -> quote("瓶", 1)).hasMessageContaining("零售价");
    }
    @Test void historicalStrategyCannotOverrideProductPrice() {
        ProductPrice strategy = new ProductPrice();
        strategy.setProductId(1L); strategy.setSaleUnit("瓶"); strategy.setPriceType(1);
        strategy.setPrice(BigDecimal.ONE); strategy.setStatus(1);
        when(rules.findByProductId(1L)).thenReturn(List.of(strategy));
        assertThat(quote("瓶", 1).suggestedUnitPrice()).isEqualByComparingTo("10");
    }
    @Test void vipUsesOptionalMemberPriceWithoutTierDifferences() {
        assertThat(quote("件", 2).suggestedUnitPrice()).isEqualByComparingTo("108");
        product.setVipPrice(BigDecimal.ZERO);
        assertThat(quote("件", 2).suggestedUnitPrice()).isEqualByComparingTo("120");
    }
    @Test void expiredVipGetsRetailPrice() {
        Customer customer = new Customer(); customer.setId(100L); customer.setType(2);
        customer.setVipLevel(3); customer.setVipEndDate(LocalDate.now().minusDays(1));
        when(customers.findById(100L)).thenReturn(Optional.of(customer));
        assertThat(pricing.resolveOrderItemUnitPrice(product, 10L, 100L, "箱"))
                .isEqualByComparingTo("30");
    }
    @Test void channelAndStoreDoNotChangeDefaultPrice() {
        PriceQuote online = pricing.quote(new PriceContext(1L, 99L, "MINI", 1, 4, "箱", LocalDate.now()));
        assertThat(online.suggestedUnitPrice()).isEqualByComparingTo(quote("箱", 1).suggestedUnitPrice());
    }
    @Test void missingPersistedUnitsCannotUseLegacyMode() {
        when(units.findByProductId(1L)).thenReturn(List.of());
        when(units.findEnabledByProductId(1L)).thenReturn(List.of());
        assertThatThrownBy(() -> quote("瓶", 1)).hasMessageContaining("持久化单位");
    }
    @Test void cannotQuoteNonSaleUnit() {
        ProductUnit noSale = unit(11L, "瓶", "1", true); noSale.setAllowSale(0);
        when(units.findEnabledByProductId(1L)).thenReturn(List.of(noSale));
        assertThatThrownBy(() -> quote("瓶", 1)).hasMessageContaining("不允许销售");
    }
    @Test void moneyRoundsToTwoDecimalsAfterConversion() {
        product.setSuggestedRetailPrice(new BigDecimal("10.01"));
        when(units.findEnabledByProductId(1L)).thenReturn(List.of(unit(11L, "瓶", "1", true), unit(14L, "半瓶", "0.5", false)));
        assertThat(quote("半瓶", 1).suggestedUnitPrice()).isEqualTo(new BigDecimal("5.01"));
    }
    private PriceQuote quote(String name, int type) {
        return pricing.quote(new PriceContext(1L, 10L, "POS", type, null, name, LocalDate.now()));
    }
    private ProductUnit unit(Long id, String name, String coefficient, boolean base) {
        ProductUnit unit = new ProductUnit(); unit.setId(id); unit.setProductId(1L); unit.setUnitName(name);
        unit.setBaseQuantityPerUnit(new BigDecimal(coefficient)); unit.setIsBaseUnit(base ? 1 : 0);
        unit.setStatus(1); unit.setAllowSale(1); return unit;
    }
}
