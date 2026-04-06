package com.agri_system.application.customer.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包交易记录VO
 *
 * @author AI Generated
 */
@Data
@Schema(description = "钱包交易记录")
public class WalletTransactionVO {

    @Schema(description = "交易ID", example = "1")
    private Long id;

    @Schema(description = "钱包类型：1-本金，2-专用金，3-积分", example = "1")
    private Integer walletType;

    @Schema(description = "交易类型：1-充值，2-消费，3-退款，4-赠送，5-扣除", example = "1")
    private Integer transactionType;

    @Schema(description = "变动金额（正数为增加，负数为减少）", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "交易前余额", example = "900.00")
    private BigDecimal balanceBefore;

    @Schema(description = "交易后余额", example = "1000.00")
    private BigDecimal balanceAfter;

    @Schema(description = "关联类型", example = "RECHARGE")
    private String referenceType;

    @Schema(description = "关联ID", example = "1")
    private Long referenceId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "支付方式：1-现金，2-微信，3-支付宝（仅充值交易有效）", example = "1")
    private Integer paymentMethod;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "操作人姓名", example = "李四")
    private String createdByName;
}

