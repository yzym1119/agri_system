package com.agri_system.application.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 手动发专用金命令
 *
 * @author AI Generated
 */
@Data
public class GrantSpecialFundCommand {

    /** 客户ID */
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    /** 发放请求标识：幂等键，同一标识重复提交只发放一次 */
    @NotBlank(message = "发放请求标识不能为空")
    @Size(max = 64, message = "发放请求标识不能超过64字符")
    private String requestKey;

    /** 发放金额；单次上限由 WalletApplicationService 统一守卫，避免两处硬编码。 */
    @NotNull(message = "发放金额不能为空")
    @DecimalMin(value = "0.01", message = "发放金额必须大于0")
    @Digits(integer = 8, fraction = 2, message = "发放金额最多两位小数")
    private BigDecimal amount;

    /** 备注，与 wallet_transaction.remark varchar(500) 对齐 */
    @Size(max = 500, message = "备注最多500字")
    private String remark;

    /** 操作人ID，由登录身份覆盖，不接受客户端指定 */
    private Long createdBy;
}
