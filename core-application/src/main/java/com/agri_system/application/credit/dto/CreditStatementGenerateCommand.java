package com.agri_system.application.credit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreditStatementGenerateCommand {

    @NotNull
    private Long customerId;
    private Long storeId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private Long createdBy;
    private String remark;
}






