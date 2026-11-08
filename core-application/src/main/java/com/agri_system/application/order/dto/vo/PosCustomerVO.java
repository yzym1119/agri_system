package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 收银选客户投影。
 *
 * <p>只给开单必需的识别与配送信息：客户编号用于提交，姓名/电话/地址用于门店配送带出收货信息，
 * 会员标记用于说明本次报价为何是会员价。**不含**余额、专用金、欠款、积分与归属员工，
 * 避免把资金档案读权限随收款权限一起放开。</p>
 */
@Schema(description = "收银选客户投影")
public record PosCustomerVO(
        @Schema(description = "客户ID", example = "9101") Long id,
        @Schema(description = "客户编码", example = "C20240101000001") String code,
        @Schema(description = "客户姓名", example = "张三") String name,
        @Schema(description = "手机号", example = "13800138000") String phone,
        @Schema(description = "收货地址（可为空）") String address,
        @Schema(description = "客户类型：1-普通，2-VIP", example = "2") Integer type,
        @Schema(description = "VIP等级", example = "1") Integer vipLevel,
        @Schema(description = "会员当前是否有效（按真实有效期判定）", example = "true") boolean vipActive
) {}
