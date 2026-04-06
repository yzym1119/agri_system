package com.agri_system.application.customer.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户列表VO
 *
 * @author AI Generated
 */
@Data
@Schema(description = "客户列表")
public class CustomerListVO {

    @Schema(description = "客户ID", example = "1")
    private Long id;

    @Schema(description = "客户编码", example = "C20240101000001")
    private String code;

    @Schema(description = "客户姓名", example = "张三")
    private String name;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "客户类型：1-普通，2-VIP", example = "2")
    private Integer type;

    @Schema(description = "VIP等级", example = "1")
    private Integer vipLevel;

    @Schema(description = "VIP结束日期")
    private LocalDate vipEndDate;

    @Schema(description = "本金余额", example = "1000.00")
    private BigDecimal balance;

    @Schema(description = "专用金余额", example = "500.00")
    private BigDecimal specialFundBalance;

    @Schema(description = "冻结专用金", example = "100.00")
    private BigDecimal frozenSpecialFund;

    @Schema(description = "积分", example = "1000")
    private BigDecimal points;

    @Schema(description = "归属员工ID", example = "1")
    private Long bindStaffId;

    @Schema(description = "归属员工姓名", example = "李四")
    private String bindStaffName;

    @Schema(description = "状态：1-正常，2-停用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

