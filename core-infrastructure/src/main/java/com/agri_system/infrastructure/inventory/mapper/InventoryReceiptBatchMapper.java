package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface InventoryReceiptBatchMapper extends BaseMapper<InventoryReceiptBatch> {
    @Select("SELECT b.* FROM inventory_receipt_batch b "
            + "JOIN inventory_receipt_line l ON l.id = b.receipt_line_id "
            + "WHERE l.receipt_id = #{receiptId} ORDER BY l.line_no, b.id")
    List<InventoryReceiptBatch> findByReceiptId(Long receiptId);

    @Select("SELECT COALESCE(SUM(b.base_quantity), 0) FROM inventory_receipt_batch b "
            + "JOIN inventory_receipt_line l ON l.id = b.receipt_line_id "
            + "WHERE l.purchase_order_item_id = #{purchaseOrderItemId}")
    BigDecimal sumBaseQuantityByPurchaseOrderItemId(Long purchaseOrderItemId);

    @Select("SELECT * FROM inventory_receipt_batch WHERE internal_receipt_batch_no = #{internalReceiptBatchNo}")
    InventoryReceiptBatch findByInternalReceiptBatchNo(@Param("internalReceiptBatchNo") String internalReceiptBatchNo);

    /** 门店全部收货批次（日期风险提示用，一次查询取全量）。 */
    @Select("SELECT * FROM inventory_receipt_batch WHERE store_id = #{storeId} ORDER BY id")
    List<InventoryReceiptBatch> findByStore(@Param("storeId") Long storeId);
}
