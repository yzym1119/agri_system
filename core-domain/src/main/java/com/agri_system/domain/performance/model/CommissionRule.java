package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提成规则
 */
@Data
@Table("commission_rule")
public class CommissionRule {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long productId;

    private Long staffId;

    private Integer ruleCategory;

    private Long targetId;

    private String calcMode;

    private BigDecimal calcValue;

    private BigDecimal maxPerOrder;

    private BigDecimal maxPerItem;

    private Integer priority;

    private Integer status;

    private Integer enabled;

    private Integer legacyRuleType;

    private BigDecimal legacyFixedAmount;

    private BigDecimal legacyProfitPercentage;

    private LocalDate effectiveDate;

    private LocalDate expiryDate;

    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    private Long createdBy;

    public boolean checkEnabled() {
        return enabled == null || enabled == 1;
    }

    public enum Category {
        SALES(1),
        RECHARGE(2),
        NEW_CUSTOMER(3),
        BRAND_PROMOTION(4),
        PACKAGE(5);

        private final int code;

        Category(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}

