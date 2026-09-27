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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPricingServiceTest {

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductUnitService productUnitService;

    @InjectMocks
    private ProductPricingService productPricingService;

    @Test
    void shouldUseVipRuleWithStoreAndLevelPriority() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        Customer customer = new Customer();
        customer.setId(100L);
        customer.setType(2);
        customer.setVipLevel(3);
        customer.setVipEndDate(LocalDate.now().plusDays(30));

        ProductPrice globalVipRule = buildRule(null, 2, null, 2, "20.00");
        ProductPrice exactVipRule = buildRule(10L, 2, 3, 2, "18.00");

        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(globalVipRule, exactVipRule));

        BigDecimal price = productPricingService.resolveOrderItemUnitPrice(product, 10L, 100L, "箱");

        assertEquals(new BigDecimal("54.00"), price);
    }

    @Test
    void shouldDowngradeExpiredVipToNormalCustomerRule() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        Customer customer = new Customer();
        customer.setId(101L);
        customer.setType(2);
        customer.setVipLevel(2);
        customer.setVipEndDate(LocalDate.now().minusDays(1));

        ProductPrice normalRule = buildRule(10L, 1, null, 1, "16.00");
        ProductPrice vipRule = buildRule(10L, 2, 2, 2, "12.00");

        when(customerRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(vipRule, normalRule));

        BigDecimal price = productPricingService.resolveOrderItemUnitPrice(product, 10L, 101L, "瓶");

        assertEquals(new BigDecimal("16.00"), price);
    }

    @Test
    void shouldFallbackToVipBasePriceWhenNoRuleMatched() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        Customer customer = new Customer();
        customer.setId(102L);
        customer.setType(2);
        customer.setVipLevel(1);
        customer.setVipEndDate(LocalDate.now().plusDays(7));

        when(customerRepository.findById(102L)).thenReturn(Optional.of(customer));
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of());

        BigDecimal price = productPricingService.resolveOrderItemUnitPrice(product, 10L, 102L, "瓶");

        assertEquals(new BigDecimal("15.00"), price);
    }

    @Test
    void boxPriceCanBe220WhileBottlePriceIs10() {
        Product product = buildProduct();
        product.setMainToAuxRatio(new BigDecimal("24"));
        product.setMinPrice(null);
        givenProduct(product);
        givenUnitModel("NEW_CHAIN_ENABLED");
        when(productUnitService.requireEnabledUnit(1L, "箱", ProductUnitService.UnitOperation.SALE))
                .thenReturn(buildUnit(11L, "箱"));
        when(productUnitService.requireEnabledUnit(1L, "瓶", ProductUnitService.UnitOperation.SALE))
                .thenReturn(buildUnit(12L, "瓶"));

        ProductPrice boxRule = buildUnitRule("箱", null, "box-v1", 1, "220");
        ProductPrice bottleRule = buildUnitRule("瓶", null, "bottle-v1", 1, "10");
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(boxRule, bottleRule));

        PriceQuote boxQuote = productPricingService.quote(context("箱"));
        assertThat(boxQuote.suggestedUnitPrice()).isEqualByComparingTo("220");
        assertThat(boxQuote.saleUnit()).isEqualTo("箱");
        assertThat(boxQuote.saleUnitId()).isEqualTo(11L);
        assertThat(boxQuote.ruleVersion()).isEqualTo("box-v1");
        assertThat(boxQuote.legacyDerived()).isFalse();
        assertThat(boxQuote.quoteId()).isNotBlank();

        PriceQuote bottleQuote = productPricingService.quote(context("瓶"));
        assertThat(bottleQuote.suggestedUnitPrice()).isEqualByComparingTo("10");
        assertThat(bottleQuote.saleUnitId()).isEqualTo(12L);
        assertThat(bottleQuote.quoteId()).isNotEqualTo(boxQuote.quoteId());
    }

    @Test
    void explicitSaleUnitRuleBeatsLegacyRuleForTheSameUnit() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        ProductPrice legacyRule = buildRule(10L, null, null, 1, "16.00");
        ProductPrice explicitBoxRule = buildUnitRule("箱", null, "box-v1", 1, "220");
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(legacyRule, explicitBoxRule));

        assertThat(productPricingService.quote(context("箱")).suggestedUnitPrice())
                .isEqualByComparingTo("220");
        assertThat(productPricingService.quote(context("瓶")).suggestedUnitPrice())
                .isEqualByComparingTo("16.00");
    }

    @Test
    void businessDateOutsideEffectiveRangeExcludesRule() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        ProductPrice activeRule = buildRule(10L, null, null, 1, "16.00");
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(activeRule));

        PriceQuote activeQuote = productPricingService.quote(context("瓶", "POS", LocalDate.now()));
        assertThat(activeQuote.suggestedUnitPrice()).isEqualByComparingTo("16.00");
        assertThat(activeQuote.referenceFloor()).isEqualByComparingTo("14.00");
        assertThat(activeQuote.belowReference()).isFalse();

        PriceQuote expiredQuote = productPricingService.quote(
                context("箱", "POS", LocalDate.now().plusDays(5)));
        assertThat(expiredQuote.suggestedUnitPrice()).isEqualByComparingTo("63.00");
        assertThat(expiredQuote.legacyDerived()).isTrue();
        assertThat(expiredQuote.ruleVersion()).isNull();
    }

    @Test
    void channelSpecificRuleBeatsGenericRule() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        ProductPrice genericRule = buildUnitRule("箱", null, "generic-v1", 1, "100");
        ProductPrice posRule = buildUnitRule("箱", "POS", "pos-v1", 1, "220");
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(genericRule, posRule));

        assertThat(productPricingService.quote(context("箱", "POS", LocalDate.now())).suggestedUnitPrice())
                .isEqualByComparingTo("220");
        assertThat(productPricingService.quote(context("箱", null, LocalDate.now())).suggestedUnitPrice())
                .isEqualByComparingTo("100");
        assertThat(productPricingService.quote(context("箱", "MINI", LocalDate.now())).suggestedUnitPrice())
                .isEqualByComparingTo("100");
    }

    @Test
    void referenceFloorUsesLowestLimitRuleAndFlagsBelowReference() {
        Product product = buildProduct();
        givenLegacyProductAndUnitModel(product);

        ProductPrice saleRule = buildUnitRule("瓶", null, "bottle-v1", 1, "10");
        ProductPrice floorRule = buildUnitRule("瓶", null, "floor-v1", 3, "12");
        when(productPriceRepository.findByProductId(1L)).thenReturn(List.of(saleRule, floorRule));

        PriceQuote quote = productPricingService.quote(context("瓶"));
        assertThat(quote.suggestedUnitPrice()).isEqualByComparingTo("10");
        assertThat(quote.referenceFloor()).isEqualByComparingTo("12");
        assertThat(quote.belowReference()).isTrue();
    }

    @Test
    void rejectsUnitNotAllowedForSaleOnNewChain() {
        Product product = buildProduct();
        givenProduct(product);
        givenUnitModel("NEW_CHAIN_ENABLED");
        when(productUnitService.requireEnabledUnit(1L, "瓶", ProductUnitService.UnitOperation.SALE))
                .thenThrow(new IllegalArgumentException("单位不允许销售: 瓶"));

        assertThatThrownBy(() -> productPricingService.quote(context("瓶")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("瓶");
    }

    @Test
    void rejectsPersistedUnitWithAllowSaleZeroOnNewChainPath() {
        Product product = buildProduct();
        givenProduct(product);

        ProductUnitRepository unitRepository = mock(ProductUnitRepository.class);
        ProductUnit baseUnit = buildUnit(11L, "箱");
        baseUnit.setIsBaseUnit(1);
        ProductUnit noSaleUnit = buildUnit(12L, "瓶");
        noSaleUnit.setIsBaseUnit(0);
        noSaleUnit.setAllowSale(0);
        when(unitRepository.findByProductId(1L)).thenReturn(List.of(baseUnit, noSaleUnit));
        when(unitRepository.findEnabledByProductId(1L)).thenReturn(List.of(baseUnit, noSaleUnit));

        ProductUnitService realUnitService = new ProductUnitService(unitRepository, productRepository);
        ProductPricingService service = new ProductPricingService(
                productPriceRepository, customerRepository, productRepository, realUnitService);

        assertThatThrownBy(() -> service.quote(context("瓶")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单位不允许销售");
    }

    private PriceContext context(String saleUnit) {
        return context(saleUnit, "POS", LocalDate.now());
    }

    private PriceContext context(String saleUnit, String channel, LocalDate businessDate) {
        return new PriceContext(1L, 10L, channel, null, null, saleUnit, businessDate);
    }

    private void givenProduct(Product product) {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
    }

    private void givenUnitModel(String status) {
        when(productUnitService.getUnitModelStatus(1L)).thenReturn(status);
    }

    private void givenLegacyProductAndUnitModel(Product product) {
        givenProduct(product);
        givenUnitModel("LEGACY");
    }

    private ProductUnit buildUnit(Long id, String unitName) {
        ProductUnit unit = new ProductUnit();
        unit.setId(id);
        unit.setProductId(1L);
        unit.setUnitName(unitName);
        unit.setStatus(1);
        unit.setAllowSale(1);
        return unit;
    }

    private Product buildProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setName("测试商品");
        product.setMainUnit("箱");
        product.setAuxUnit("瓶");
        product.setMainToAuxRatio(new BigDecimal("3"));
        product.setSuggestedRetailPrice(new BigDecimal("21.00"));
        product.setVipPrice(new BigDecimal("15.00"));
        product.setMinPrice(new BigDecimal("14.00"));
        product.setOnlinePrice(new BigDecimal("22.00"));
        return product;
    }

    private ProductPrice buildRule(Long storeId, Integer customerType, Integer vipLevel, Integer priceType, String price) {
        ProductPrice rule = new ProductPrice();
        rule.setProductId(1L);
        rule.setStoreId(storeId);
        rule.setCustomerType(customerType);
        rule.setVipLevel(vipLevel);
        rule.setPriceType(priceType);
        rule.setPrice(new BigDecimal(price));
        rule.setStatus(1);
        rule.setEffectiveDate(LocalDate.now().minusDays(1));
        rule.setExpiryDate(LocalDate.now().plusDays(1));
        return rule;
    }

    private ProductPrice buildUnitRule(String saleUnit, String channel, String ruleVersion,
                                       Integer priceType, String price) {
        ProductPrice rule = buildRule(10L, null, null, priceType, price);
        rule.setSaleUnit(saleUnit);
        rule.setChannel(channel);
        rule.setRuleVersion(ruleVersion);
        return rule;
    }
}
