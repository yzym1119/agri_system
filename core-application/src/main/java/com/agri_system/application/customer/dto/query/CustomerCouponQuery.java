package com.agri_system.application.customer.dto.query;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户优惠券查询参数
 * 
 * @author AI Generated
 */
@Data
public class CustomerCouponQuery {
    
    /** 客户ID */
    @NotNull(message = "客户ID不能为空")
    private Long customerId;
    
    /** 状态：1-未使用, 2-已使用, 3-已过期, 4-已作废（为空则查询所有） */
    private Integer status;
    
    /** 页码 */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 10;
}









