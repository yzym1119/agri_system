package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryReceiptLine;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryReceiptLineMapper extends BaseMapper<InventoryReceiptLine> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM inventory_receipt_line WHERE receipt_id=#{receiptId} ORDER BY line_no,id")
    java.util.List<InventoryReceiptLine> findByReceiptId(@org.apache.ibatis.annotations.Param("receiptId") Long receiptId);
}
