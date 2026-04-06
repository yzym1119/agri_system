package com.agri_system.application.customer.dto;

import lombok.Data;

/**
 * 积分兑换命令
 * 
 * @author AI Generated
 */
@Data
public class PointsExchangeCommand {
    
    /** 客户ID */
    private Long customerId;
    
    /** 积分商品ID */
    private Long goodsId;
    
    /** 兑换数量 */
    private Integer quantity;
}






