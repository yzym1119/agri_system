package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.inventory.dto.vo.ThresholdRecommendation;
import com.agri_system.application.inventory.service.SmartThresholdService;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 库存预警Controller
 */
@RestController
@RequestMapping("/api/admin/inventory/warning")
@Tag(name = "库存预警", description = "低库存、积压库存预警查询")
@RequiredArgsConstructor
public class InventoryWarningController {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SmartThresholdService smartThresholdService;

    @GetMapping("/low-stock")
    @Operation(summary = "查询低库存预警商品")
    @SaCheckPermission("inventory:warning:query")
    public ResponseEntity<List<Map<String, Object>>> lowStock(
            @RequestParam(required = false) Long storeId) {
        List<Product> products = productRepository.findAllWithMinStock();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (Product product : products) {
            if (product.getMinStock() == null) continue;

            // 汇总库存：指定门店则查该门店，不指定则汇总全部门店
            BigDecimal currentStock = inventoryRepository.sumQuantityByProduct(product.getId(), storeId);

            if (currentStock.compareTo(product.getMinStock()) < 0) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("productId", product.getId());
                warning.put("productName", product.getName());
                warning.put("productCode", product.getCode());
                warning.put("mainUnit", product.getMainUnit());
                warning.put("currentStock", currentStock);
                warning.put("minStock", product.getMinStock());
                warning.put("reorderQuantity", product.getReorderQuantity());
                warning.put("gap", product.getMinStock().subtract(currentStock));
                warnings.add(warning);
            }
        }

        // 按缺口从大到小排序
        warnings.sort((a, b) -> ((BigDecimal) b.get("gap")).compareTo((BigDecimal) a.get("gap")));
        return ResponseEntity.ok(warnings);
    }

    @GetMapping("/overstock")
    @Operation(summary = "查询积压库存预警商品")
    @SaCheckPermission("inventory:warning:query")
    public ResponseEntity<List<Map<String, Object>>> overstock(
            @RequestParam(required = false) Long storeId) {
        List<Product> products = productRepository.findAllWithMaxStock();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (Product product : products) {
            if (product.getMaxStock() == null) continue;

            BigDecimal currentStock = inventoryRepository.sumQuantityByProduct(product.getId(), storeId);

            if (currentStock.compareTo(product.getMaxStock()) > 0) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("productId", product.getId());
                warning.put("productName", product.getName());
                warning.put("mainUnit", product.getMainUnit());
                warning.put("currentStock", currentStock);
                warning.put("maxStock", product.getMaxStock());
                warning.put("excess", currentStock.subtract(product.getMaxStock()));
                warnings.add(warning);
            }
        }

        warnings.sort((a, b) -> ((BigDecimal) b.get("excess")).compareTo((BigDecimal) a.get("excess")));
        return ResponseEntity.ok(warnings);
    }

    @GetMapping("/smart-threshold")
    @Operation(summary = "智能库存阈值推荐（基于销售数据自动计算）")
    @SaCheckPermission("inventory:warning:query")
    public ResponseEntity<List<ThresholdRecommendation>> smartThreshold(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "90") Integer days) {
        List<ThresholdRecommendation> recommendations = smartThresholdService.calculateRecommendations(storeId, days);
        return ResponseEntity.ok(recommendations);
    }
}
