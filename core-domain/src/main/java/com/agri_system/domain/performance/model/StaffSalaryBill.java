package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("staff_salary_bill")
public class StaffSalaryBill {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long staffId;

    private String month;

    private BigDecimal totalCommission;

    private BigDecimal totalSales;

    private Integer newCustomerCount;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;
}

