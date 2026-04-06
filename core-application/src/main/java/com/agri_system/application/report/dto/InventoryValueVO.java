package com.agri_system.application.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存资金占用视图对象
 */
@Data
public class InventoryValueVO {

    /** 当前有效库存资金占用总额 */
    private BigDecimal totalInventoryValue;

    /** 库龄 > 180 天的批次金额 */
    private BigDecimal over180DaysValue;
}





