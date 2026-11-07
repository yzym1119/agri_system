package com.agri_system.infrastructure.order.query;

import com.agri_system.domain.order.query.PickupEvidenceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 提货证据只读查询的唯一实现；SQL 与参数顺序从 PickupFulfillmentService 原样迁入。 */
@Repository
@RequiredArgsConstructor
public class PickupEvidenceQueryImpl implements PickupEvidenceQuery {
    private final JdbcTemplate jdbc;

    @Override
    public String findBaseUnitName(Long baseUnitId, Long productId) {
        var names = jdbc.queryForList("SELECT unit_name FROM product_unit WHERE id=? AND product_id=? AND is_base_unit=1",
                String.class, baseUnitId, productId);
        return names.size() == 1 ? names.get(0) : null;
    }

    @Override
    public List<Map<String, Object>> findMovements(List<Long> sourceMovementIds, List<Long> allocationIds) {
        // 分配为空时原实现也不会生成 IN () 的非法 SQL；此处显式短路，避免端口被误用。
        if (allocationIds.isEmpty()) return List.of();
        String saleSlots = sourceMovementIds.isEmpty() ? "NULL" : String.join(",", Collections.nCopies(sourceMovementIds.size(), "?"));
        String returnSlots = String.join(",", Collections.nCopies(allocationIds.size(), "?"));
        List<Object> args = new ArrayList<>(sourceMovementIds);
        args.addAll(allocationIds);
        return jdbc.queryForList("""
                SELECT m.*, CASE WHEN m.reference_type='ORDER' AND EXISTS
                  (SELECT 1 FROM after_sales af WHERE af.order_id=m.reference_id AND af.status IN (10,40))
                  THEN 1 ELSE 0 END AS processing_after_sales
                FROM inventory_movement m WHERE m.id IN (%s)
                  OR (m.movement_type='RETURN' AND m.reference_type='SALE_ALLOCATION'
                      AND m.reference_id IN (%s) AND m.base_quantity > 0)
                """.formatted(saleSlots, returnSlots), args.toArray());
    }
}
