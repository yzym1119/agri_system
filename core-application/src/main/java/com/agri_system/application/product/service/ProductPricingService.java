package com.agri_system.application.product.service;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductPrice;
import com.agri_system.domain.product.repository.ProductPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 商品定价服务
 * 对齐管理端 POS 的价格匹配和降级策略，统一订单侧单价解析口径。
 */
@Service
@RequiredArgsConstructor
public class ProductPricingService {

    private final ProductPriceRepository productPriceRepository;
    private final CustomerRepository customerRepository;

    /**
     * 解析订单行单价。
     *
     * @param product 商品
     * @param storeId 门店ID
     * @param customerId 客户ID
     * @param currentUnit 当前下单单位
     * @return 订单单价
     */
    public BigDecimal resolveOrderItemUnitPrice(Product product, Long storeId, Long customerId, String currentUnit) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("商品信息不完整，无法解析价格");
        }

        CustomerPriceContext customerContext = resolveCustomerContext(customerId);
        List<ProductPrice> activeRules = productPriceRepository.findByProductId(product.getId()).stream()
                .filter(ProductPrice::isActive)
                .toList();

        BigDecimal basePrice = matchRulePrice(activeRules, storeId,
                customerContext.customerType(), customerContext.vipLevel());
        if (basePrice == null) {
            basePrice = resolveProductBasePrice(product, customerContext.customerType());
        }
        if (basePrice == null) {
            throw new IllegalArgumentException("商品未配置可用销售价格: " + product.getName());
        }

        return applyProductUnitPrice(basePrice, product, currentUnit);
    }

    private CustomerPriceContext resolveCustomerContext(Long customerId) {
        if (customerId == null) {
            return new CustomerPriceContext(null, null);
        }

        return customerRepository.findById(customerId)
                .map(this::buildCustomerContext)
                .orElse(new CustomerPriceContext(null, null));
    }

    private CustomerPriceContext buildCustomerContext(Customer customer) {
        if (customer == null) {
            return new CustomerPriceContext(null, null);
        }

        if (customer.isVip() && !customer.isVipExpired()) {
            return new CustomerPriceContext(2, customer.getVipLevel());
        }

        Integer customerType = customer.getType();
        if (Integer.valueOf(2).equals(customerType)) {
            customerType = 1;
        }
        return new CustomerPriceContext(customerType, null);
    }

    private BigDecimal matchRulePrice(List<ProductPrice> rules, Long storeId, Integer customerType, Integer vipLevel) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        List<Integer> targetPriceTypes = Integer.valueOf(2).equals(customerType)
                ? List.of(2, 1)
                : List.of(1);

        List<PriceMatchCandidate> candidates = new ArrayList<>();
        addCandidate(candidates, storeId, customerType, vipLevel);
        addCandidate(candidates, storeId, customerType, null);
        addCandidate(candidates, storeId, null, null);
        addCandidate(candidates, null, customerType, vipLevel);
        addCandidate(candidates, null, customerType, null);
        addCandidate(candidates, null, null, null);

        for (PriceMatchCandidate candidate : candidates) {
            for (Integer priceType : targetPriceTypes) {
                for (ProductPrice rule : rules) {
                    if (rule == null || !Objects.equals(rule.getPriceType(), priceType)) {
                        continue;
                    }
                    if (!Objects.equals(rule.getStoreId(), candidate.storeId())
                            || !Objects.equals(rule.getCustomerType(), candidate.customerType())
                            || !Objects.equals(rule.getVipLevel(), candidate.vipLevel())) {
                        continue;
                    }
                    BigDecimal matchedPrice = toPositiveAmount(rule.getPrice());
                    if (matchedPrice != null) {
                        return matchedPrice;
                    }
                }
            }
        }

        return null;
    }

    private void addCandidate(List<PriceMatchCandidate> candidates, Long storeId, Integer customerType, Integer vipLevel) {
        PriceMatchCandidate candidate = new PriceMatchCandidate(storeId, customerType, vipLevel);
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private BigDecimal resolveProductBasePrice(Product product, Integer customerType) {
        if (Integer.valueOf(2).equals(customerType)) {
            BigDecimal vipPrice = toPositiveAmount(product.getVipPrice());
            if (vipPrice != null) {
                return vipPrice;
            }
        }

        BigDecimal suggestedRetailPrice = toPositiveAmount(product.getSuggestedRetailPrice());
        if (suggestedRetailPrice != null) {
            return suggestedRetailPrice;
        }

        BigDecimal minPrice = toPositiveAmount(product.getMinPrice());
        if (minPrice != null) {
            return minPrice;
        }

        return toPositiveAmount(product.getOnlinePrice());
    }

    private BigDecimal applyProductUnitPrice(BigDecimal basePrice, Product product, String currentUnit) {
        if (basePrice == null || product == null) {
            return basePrice;
        }

        BigDecimal ratio = toPositiveAmount(product.getMainToAuxRatio());
        String resolvedUnit = StringUtils.hasText(currentUnit) ? currentUnit : product.getMainUnit();
        if (StringUtils.hasText(product.getAuxUnit()) && ratio != null) {
            if (Objects.equals(resolvedUnit, product.getAuxUnit())) {
                return basePrice;
            }
            return basePrice.multiply(ratio);
        }

        return basePrice;
    }

    private BigDecimal toPositiveAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value;
    }

    private record CustomerPriceContext(Integer customerType, Integer vipLevel) {
    }

    private record PriceMatchCandidate(Long storeId, Integer customerType, Integer vipLevel) {
    }
}
