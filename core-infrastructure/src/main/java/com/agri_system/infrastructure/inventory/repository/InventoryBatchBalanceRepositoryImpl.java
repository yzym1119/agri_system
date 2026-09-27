package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryBatchBalanceRepositoryImpl implements InventoryBatchBalanceRepository {
    private final InventoryBatchBalanceMapper mapper;

    @Override
    public void save(InventoryBatchBalance balance) {
        if (balance.getId() == null) mapper.insert(balance); else mapper.update(balance);
    }

    @Override
    public Optional<InventoryBatchBalance> findByBatchAndLocationForUpdate(Long batchId, Long locationId) {
        return Optional.ofNullable(mapper.findByBatchAndLocationForUpdate(batchId, locationId));
    }

    @Override
    public BigDecimal sumAvailableBaseQuantity(Long locationId, Long productId) {
        return mapper.sumAvailableBaseQuantity(locationId, productId);
    }

    @Override
    public BigDecimal sumBaseQuantityByStoreAndProduct(Long storeId, Long productId) {
        return mapper.sumBaseQuantityByStoreAndProduct(storeId, productId);
    }

    @Override
    public List<InventoryBatchBalance> findByLocationAndProduct(Long locationId, Long productId) {
        return mapper.findByLocationAndProduct(locationId, productId);
    }

    @Override
    public Map<Long, BigDecimal> sumAvailableBaseQuantityGroupedByProduct(Long storeId) {
        return foldGrouped(mapper.sumAvailableBaseQuantityGroupedByProduct(storeId), "productId", "product_id");
    }

    @Override
    public Map<Long, BigDecimal> sumAvailableBaseQuantityGroupedByBatch(Long storeId) {
        return foldGrouped(mapper.sumAvailableBaseQuantityGroupedByBatch(storeId), "batchId", "batch_id");
    }

    /** MyBatis 无法直接映射为 Map<Long,BigDecimal>，在仓储层折叠分组行（键名大小写容错）。 */
    private Map<Long, BigDecimal> foldGrouped(List<Map<String, Object>> rows, String keyAlias, String keyColumn) {
        Map<Long, BigDecimal> folded = new LinkedHashMap<>();
        if (rows == null) {
            return folded;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long key = toLong(pick(row, keyAlias, keyColumn));
            if (key == null) {
                continue;
            }
            BigDecimal value = toBigDecimal(pick(row, "availableBaseQuantity", "available_base_quantity"));
            folded.put(key, value == null ? BigDecimal.ZERO : value);
        }
        return folded;
    }

    private static Object pick(Map<String, Object> row, String... candidates) {
        for (String candidate : candidates) {
            if (row.containsKey(candidate)) {
                return row.get(candidate);
            }
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(candidate)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        try { return new BigDecimal(obj.toString()); } catch (NumberFormatException e) { return null; }
    }
}
