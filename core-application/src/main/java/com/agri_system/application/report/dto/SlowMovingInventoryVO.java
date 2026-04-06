package com.agri_system.application.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 滞销商品VO（支持Excel导出）
 */
@Data
public class SlowMovingInventoryVO {

    @ExcelProperty("商品ID")
    private Long productId;

    @ExcelProperty("商品名称")
    private String productName;

    @ExcelProperty("批次号")
    private String batchNo;

    @ExcelProperty("剩余数量")
    private BigDecimal quantity;

    @ExcelProperty("占用金额")
    private BigDecimal value;

    @ExcelProperty("生产日期")
    private LocalDate productionDate;

    @ExcelProperty("入库日期")
    private LocalDateTime createdAt;

    @ExcelProperty("库龄(天)")
    private Long ageDays;
}




