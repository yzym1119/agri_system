package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户复购提醒Controller
 * 根据客户历史购买记录 + 产品使用周期，智能推荐需要跟进的客户
 */
@RestController
@RequestMapping("/api/admin/customer/follow-up")
@Tag(name = "客户复购提醒", description = "基于购买历史的智能复购提醒")
@RequiredArgsConstructor
public class CustomerFollowUpController {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @GetMapping("/repurchase-alerts")
    @Operation(summary = "查询需要复购跟进的客户")
    @SaCheckPermission("customer:followup:manage")
    public ResponseEntity<List<Map<String, Object>>> repurchaseAlerts(
            @RequestParam(required = false) Long staffId,
            @RequestParam(defaultValue = "20") int limit) {

        // 安全：限制最大返回数量，防止数据泄露和资源耗尽
        limit = Math.min(Math.max(limit, 1), 100);

        // 查询所有归属指定员工的客户（或全部客户）
        List<Customer> customers;
        if (staffId != null) {
            customers = customerRepository.findByBindStaffId(staffId);
        } else {
            customers = customerRepository.findAllWithBindStaff();
        }

        List<Map<String, Object>> alerts = new ArrayList<>();

        for (Customer customer : customers) {
            // 查询客户最近3个月的已完成订单
            LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
            Page<Order> orders = orderRepository.findPage(1, 50, null, null,
                    null, null, "6,7,8,9", // 已完成/已发货状态
                    threeMonthsAgo.toString(), null, customer.getId());

            if (orders.getRecords().isEmpty()) continue;

            // 分析购买的农资产品及购买时间
            for (Order order : orders.getRecords()) {
                List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                for (OrderItem item : items) {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product == null) continue;

                    // 根据产品保质期/使用周期推算复购时间
                    int estimatedUsageDays = estimateUsageDays(product, item);
                    if (estimatedUsageDays <= 0) continue;

                    LocalDateTime purchaseDate = order.getCreatedAt();
                    LocalDateTime expectedRepurchase = purchaseDate.plusDays(estimatedUsageDays);
                    LocalDateTime now = LocalDateTime.now();

                    // 如果预期复购日期在过去7天到未来14天之间，则需要跟进
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

        // 按复购紧迫度排序（daysUntilRepurchase 从小到大）
        alerts.sort(Comparator.comparingLong(a -> (Long) a.get("daysUntilRepurchase")));

        // 去重（同一客户同一产品只保留最紧急的一条）
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (Map<String, Object> alert : alerts) {
            String key = alert.get("customerId") + "_" + alert.get("productName");
            if (seen.add(key)) {
                deduped.add(alert);
            }
        }

        return ResponseEntity.ok(deduped.stream().limit(limit).collect(Collectors.toList()));
    }

    /**
     * 根据产品类型和购买数量估算使用天数
     * 农资产品的典型使用周期：
     * - 农药：一般7-15天喷一次，一瓶可用2-3次
     * - 化肥：一般30-45天施一次
     * - 种子：一季一次
     */
    private int estimateUsageDays(Product product, OrderItem item) {
        String categoryName = product.getCategoryId() != null ? "" : "";
        // 简化逻辑：基于商品名称关键词判断
        String name = product.getName() != null ? product.getName() : "";
        double quantity = item.getQuantity() != null ? item.getQuantity().doubleValue() : 1;

        if (name.contains("农药") || name.contains("杀虫") || name.contains("杀菌") || name.contains("除草")) {
            return (int) (15 * quantity); // 每瓶约15天用量
        } else if (name.contains("肥") || name.contains("营养") || name.contains("叶面")) {
            return (int) (30 * quantity); // 每袋约30天用量
        } else if (name.contains("种子") || name.contains("种苗")) {
            return 90; // 一季约90天
        } else if (name.contains("膜") || name.contains("地膜")) {
            return 120; // 一季用量
        }
        // 默认30天复购周期
        return 30;
    }
}
