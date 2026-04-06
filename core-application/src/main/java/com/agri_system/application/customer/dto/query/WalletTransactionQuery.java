package com.agri_system.application.customer.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 钱包交易记录查询条件
 *
 * @author AI Generated
 */
@Data
@Schema(description = "钱包交易记录查询条件")
public class WalletTransactionQuery {

    @Schema(description = "客户ID（必填）", example = "1", required = true)
    private Long customerId;

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "钱包类型：1-本金，2-专用金，3-积分", example = "1")
    private Integer walletType;

    @Schema(description = "交易类型：1-充值，2-消费，3-退款，4-赠送，5-扣除", example = "1")
    private Integer transactionType;
}









