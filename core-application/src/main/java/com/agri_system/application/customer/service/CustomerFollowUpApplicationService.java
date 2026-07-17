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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户跟进应用服务
 */
@Service
@RequiredArgsConstructor
public class CustomerFollowUpApplicationService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    /**
     * 查询需要复购跟进的客户
     */
    public List<Map<String, Object>> getRepurchaseAlerts(Long staffId, int limit) {
        limit = Math.min(Math.max(limit, 1), 100);

        List<Customer> customers;
        if (staffId != null) {
            customers = customerRepository.findByBindStaffId(staffId);
        } else {
            customers = customerRepository.findAllWithBindStaff();
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeMonthsAgo = now.minusMonths(3);

        for (Customer customer : customers) {
            Page<Order> orders = orderRepository.findPage(1, 50, null, null,
                    null, null, "6,7,8,9",
                    threeMonthsAgo.toString(), null, customer.getId());

            if (orders.getRecords().isEmpty()) continue;

            for (Order order : orders.getRecords()) {
                List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                for (OrderItem item : items) {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product == null) continue;

                    int estimatedUsageDays = estimateUsageDays(product, item);
                    if (estimatedUsageDays <= 0) continue;

                    LocalDateTime purchaseDate = order.getCreatedAt();
                    LocalDateTime expectedRepurchase = purchaseDate.plusDays(estimatedUsageDays);

                    if (expectedRepurchase.isAfter(now.minusDays(7)) && expectedRepurchase.isBefore(now.plusDays(14))) {
                        Map<String, Object> alert = new LinkedHashMap<>();
                        alert.put("customerId", customer.getId());
                        alert.put("customerName", customer.getName());
                        alert.put("customerPhone", customer.getPhone());
                        alert.put("productName", product.getName());
                        alert.put("lastPurchaseDate", purchaseDate.toLocalDate().toString());
                        alert.put("expectedRepurchaseDate", expectedRepurchase.toLocalDate().toString());
                        alert.put("daysUntilRepurchase", ChronoUnit.DAYS.between(now.toLocalDate(), expectedRepurchase.toLocalDate()));
                        alert.put("bindStaffId", customer.getBindStaffId());
                        alerts.add(alert);
                    }
                }
            }
        }

        alerts.sort(Comparator.comparingLong(a -> (Long) a.get("daysUntilRepurchase")));

        // 去重
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (Map<String, Object> alert : alerts) {
            String key = alert.get("customerId") + "_" + alert.get("productName");
            if (seen.add(key)) {
                deduped.add(alert);
            }
        }

        return deduped.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 根据产品类型和购买数量估算使用天数
     */
    public int estimateUsageDays(Product product, OrderItem item) {
        String name = product.getName() != null ? product.getName() : "";
        double quantity = item.getQuantity() != null ? item.getQuantity().doubleValue() : 1;

        if (name.contains("农药") || name.contains("杀虫") || name.contains("杀菌") || name.contains("除草")) {
            return (int) (15 * quantity);
        } else if (name.contains("肥") || name.contains("营养") || name.contains("叶面")) {
            return (int) (30 * quantity);
        } else if (name.contains("种子") || name.contains("种苗")) {
            return 90;
        } else if (name.contains("膜") || name.contains("地膜")) {
            return 120;
        }
        return 30;
    }
}
