package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单配送实体
 * 
 * @author AI Generated
 */
@Data
@Table("order_delivery")
public class OrderDelivery {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 订单ID */
    private Long orderId;
    
    /** 配送单号 */
    private String deliveryNo;
    
    /** 物流公司名称（快递发货时必填） */
    private String logisticsCompany;
    
    /** 快递单号（快递发货时必填） */
    private String trackingNo;
    
    /** 配送状态：1-待审核(WAIT_AUDIT), 2-已审核(AUDITED), 3-配送中(SHIPPING), 
     *           4-已送达(DELIVERED), 5-已拒收(REJECTED) */
    private Integer deliveryStatus;
    
    /** 配送地址 */
    private String deliveryAddress;
    
    /** 配送联系电话 */
    private String deliveryPhone;

    /** 配送方式：2-同城配送，3-快递配送 */
    private Integer deliveryType;
    
    /** 配送员工ID */
    private Long deliveryStaffId;
    
    /** 审核状态：1-待审核, 2-已通过, 3-已拒绝 */
    private Integer auditStatus;
    
    /** 审核备注 */
    private String auditRemark;
    
    /** 审核时间 */
    private LocalDateTime auditAt;
    
    /** 审核人ID */
    private Long auditBy;
    
    /** 配送时间 */
    private LocalDateTime deliveryAt;
    
    /** 签收时间 */
    private LocalDateTime receivedAt;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
}







