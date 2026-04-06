package com.agri_system.application.credit.dto;

import jakarta.validation.constraints.Min;
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
    @Min(1)
    private BigDecimal amount;

    @NotNull
    private Integer paymentMethod;

    private String transactionId;

    private String remark;

    private Long createdBy;
}






