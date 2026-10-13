package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.repository.InventoryMovementRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryMovementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryMovementRepositoryImpl implements InventoryMovementRepository {
    private final InventoryMovementMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public void append(InventoryMovement movement) {
        if (movement.getId() != null) throw new IllegalArgumentException("库存移动事实仅允许追加");
        mapper.insert(movement);
        // 库存流水列表是移动账的展示投影；双边店内移库不改变门店库存。
        if (movement.getFromLocationId() != null && movement.getToLocationId() != null) return;
        java.math.BigDecimal factor = jdbc.queryForObject(
                "SELECT u.base_quantity_per_unit FROM product p JOIN product_unit u ON u.product_id=p.id AND u.unit_name=p.main_unit WHERE p.id=?",
                java.math.BigDecimal.class, movement.getProductId());
        java.math.BigDecimal signedBase = movement.getFromLocationId() == null ? movement.getBaseQuantity() : movement.getBaseQuantity().negate();
        java.math.BigDecimal signed = signedBase.divide(factor, 8, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal afterBase = jdbc.queryForList(
                "SELECT base_quantity FROM inventory_batch_balance WHERE store_id=? AND product_id=? ORDER BY id FOR UPDATE",
                java.math.BigDecimal.class, movement.getStoreId(), movement.getProductId()).stream().reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal batchAfterBase = jdbc.queryForList(
                "SELECT base_quantity FROM inventory_batch_balance WHERE batch_id=? ORDER BY id FOR UPDATE",
                java.math.BigDecimal.class, movement.getBatchId()).stream().reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal after = afterBase.divide(factor, 8, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal batchAfter = batchAfterBase.divide(factor, 8, java.math.RoundingMode.HALF_UP);
        int type = switch (movement.getMovementType()) {
            case "RECEIPT" -> 1;
            case "SALE" -> 2;
            case "RETURN" -> 4;
            case "TRANSFER" -> signed.signum() < 0 ? 5 : 6;
            default -> signed.signum() < 0 ? 8 : 7;
        };
        if ("SALE".equals(movement.getMovementType()) && "ORDER".equals(movement.getReferenceType())) {
            Integer orderType = jdbc.queryForObject("SELECT order_type FROM `order` WHERE id=?", Integer.class, movement.getReferenceId());
            if (orderType != null && orderType != 1) type = 3;
        }
        jdbc.update("""
                INSERT INTO inventory_transaction(store_id,product_id,receipt_batch_id,transaction_type,
                    before_quantity,after_quantity,batch_before_qty,batch_after_qty,quantity,cost_price,unit,
                    original_unit,original_quantity,reference_type,reference_id,remark,created_at,created_by)
                SELECT ?,?,?,?,?,?,?,?,?,?,p.main_unit,?,?,?,?,?,?,? FROM product p WHERE p.id=?
                """, movement.getStoreId(), movement.getProductId(), movement.getBatchId(), type,
                afterBase.subtract(signedBase).divide(factor,8,java.math.RoundingMode.HALF_UP), after,
                batchAfterBase.subtract(signedBase).divide(factor,8,java.math.RoundingMode.HALF_UP), batchAfter, signed,
                movement.getUnitCost().multiply(factor), movement.getOriginalUnitName(), movement.getOriginalQuantity(),
                movement.getReferenceType(), movement.getReferenceId(), movement.getRemark(), movement.getOccurredAt(), movement.getOperatorId(), movement.getProductId());
        // 商品成本只做展示缓存，实际销售成本来自订单批次归属。
        jdbc.update("""
                UPDATE product SET avg_cost_price=(SELECT SUM(b.base_quantity*r.base_unit_cost)/NULLIF(SUM(b.base_quantity),0)*?
                FROM inventory_batch_balance b JOIN inventory_receipt_batch r ON r.id=b.batch_id WHERE b.product_id=?)
                WHERE id=? AND EXISTS(SELECT 1 FROM inventory_batch_balance b WHERE b.product_id=? AND b.base_quantity>0)
                """, factor, movement.getProductId(), movement.getProductId(), movement.getProductId());
    }
}
