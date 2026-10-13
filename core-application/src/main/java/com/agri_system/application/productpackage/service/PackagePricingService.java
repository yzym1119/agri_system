package com.agri_system.application.productpackage.service;

import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.productpackage.dto.vo.PackagePriceQuote;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 套餐是独立的元/套价格；物料零售价只决定分摊权重，不决定套餐成交总额。 */
@Service
@RequiredArgsConstructor
public class PackagePricingService {
    private final ProductPackageRepository packages;
    private final ProductPackageItemRepository items;
    private final ProductRepository products;
    private final ProductUnitService units;
    private final ProductPricingService productPricing;
    private record Material(Product product, ProductUnit unit, BigDecimal quantityPerSet, BigDecimal weight) {}

    @Transactional(readOnly = true)
    public PackagePriceQuote quote(Long id, Long customerId, Long storeId, BigDecimal quantity, BigDecimal dealPrice) {
        ProductPackage pack = packages.findById(id).orElseThrow(() -> new IllegalArgumentException("套餐不存在"));
        return quote(pack, customerId, storeId, quantity, dealPrice);
    }

    /** 开单事务锁套餐主档；编辑套餐也写同一行，组成与定价不能在开单过程中被替换。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public PackagePriceQuote quoteForSale(Long id, Long customerId, Long storeId, BigDecimal quantity, BigDecimal dealPrice) {
        ProductPackage pack = packages.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("套餐不存在"));
        return quote(pack, customerId, storeId, quantity, dealPrice);
    }

    private PackagePriceQuote quote(ProductPackage pack, Long customerId, Long storeId, BigDecimal quantity, BigDecimal dealPrice) {
        if (!pack.isOnline()) throw new IllegalArgumentException("套餐未上架，不能销售");
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("999999")) > 0) {
            throw new IllegalArgumentException("套餐套数必须为1到999999");
        }
        if (quantity.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("套餐套数必须是正整数");
        validatePrices(pack.getSuggestedRetailPrice(), pack.getVipPrice(), pack.getMinPrice());
        boolean member = Integer.valueOf(2).equals(productPricing.resolveCustomerContext(customerId).customerType());
        BigDecimal suggested = member && positive(pack.getVipPrice()) ? pack.getVipPrice() : pack.getSuggestedRetailPrice();
        validateMoney(suggested, "套餐零售价", false);
        BigDecimal deal = dealPrice == null ? suggested : dealPrice;
        validateMoney(deal, "套餐成交价", false);
        BigDecimal total = deal.multiply(quantity).setScale(2, RoundingMode.UNNECESSARY);
        validateMoney(total, "套餐销售总额", false);
        BigDecimal floor = positive(pack.getMinPrice()) ? pack.getMinPrice().setScale(2) : null;
        List<ProductPackageItem> configured = items.findItemsByPackageId(pack.getId());
        if (configured == null || configured.isEmpty()) throw new IllegalArgumentException("套餐缺少商品明细");
        List<ProductPackageItem> sorted = configured.stream().sorted(Comparator
                .comparing((ProductPackageItem i) -> i.getSortOrder() == null ? 0 : i.getSortOrder())
                .thenComparing(i -> i.getId() == null ? 0L : i.getId())).toList();
        List<Material> materials = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        append(signature, pack.getId(), pack.getName(), suggested, floor, member);
        for (ProductPackageItem component : sorted) {
            Product product = products.findById(component.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("套餐商品不存在"));
            if (!Integer.valueOf(1).equals(product.getStatus())) throw new IllegalArgumentException("套餐商品已停用: " + product.getName());
            BigDecimal perSet = component.getQuantity();
            if (perSet == null || perSet.signum() <= 0 || perSet.stripTrailingZeros().scale() > 4) {
                throw new IllegalArgumentException("套餐商品数量必须大于0且最多四位小数");
            }
            BigDecimal expanded = perSet.multiply(quantity);
            if (expanded.compareTo(new BigDecimal("999999.9999")) > 0) throw new IllegalArgumentException("套餐商品数量超出存储范围");
            ProductUnit unit = units.requireEnabledUnit(product.getId(), component.getUnit(), ProductUnitService.UnitOperation.SALE);
            // 固定用普通零售价作为分摊权重，套餐会员价不会再叠加子商品会员折扣。
            BigDecimal retail = productPricing.quote(new PriceContext(product.getId(), storeId, "POS", 1, null,
                    unit.getUnitName(), LocalDate.now())).suggestedUnitPrice();
            BigDecimal weight = retail.multiply(perSet);
            materials.add(new Material(product, unit, perSet, weight));
            append(signature, product.getId(), product.getName(), unit.getId(), unit.getUnitName(),
                    unit.getBaseQuantityPerUnit(), perSet, retail, product.getAllowSpecialFund());
        }
        List<BigDecimal> weights = materials.stream().map(Material::weight).toList();
        List<BigDecimal> amounts = allocate(total, weights);
        List<BigDecimal> standardAmounts = allocate(suggested.multiply(quantity).setScale(2), weights);
        List<PackagePriceQuote.Item> result = new ArrayList<>();
        BigDecimal specialAmount = BigDecimal.ZERO;
        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);
            BigDecimal qty = material.quantityPerSet().multiply(quantity);
            BigDecimal amount = amounts.get(i);
            BigDecimal unitPrice = amount.divide(qty, 12, RoundingMode.HALF_UP);
            BigDecimal standardPrice = standardAmounts.get(i).divide(qty, 12, RoundingMode.HALF_UP);
            Integer allowSpecial = Integer.valueOf(1).equals(material.product().getAllowSpecialFund()) ? 1 : 0;
            if (allowSpecial == 1) specialAmount = specialAmount.add(amount);
            result.add(new PackagePriceQuote.Item(material.product().getId(), material.product().getName(), qty,
                    material.unit().getUnitName(), material.unit().getId(), unitPrice, amount, allowSpecial, standardPrice));
        }
        return new PackagePriceQuote(hash(signature.toString()), pack.getId(), pack.getName(), quantity,
                suggested.setScale(2), floor, floor != null && deal.compareTo(floor) < 0,
                deal.setScale(2), total, specialAmount.setScale(2), List.copyOf(result));
    }

    /** 最大余数法分到整数分，同余数按物料配置顺序分配；允许0元物料但不能丢失实际数量。 */
    private List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        long target = total.movePointRight(2).longValueExact();
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() <= 0) throw new IllegalArgumentException("套餐商品缺少有效零售价，无法确定分摊权重");
        long[] cents = new long[weights.size()];
        BigDecimal[] remainders = new BigDecimal[weights.size()];
        long allocated = 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal numerator = BigDecimal.valueOf(target).multiply(weights.get(i));
            cents[i] = numerator.divide(sum, 0, RoundingMode.DOWN).longValueExact();
            remainders[i] = numerator.remainder(sum); allocated += cents[i]; order.add(i);
        }
        order.sort((a,b) -> { int comparison = remainders[b].compareTo(remainders[a]); return comparison != 0 ? comparison : Integer.compare(a,b); });
        for (int i = 0; i < target - allocated; i++) cents[order.get(i)]++;
        List<BigDecimal> result = new ArrayList<>();
        for (long amount : cents) result.add(BigDecimal.valueOf(amount, 2));
        return result;
    }

    public static void validatePrices(BigDecimal retail, BigDecimal member, BigDecimal floor) {
        validateMoney(retail, "套餐零售价", true);
        if (member != null) validateMoney(member, "套餐会员价", true);
        if (floor != null) validateMoney(floor, "套餐最低价", true);
        if (positive(member) && member.compareTo(retail) > 0) throw new IllegalArgumentException("套餐会员价不能高于零售价");
        if (positive(floor) && (floor.compareTo(retail) > 0 || (positive(member) && floor.compareTo(member) > 0))) {
            throw new IllegalArgumentException("套餐最低价不能高于零售价或会员价");
        }
    }
    private static void validateMoney(BigDecimal value, String label, boolean allowZero) {
        if (value == null || value.signum() < 0 || (!allowZero && value.signum() == 0)
                || value.stripTrailingZeros().scale() > 2 || value.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException(label + "必须为有效的两位小数金额");
        }
    }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private void append(StringBuilder builder, Object... values) {
        for (Object value : values) {
            String normalized = value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : Objects.toString(value, "");
            builder.append(normalized.length()).append(':').append(normalized).append(';');
        }
    }
    private String hash(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
