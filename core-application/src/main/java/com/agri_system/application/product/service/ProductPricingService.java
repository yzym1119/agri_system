package com.agri_system.application.product.service;

import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.exception.ProductErrorCode;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/** 唯一商品报价：基准单位零售价、可选会员价，按持久化销售单位系数换算。 */
@Service
@RequiredArgsConstructor
public class ProductPricingService {
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductUnitService productUnitService;

    public PriceQuote quote(PriceContext context) {
        if (context == null || context.productId() == null) {
            throw new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在");
        }
        Product product = productRepository.findById(context.productId())
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        String resolvedUnit = StringUtils.hasText(context.saleUnit()) ? context.saleUnit().trim() : product.getMainUnit();
        ProductUnit unit = productUnitService.requireEnabledUnit(product.getId(), resolvedUnit,
                ProductUnitService.UnitOperation.SALE);
        BigDecimal retail = positive(product.getSuggestedRetailPrice());
        if (retail == null) {
            throw new IllegalArgumentException("商品未配置有效零售价: " + product.getName());
        }
        BigDecimal member = Integer.valueOf(2).equals(context.customerType()) ? positive(product.getVipPrice()) : null;
        BigDecimal coefficient = unit.getBaseQuantityPerUnit();
        if (coefficient == null || coefficient.signum() <= 0) {
            throw new IllegalArgumentException("销售单位换算系数无效: " + resolvedUnit);
        }
        BigDecimal price = convert(member != null ? member : retail, coefficient);
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("销售单位价格不足一分，请调整价格或销售单位");
        }
        BigDecimal floor = positive(product.getMinPrice());
        BigDecimal convertedFloor = floor == null ? null : convert(floor, coefficient);
        return new PriceQuote(UUID.randomUUID().toString(), null, product.getId(), context.storeId(),
                context.channel(), context.customerType(), null, unit.getUnitName(), unit.getId(),
                context.businessDate() != null ? context.businessDate() : LocalDate.now(), price,
                member != null ? "MEMBER" : "RETAIL", convertedFloor,
                convertedFloor != null && price.compareTo(convertedFloor) < 0, false);
    }

    public BigDecimal resolveOrderItemUnitPrice(Product product, Long storeId, Long customerId, String currentUnit) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("商品信息不完整，无法解析价格");
        }
        CustomerPriceContext customer = resolveCustomerContext(customerId);
        return quote(new PriceContext(product.getId(), storeId, null, customer.customerType(), null,
                currentUnit, LocalDate.now())).suggestedUnitPrice();
    }

    public CustomerPriceContext resolveCustomerContext(Long customerId) {
        if (customerId == null) return new CustomerPriceContext(1, null);
        return customerRepository.findById(customerId).map(this::buildCustomerContext)
                .orElse(new CustomerPriceContext(1, null));
    }

    private CustomerPriceContext buildCustomerContext(Customer customer) {
        return new CustomerPriceContext(customer.isVip() && !customer.isVipExpired() ? 2 : 1, null);
    }

    private BigDecimal positive(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }

    private BigDecimal convert(BigDecimal price, BigDecimal coefficient) {
        return price.multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
    }

    /** vipLevel 保留 DTO 兼容，定价不再区分会员等级。 */
    public record CustomerPriceContext(Integer customerType, Integer vipLevel) {}
}
