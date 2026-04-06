package com.agri_system.domain.inventory.enums;

import lombok.Getter;

/**
 * 库存流水类型枚举
 * 
 * @author AI Generated
 */
@Getter
public enum InventoryTransactionType {
    
    /** 采购入库 */
    PURCHASE_IN(1, "采购入库"),
    
    /** 线下销售 */
    POS_SALES(2, "线下销售"),
    
    /** 线上销售 */
    ONLINE_SALES(3, "线上销售"),
    
    /** 售后退回 */
    SALES_RETURN(4, "售后退回"),
    
    /** 调拨出库 */
    TRANSFER_OUT(5, "调拨出库"),
    
    /** 调拨入库 */
    TRANSFER_IN(6, "调拨入库"),
    
    /** 盘点盘盈 */
    ADJUSTMENT_IN(7, "盘点盘盈"),
    
    /** 盘点盘亏 */
    ADJUSTMENT_OUT(8, "盘点盘亏");
    
    private final Integer code;
    private final String desc;
    
    InventoryTransactionType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static InventoryTransactionType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (InventoryTransactionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}









