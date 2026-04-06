package com.agri_system.application.inventory.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次详情VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "批次详情")
public class BatchDetailVO {
    
    @Schema(description = "批次ID", example = "1")
    private Long id;
    
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "批次号", example = "BATCH20240101001")
    private String batchNo;
    
    @Schema(description = "生产日期", example = "2024-01-01")
    private LocalDate productionDate;
    
    @Schema(description = "有效期至", example = "2025-01-01")
    private LocalDate expireDate;
    
    @Schema(description = "当前剩余数量", example = "100.0000")
    private BigDecimal quantity;
    
    @Schema(description = "成本价（元）", example = "10.50")
    private BigDecimal costPrice;
    
    @Schema(description = "是否有效：1-有效，0-归档", example = "1")
    private Integer isActive;
    
    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间", example = "2024-01-01T10:00:00")
    private LocalDateTime updatedAt;
    
    @Schema(description = "是否已过期", example = "false")
    private Boolean isExpired;
    
    @Schema(description = "是否即将过期（7天内）", example = "false")
    private Boolean isExpiringSoon;
}









