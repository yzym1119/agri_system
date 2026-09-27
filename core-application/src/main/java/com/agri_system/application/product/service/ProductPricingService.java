package com.agri_system.application.product.service;

import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.exception.ProductErrorCode;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductPrice;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductPriceRepository;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 商品定价服务
 * 对齐管理端 POS 的价格匹配和降级策略，统一订单侧单价解析口径。
 *
 * <p>报价引擎统一按“销售单位 + 渠道 + 客户分层”匹配价格规则：
 * 显式按销售单位配置的规则优先于旧链路（sale_unit 为空）规则；旧链路规则仍按主辅单位换算。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductPricingService {

    private static final String UNIT_MODEL_LEGACY = "LEGACY";
    private static final int PRICE_TYPE_SUGGESTED_RETAIL = 1;
    private static final int PRICE_TYPE_VIP = 2;
    private static final int PRICE_TYPE_MIN_LIMIT = 3;

    private final ProductPriceRepository productPriceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductUnitService productUnitService;

    /**
     * 按销售单位报价（唯一价格引擎入口）。
     *
     * @param context 报价上下文
     * @return 可审计报价结果
     */
    public PriceQuote quote(PriceContext context) {
        if (context == null || context.productId() == null) {
            throw new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在");
        }

        Product product = productRepository.findById(context.productId())
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));

        String resolvedUnit = StringUtils.hasText(context.saleUnit()) ? context.saleUnit() : product.getMainUnit();
        Long saleUnitId = null;
        if (!UNIT_MODEL_LEGACY.equals(productUnitService.getUnitModelStatus(product.getId()))) {
            ProductUnit unit = productUnitService.requireEnabledUnit(product.getId(), resolvedUnit,
                    ProductUnitService.UnitOperation.SALE);
            saleUnitId = unit.getId();
        }

        LocalDate businessDate = context.businessDate() != null ? context.businessDate() : LocalDate.now();
        List<ProductPrice> activeRules = productPriceRepository.findByProductId(product.getId()).stream()
                .filter(rule -> rule != null && rule.isActiveOn(businessDate))
                .filter(rule -> rule.getChannel() == null || rule.getChannel().equals(context.channel()))
                .toList();

        List<Integer> targetPriceTypes = Integer.valueOf(2).equals(context.customerType())
                ? List.of(PRICE_TYPE_VIP, PRICE_TYPE_SUGGESTED_RETAIL)
                : List.of(PRICE_TYPE_SUGGESTED_RETAIL);

        PriceMatch match = matchRule(activeRules, context, resolvedUnit, targetPriceTypes);
        BigDecimal suggestedPrice;
        String ruleVersion = null;
        boolean legacyDerived = false;
        if (match != null) {
            ruleVersion = match.rule().getRuleVersion();
            BigDecimal rulePrice = match.rule().getPrice();
            suggestedPrice = match.explicitUnit()
                    ? rulePrice
                    : applyProductUnitPrice(rulePrice, product, resolvedUnit);
        } else {
            BigDecimal basePrice = resolveProductBasePrice(product, context.customerType());
            suggestedPrice = applyProductUnitPrice(basePrice, product, resolvedUnit);
            if (suggestedPrice == null) {
                throw new IllegalArgumentException("商品未配置可用销售价格: " + product.getName());
            }
            legacyDerived = true;
        }

        BigDecimal referenceFloor = resolveReferenceFloor(activeRules, context, resolvedUnit, product);
        boolean belowReference = referenceFloor != null && suggestedPrice.compareTo(referenceFloor) < 0;

        return new PriceQuote(UUID.randomUUID().toString(), null, product.getId(), context.storeId(),
                context.channel(), context.customerType(), context.vipLevel(), resolvedUnit, saleUnitId,
                businessDate, suggestedPrice, ruleVersion, referenceFloor, belowReference, legacyDerived);
    }

    /**
     * 解析订单行单价（旧链路入口，委托统一报价引擎）。
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
        PriceQuote quote = quote(new PriceContext(product.getId(), storeId, null,
                customerContext.customerType(), customerContext.vipLevel(), currentUnit, LocalDate.now()));
        return quote.suggestedUnitPrice();
    }

    /**
     * 解析客户分层上下文（VIP 未过期按 VIP 档，客户类型 2 归一为 1）。
     * 订单侧各入口共用，保证同一客户在唯一引擎下的分层口径一致。
     */
    public CustomerPriceContext resolveCustomerContext(Long customerId) {
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

    /**
     * 在同一候选顺序下依次尝试：显式销售单位规则 → 旧链路规则（sale_unit 为空），
     * 分区内先精确渠道后通用渠道，再按既有 6 级 (storeId, customerType, vipLevel) 顺序与价格类型偏好匹配。
     */
    private PriceMatch matchRule(List<ProductPrice> rules, PriceContext context, String resolvedUnit,
                                 List<Integer> targetPriceTypes) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (boolean explicitUnit : List.of(true, false)) {
            for (boolean exactChannel : List.of(true, false)) {
                for (PriceMatchCandidate candidate : buildCandidates(context.storeId(), context.customerType(),
                        context.vipLevel())) {
                    for (Integer priceType : targetPriceTypes) {
                        for (ProductPrice rule : rules) {
                            if (rule == null || !Objects.equals(rule.getPriceType(), priceType)) {
                                continue;
                            }
                            if (!unitMatches(rule, resolvedUnit, explicitUnit)) {
                                continue;
                            }
                            if (exactChannel != hasExactChannel(rule, context.channel())) {
                                continue;
                            }
                            if (!Objects.equals(rule.getStoreId(), candidate.storeId())
                                    || !Objects.equals(rule.getCustomerType(), candidate.customerType())
                                    || !Objects.equals(rule.getVipLevel(), candidate.vipLevel())) {
                                continue;
                            }
                            BigDecimal matchedPrice = toPositiveAmount(rule.getPrice());
                            if (matchedPrice != null) {
                                return new PriceMatch(rule, explicitUnit);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean unitMatches(ProductPrice rule, String resolvedUnit, boolean explicitUnit) {
        if (explicitUnit) {
            return rule.getSaleUnit() != null && rule.getSaleUnit().equals(resolvedUnit);
        }
        return rule.getSaleUnit() == null;
    }

    private boolean hasExactChannel(ProductPrice rule, String channel) {
        return rule.getChannel() != null && rule.getChannel().equals(channel);
    }

    private List<PriceMatchCandidate> buildCandidates(Long storeId, Integer customerType, Integer vipLevel) {
        List<PriceMatchCandidate> candidates = new ArrayList<>();
        addCandidate(candidates, storeId, customerType, vipLevel);
        addCandidate(candidates, storeId, customerType, null);
        addCandidate(candidates, storeId, null, null);
        addCandidate(candidates, null, customerType, vipLevel);
        addCandidate(candidates, null, customerType, null);
        addCandidate(candidates, null, null, null);
        return candidates;
    }

    private void addCandidate(List<PriceMatchCandidate> candidates, Long storeId, Integer customerType, Integer vipLevel) {
        PriceMatchCandidate candidate = new PriceMatchCandidate(storeId, customerType, vipLevel);
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    /**
     * 参考底价：同范围命中最低限价（price_type=3）规则时取其价格，否则回退商品最低价。
     */
    private BigDecimal resolveReferenceFloor(List<ProductPrice> rules, PriceContext context, String resolvedUnit,
                                             Product product) {
        PriceMatch floorMatch = matchRule(rules, context, resolvedUnit, List.of(PRICE_TYPE_MIN_LIMIT));
        if (floorMatch != null) {
            BigDecimal floorPrice = floorMatch.rule().getPrice();
            return floorMatch.explicitUnit()
                    ? floorPrice
                    : applyProductUnitPrice(floorPrice, product, resolvedUnit);
        }
        return toPositiveAmount(product.getMinPrice());
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

    /**
     * 客户价格分层上下文：customerType 为空表示未识别客户；
     * vipLevel 仅在 customerType=2（VIP）时携带。
     */
    public record CustomerPriceContext(Integer customerType, Integer vipLevel) {
    }

    private record PriceMatchCandidate(Long storeId, Integer customerType, Integer vipLevel) {
    }

    private record PriceMatch(ProductPrice rule, boolean explicitUnit) {
    }
}
