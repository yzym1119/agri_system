package com.agri_system.domain.after_sales.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后明细
 *
 * @author AI
 */
@Data
@Table("after_sales_item")
public class AfterSalesItem {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long afterSalesId;

    private Long orderItemId;

    private Long productId;

    private BigDecimal quantity;

    private BigDecimal refundAmount;

    private LocalDateTime createdAt;
}








