package com.agri_system.application.inventory.service;

import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 库存预警应用服务
 */
@Service
@RequiredArgsConstructor
public class InventoryWarningApplicationService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * 查询低库存预警
     */
    public List<Map<String, Object>> getLowStockWarnings(Long storeId) {
        List<Product> products = productRepository.findAllWithMinStock();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (Product product : products) {
            if (product.getMinStock() == null) continue;

            BigDecimal currentStock = inventoryRepository.sumQuantityByProduct(product.getId(), storeId);

            if (currentStock.compareTo(product.getMinStock()) < 0) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("productId", product.getId());
                warning.put("productName", product.getName());
                warning.put("productCode", product.getCode());
                warning.put("currentStock", currentStock);
                warning.put("minStock", product.getMinStock());
                warning.put("reorderQuantity", product.getReorderQuantity());
                warning.put("gap", product.getMinStock().subtract(currentStock));
                warnings.add(warning);
            }
        }

        // 按缺口从大到小排序
        warnings.sort((a, b) -> ((BigDecimal) b.get("gap")).compareTo((BigDecimal) a.get("gap")));
        return warnings;
    }

    /**
     * 查询积压库存预警
     */
    public List<Map<String, Object>> getOverstockWarnings(Long storeId) {
        List<Product> products = productRepository.findAllWithMaxStock();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (Product product : products) {
            if (product.getMaxStock() == null) continue;

            BigDecimal currentStock = inventoryRepository.sumQuantityByProduct(product.getId(), storeId);

            if (currentStock.compareTo(product.getMaxStock()) > 0) {
                Map<String, Object> warning = new LinkedHashMap<>();
                warning.put("productId", product.getId());
                warning.put("productName", product.getName());
                warning.put("currentStock", currentStock);
                warning.put("maxStock", product.getMaxStock());
                warning.put("excess", currentStock.subtract(product.getMaxStock()));
                warnings.add(warning);
            }
        }

        warnings.sort((a, b) -> ((BigDecimal) b.get("excess")).compareTo((BigDecimal) a.get("excess")));
        return warnings;
    }
}
