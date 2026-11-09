package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.PosStockCheckCommand;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.productpackage.service.PackagePricingService;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.inventory.vo.SaleableStock;

@Service
@RequiredArgsConstructor
public class PosStockPreviewService {
    private final ProductUnitService units;
    private final PackagePricingService packages;
    private final InventoryBatchBalanceRepository balances;
    private final ProductRepository products;
    public record Preview(String storeId,boolean sufficient,List<String> issues) {}
    public Preview preview(PosStockCheckCommand command) {
        if (CurrentUser.staffId() == null) throw new IllegalArgumentException("仅员工可核对销售库存");
        Long store = CurrentUser.storeId();
        if (store == null || store <= 0 || (command.getStoreId() != null && !store.equals(command.getStoreId())))
            throw new IllegalArgumentException("只能核对当前员工门店库存");
        if (command.getItems() == null || command.getItems().isEmpty() || command.getItems().size() > 500)
            throw new IllegalArgumentException("销售明细须为1到500行");
        var demand = new TreeMap<Long, BigDecimal>();
        var knownProducts = new HashMap<Long, Product>();
        int materialCount = 0;
        for (var line : command.getItems()) {
            if (line == null || line.getQuantity() == null || line.getQuantity().signum() <= 0
                    || line.getQuantity().stripTrailingZeros().scale() > 4) throw new IllegalArgumentException("数量必须为正且最多4位有效小数");
            int type = line.getItemType() == null ? 1 : line.getItemType();
            if (type == 2) {
                if (line.getProductId() != null || line.getPackageId() == null || line.getPackageQuoteId() == null)
                    throw new IllegalArgumentException("套餐须整套提交有效报价，不接受前端拆分");
                var quote = packages.quote(line.getPackageId(), command.getCustomerId(), store, line.getQuantity(), null);
                if (!line.getPackageQuoteId().equals(quote.quoteId())) throw new IllegalArgumentException("套餐价格或组成已变化，请重新报价");
                if (quote.items() == null || quote.items().isEmpty()) throw new IllegalArgumentException("套餐没有有效物料");
                materialCount += quote.items().size();
                if (materialCount > 1000) throw new IllegalArgumentException("整单物料行超过核对上限");
                for (var material : quote.items()) addDemand(demand, knownProducts, material.productId(), material.unit(), material.quantity());
            } else if (type == 1 && line.getPackageId() == null && line.getProductId() != null) {
                if (++materialCount > 1000) throw new IllegalArgumentException("整单物料行超过核对上限");
                addDemand(demand, knownProducts, line.getProductId(), line.getUnit(), line.getQuantity());
            } else throw new IllegalArgumentException("明细类型或物料身份无效");
        }
        var stock = balances.findSaleableStock(store, List.copyOf(demand.keySet())).stream().collect(Collectors.groupingBy(SaleableStock::productId));
        var issues = new ArrayList<String>();
        for (var entry : demand.entrySet()) {
            Long productId = entry.getKey();
            var baseUnits = units.getUnits(productId).stream().filter(u -> u.getId() != null && Integer.valueOf(1).equals(u.getStatus())
                    && Integer.valueOf(1).equals(u.getIsBaseUnit()) && u.getBaseQuantityPerUnit() != null && u.getBaseQuantityPerUnit().compareTo(BigDecimal.ONE)==0
                    && u.getUnitName() != null && !u.getUnitName().isBlank()).toList();
            var rows = stock.getOrDefault(productId, List.of());
            String name = knownProducts.get(productId).getName();
            if (baseUnits.size() != 1 || rows.stream().anyMatch(r -> !baseUnits.get(0).getId().equals(r.baseUnitId())
                    || r.availableBaseQuantity() == null || r.availableBaseQuantity().signum() < 0)) {
                issues.add(name + "：库存单位资料待核对");continue;
            }
            BigDecimal available = rows.stream().map(SaleableStock::availableBaseQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (entry.getValue().compareTo(available) > 0) issues.add(name + "：整单需要 " + entry.getValue().stripTrailingZeros().toPlainString()
                    + "，本店可售 " + available.stripTrailingZeros().toPlainString() + " " + baseUnits.get(0).getUnitName());
        }
        return new Preview(store.toString(), issues.isEmpty(), List.copyOf(issues));
    }
    private void addDemand(java.util.Map<Long, BigDecimal> demand, java.util.Map<Long, Product> cache, Long id, String unit, BigDecimal quantity) {
        if (id == null || id <= 0 || quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("物料身份或数量无效");
        Product product = cache.computeIfAbsent(id, key -> products.findById(key).orElseThrow(() -> new IllegalArgumentException("商品不存在")));
        if (!Integer.valueOf(1).equals(product.getStatus())) throw new IllegalArgumentException("商品已停用");
        String saleUnit = unit == null || unit.isBlank() ? product.getMainUnit() : unit;
        units.requireEnabledUnit(id, saleUnit, ProductUnitService.UnitOperation.SALE);
        demand.merge(id, units.toBaseQuantity(id, saleUnit, quantity).baseQuantity(), BigDecimal::add);
    }
}
