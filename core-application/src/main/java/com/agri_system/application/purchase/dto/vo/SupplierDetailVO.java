package com.agri_system.application.purchase.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商详情VO
 * 
 * @author AI Generated
 */
@Data
public class SupplierDetailVO {
    
    private Long id;
    private String code;
    private String name;
    private String contactName;
    private String contactPhone;
    private String bankAccount;
    private String bankName;
    private String address;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

