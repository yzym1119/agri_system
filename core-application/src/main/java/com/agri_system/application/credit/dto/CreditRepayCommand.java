package com.agri_system.application.credit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditRepayCommand {

    @NotNull
    private Long customerId;

    @NotNull
    private Long storeId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private Integer paymentMethod;

    private String transactionId;

    private String remark;

    private Long createdBy;
}





