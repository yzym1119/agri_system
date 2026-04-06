package com.agri_system.application.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户价值分析 - 流失预警条目（支持Excel导出）
 */
@Data
public class ChurnRiskVO {

    @ExcelProperty("客户ID")
    private Long customerId;

    @ExcelProperty("客户姓名")
    private String customerName;

    @ExcelProperty("手机号")
    private String phone;

    /** 最近一次下单时间 */
    @ExcelProperty("最后下单时间")
    private LocalDateTime lastOrderTime;

    /** 距离最近下单的天数 */
    @ExcelProperty("流失天数")
    private Long daysSinceLastOrder;

    /** 历史累计贡献金额 */
    @ExcelProperty("历史贡献")
    private BigDecimal totalOrderAmount;
}


