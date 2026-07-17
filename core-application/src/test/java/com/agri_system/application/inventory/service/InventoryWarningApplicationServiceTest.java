package com.agri_system.application.inventory.service;

import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 库存预警应用服务测试
 */
@ExtendWith(MockitoExtension.class)
class InventoryWarningApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryWarningApplicationService service;

    @Test
    void lowStock_shouldReturnWarningWhenStockBelowMin() {
        // Given
        Product product = new Product();
        product.setId(1L);
        product.setName("尿素");
        product.setCode("P001");
        product.setMinStock(new BigDecimal("100"));
        product.setReorderQuantity(new BigDecimal("50"));

        when(productRepository.findAllWithMinStock()).thenReturn(List.of(product));
        when(inventoryRepository.sumQuantityByProduct(eq(1L), any())).thenReturn(new BigDecimal("30"));

        // When
        List<Map<String, Object>> warnings = service.getLowStockWarnings(null);

        // Then
        assertEquals(1, warnings.size());
        Map<String, Object> warning = warnings.get(0);
        assertEquals(1L, warning.get("productId"));
        assertEquals("尿素", warning.get("productName"));
        assertEquals(new BigDecimal("70"), warning.get("gap"));
    }

    @Test
    void lowStock_shouldNotReturnWarningWhenStockAboveMin() {
        // Given
        Product product = new Product();
        product.setId(1L);
        product.setName("复合肥");
        product.setMinStock(new BigDecimal("50"));

        when(productRepository.findAllWithMinStock()).thenReturn(List.of(product));
        when(inventoryRepository.sumQuantityByProduct(eq(1L), any())).thenReturn(new BigDecimal("100"));

        // When
        List<Map<String, Object>> warnings = service.getLowStockWarnings(null);

        // Then
        assertTrue(warnings.isEmpty());
    }

    @Test
    void lowStock_shouldSortByGapDescending() {
        // Given
        Product product1 = new Product();
        product1.setId(1L);
        product1.setName("产品A");
        product1.setMinStock(new BigDecimal("100"));

        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("产品B");
        product2.setMinStock(new BigDecimal("200"));

        when(productRepository.findAllWithMinStock()).thenReturn(List.of(product1, product2));
        when(inventoryRepository.sumQuantityByProduct(eq(1L), any())).thenReturn(new BigDecimal("80")); // gap = 20
        when(inventoryRepository.sumQuantityByProduct(eq(2L), any())).thenReturn(new BigDecimal("50")); // gap = 150

        // When
        List<Map<String, Object>> warnings = service.getLowStockWarnings(null);

        // Then
        assertEquals(2, warnings.size());
        assertEquals(2L, warnings.get(0).get("productId")); // gap 150 > gap 20
    }

    @Test
    void lowStock_shouldFilterByStoreId() {
        // Given
        Product product = new Product();
        product.setId(1L);
        product.setName("农药");
        product.setMinStock(new BigDecimal("50"));

        when(productRepository.findAllWithMinStock()).thenReturn(List.of(product));
        when(inventoryRepository.sumQuantityByProduct(eq(1L), eq(100L))).thenReturn(new BigDecimal("20"));

        // When
        List<Map<String, Object>> warnings = service.getLowStockWarnings(100L);

        // Then
        assertEquals(1, warnings.size());
    }

    @Test
    void overstock_shouldReturnWarningWhenStockAboveMax() {
        // Given
        Product product = new Product();
        product.setId(1L);
        product.setName("地膜");
        product.setMaxStock(new BigDecimal("500"));

        when(productRepository.findAllWithMaxStock()).thenReturn(List.of(product));
        when(inventoryRepository.sumQuantityByProduct(eq(1L), any())).thenReturn(new BigDecimal("800"));

        // When
        List<Map<String, Object>> warnings = service.getOverstockWarnings(null);

        // Then
        assertEquals(1, warnings.size());
        assertEquals(new BigDecimal("300"), warnings.get(0).get("excess"));
    }

    @Test
    void overstock_shouldNotReturnWarningWhenStockBelowMax() {
        // Given
        Product product = new Product();
        product.setId(1L);
        product.setName("种子");
        product.setMaxStock(new BigDecimal("1000"));

        when(productRepository.findAllWithMaxStock()).thenReturn(List.of(product));
        when(inventoryRepository.sumQuantityByProduct(eq(1L), any())).thenReturn(new BigDecimal("500"));

        // When
        List<Map<String, Object>> warnings = service.getOverstockWarnings(null);

        // Then
        assertTrue(warnings.isEmpty());
    }

    @Test
    void lowStock_shouldSkipProductWithNullMinStock() {
        // Given
        Product product = new Product();
        product.setId(1L);
        product.setName("无阈值产品");
        product.setMinStock(null);

        when(productRepository.findAllWithMinStock()).thenReturn(List.of(product));

        // When
        List<Map<String, Object>> warnings = service.getLowStockWarnings(null);

        // Then
        assertTrue(warnings.isEmpty());
    }
}
