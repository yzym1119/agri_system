package com.agri_system.application.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售趋势VO（支持Excel导出）
 */
@Data
public class SalesTrendVO {

    @ExcelProperty("日期")
    private LocalDate date;

    @ExcelProperty("销售额")
    private BigDecimal saleAmount;

    @ExcelProperty("成本额")
    private BigDecimal costAmount;

    @ExcelProperty("毛利额")
    private BigDecimal profit;

    @ExcelProperty("毛利率(%)")
    private BigDecimal profitRate;
}




