package com.agri_system.application.performance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工提成看板VO
 * 核心设计：让员工一眼看到"今天赚了多少、这个月能拿多少"
 */
@Data
public class StaffCommissionDashboardVO {

    /** 员工姓名 */
    private String staffName;

    /** 所属门店 */
    private String storeName;

    /** 岗位 */
    private String position;

    // ==================== 今日数据 ====================

    /** 今日提成金额 */
    private BigDecimal todayCommission;

    /** 今日销售额 */
    private BigDecimal todaySalesAmount;

    /** 今日成交单数 */
    private Integer todayOrderCount;

    // ==================== 本月数据 ====================

    /** 本月累计提成 */
    private BigDecimal monthCommission;

    /** 本月累计销售额 */
    private BigDecimal monthSalesAmount;

    /** 本月成交单数 */
    private Integer monthOrderCount;

    // ==================== 目标进度 ====================

    /** 月度销售目标 */
    private BigDecimal targetSalesAmount;

    /** 销售目标完成率(%) */
    private BigDecimal salesTargetRate;

    /** 月度新客目标 */
    private Integer targetNewCustomer;

    /** 本月新增客户数 */
    private Integer monthNewCustomer;

    /** 新客目标完成率(%) */
    private BigDecimal newCustomerTargetRate;

    // ==================== 预估工资 ====================

    /** 基本底薪 */
    private BigDecimal salaryBase;

    /** 预估月度总工资 = 底薪 + 提成 */
    private BigDecimal estimatedSalary;

    // ==================== 排名 ====================

    /** 本月提成排名（在门店内） */
    private Integer monthRank;

    /** 门店总人数 */
    private Integer storeStaffCount;

    // ==================== 归属客户 ====================

    /** 归属客户总数 */
    private Integer bindCustomerCount;

    /** 待跟进客户数 */
    private Integer pendingFollowUpCount;

    // ==================== 最近提成明细 ====================

    /** 最近5条提成记录 */
    private List<CommissionRecord> recentRecords;

    @Data
    public static class CommissionRecord {
        /** 来源：销售订单/充值/新客/套餐 */
        private String source;
        /** 关联名称（如商品名） */
        private String targetName;
        /** 提成金额 */
        private BigDecimal amount;
        /** 时间 */
        private String time;
    }
}
