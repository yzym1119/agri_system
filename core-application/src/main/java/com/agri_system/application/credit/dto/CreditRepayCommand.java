package com.agri_system.application.credit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditRepayCommand {

    @NotNull
    private Long customerId;

    private Long storeId;

    @NotBlank(message = "还款请求标识不能为空")
    @Size(max = 64, message = "还款请求标识不能超过64个字符")
    private String requestKey;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private Integer paymentMethod;

    private String transactionId;

    private String remark;

    private Long createdBy;
}





