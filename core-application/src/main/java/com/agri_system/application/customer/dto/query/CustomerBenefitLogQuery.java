package com.agri_system.application.customer.dto.query;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户权益流水查询参数
 * 
 * @author AI Generated
 */
@Data
public class CustomerBenefitLogQuery {
    
    /** 客户ID */
    @NotNull(message = "客户ID不能为空")
    private Long customerId;
    
    /** 页码 */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 10;
}









