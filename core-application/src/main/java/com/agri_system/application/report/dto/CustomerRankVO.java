package com.agri_system.application.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户价值分析 - 大户排行榜条目（支持Excel导出）
 */
@Data
public class CustomerRankVO {

    @ExcelProperty("客户ID")
    private Long customerId;

    @ExcelProperty("客户姓名")
    private String customerName;

    @ExcelProperty("手机号")
    private String phone;

    /** 累计成交金额 */
    @ExcelProperty("累计消费")
    private BigDecimal totalOrderAmount;

    /** 最后下单时间 */
    @ExcelProperty("最近购买日")
    private LocalDateTime lastOrderTime;

    /** 归属员工姓名（若有绑定） */
    @ExcelProperty("归属员工")
    private String staffName;
}


