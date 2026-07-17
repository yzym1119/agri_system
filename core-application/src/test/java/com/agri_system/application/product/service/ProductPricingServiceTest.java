package com.agri_system.application.product.service;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductPrice;
import com.agri_system.domain.product.repository.ProductPriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPricingServiceTest {

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private ProductPricingService productPricingService;

    @Test
    void shouldUseVipRuleWithStoreAndLevelPriority() {
        Product product = buildProduct();
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
}
