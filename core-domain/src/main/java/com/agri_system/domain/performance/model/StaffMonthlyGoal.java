package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("staff_monthly_goal")
public class StaffMonthlyGoal {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long staffId;

    private String month;

    private BigDecimal targetSalesAmount;

    private Integer targetNewCustomer;

    private BigDecimal targetCommission;

    private Integer status;

    private LocalDateTime createdAt;
}

