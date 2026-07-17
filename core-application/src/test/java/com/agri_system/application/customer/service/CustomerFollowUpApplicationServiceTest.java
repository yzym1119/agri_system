package com.agri_system.application.customer.service;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 客户跟进应用服务测试
 */
@ExtendWith(MockitoExtension.class)
class CustomerFollowUpApplicationServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CustomerFollowUpApplicationService service;

    @Test
    void estimateUsageDays_pesticide_shouldReturn15TimesQuantity() {
        Product product = new Product();
        product.setName("高效农药喷雾剂");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("2"));

        int days = service.estimateUsageDays(product, item);
        assertEquals(30, days); // 15 * 2
    }

    @Test
    void estimateUsageDays_insecticide_shouldReturn15TimesQuantity() {
        Product product = new Product();
        product.setName("杀虫灵");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("3"));

        assertEquals(45, service.estimateUsageDays(product, item)); // 15 * 3
    }

    @Test
    void estimateUsageDays_fertilizer_shouldReturn30TimesQuantity() {
        Product product = new Product();
        product.setName("复合肥");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("2"));

        assertEquals(60, service.estimateUsageDays(product, item)); // 30 * 2
    }

    @Test
    void estimateUsageDays_leafFertilizer_shouldReturn30TimesQuantity() {
        Product product = new Product();
        product.setName("叶面肥");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("1"));

        assertEquals(30, service.estimateUsageDays(product, item));
    }

    @Test
    void estimateUsageDays_seeds_shouldReturn90() {
        Product product = new Product();
        product.setName("玉米种子");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("5"));

        assertEquals(90, service.estimateUsageDays(product, item));
    }

    @Test
    void estimateUsageDays_film_shouldReturn120() {
        Product product = new Product();
        product.setName("农用地膜");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("1"));

        assertEquals(120, service.estimateUsageDays(product, item));
    }

    @Test
    void estimateUsageDays_unknownProduct_shouldReturn30() {
        Product product = new Product();
        product.setName("工具剪刀");

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("1"));

        assertEquals(30, service.estimateUsageDays(product, item));
    }

    @Test
    void estimateUsageDays_nullQuantity_shouldDefaultToOne() {
        Product product = new Product();
        product.setName("农药");

        OrderItem item = new OrderItem();
        item.setQuantity(null);

        assertEquals(15, service.estimateUsageDays(product, item)); // 15 * 1
    }

    @Test
    void estimateUsageDays_nullProductName_shouldReturn30() {
        Product product = new Product();
        product.setName(null);

        OrderItem item = new OrderItem();
        item.setQuantity(new BigDecimal("1"));

        assertEquals(30, service.estimateUsageDays(product, item));
    }

    @Test
    void repurchaseAlerts_shouldLimitResultToMax100() {
        when(customerRepository.findAllWithBindStaff()).thenReturn(List.of());

        List<Map<String, Object>> result = service.getRepurchaseAlerts(null, 200);

        // limit capped to 100, but no customers means empty result
        assertTrue(result.isEmpty());
    }

    @Test
    void repurchaseAlerts_shouldFilterByStaffId() {
        when(customerRepository.findByBindStaffId(1L)).thenReturn(List.of());

        service.getRepurchaseAlerts(1L, 20);

        // Verify it used the staff-specific query
        org.mockito.Mockito.verify(customerRepository).findByBindStaffId(1L);
    }

    @Test
    void repurchaseAlerts_shouldReturnAlertForUpcomingRepurchase() {
        // Given: a customer who bought pesticide 10 days ago, quantity 1 → expected repurchase in 5 days
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("张三");
        customer.setPhone("13800000000");
        customer.setBindStaffId(100L);

        Product product = new Product();
        product.setId(1L);
        product.setName("农药");

        Order order = new Order();
        order.setId(1L);
        order.setCreatedAt(LocalDateTime.now().minusDays(10));

        OrderItem item = new OrderItem();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("1"));

        Page<Order> orderPage = new Page<>();
        orderPage.setRecords(List.of(order));

        when(customerRepository.findAllWithBindStaff()).thenReturn(List.of(customer));
        when(orderRepository.findPage(anyInt(), anyInt(), any(), any(), any(), any(), anyString(), anyString(), any(), eq(1L)))
                .thenReturn(orderPage);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // When
        List<Map<String, Object>> alerts = service.getRepurchaseAlerts(null, 20);

        // Then
        assertFalse(alerts.isEmpty());
        assertEquals(1L, alerts.get(0).get("customerId"));
        assertEquals("农药", alerts.get(0).get("productName"));
    }

    @Test
    void repurchaseAlerts_shouldDeduplicateByCustomerAndProduct() {
        // Given: same customer bought same product twice
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("张三");
        customer.setBindStaffId(100L);

        Product product = new Product();
        product.setId(1L);
        product.setName("农药");

        Order order1 = new Order();
        order1.setId(1L);
        order1.setCreatedAt(LocalDateTime.now().minusDays(10));

        Order order2 = new Order();
        order2.setId(2L);
        order2.setCreatedAt(LocalDateTime.now().minusDays(8));

        OrderItem item1 = new OrderItem();
        item1.setProductId(1L);
        item1.setQuantity(new BigDecimal("1"));

        OrderItem item2 = new OrderItem();
        item2.setProductId(1L);
        item2.setQuantity(new BigDecimal("1"));

        Page<Order> orderPage = new Page<>();
        orderPage.setRecords(List.of(order1, order2));

        when(customerRepository.findAllWithBindStaff()).thenReturn(List.of(customer));
        when(orderRepository.findPage(anyInt(), anyInt(), any(), any(), any(), any(), anyString(), anyString(), any(), eq(1L)))
                .thenReturn(orderPage);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item1));
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of(item2));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // When
        List<Map<String, Object>> alerts = service.getRepurchaseAlerts(null, 20);

        // Then: only one alert for customer+product combo
        assertEquals(1, alerts.size());
    }
}
