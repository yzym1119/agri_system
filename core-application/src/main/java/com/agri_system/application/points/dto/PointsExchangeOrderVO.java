package com.agri_system.application.points.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointsExchangeOrderVO {

    private Long id;
    private String exchangeNo;
    private Long customerId;
    private Long goodsId;
    private String goodsName;
    private String goodsImage;
    private Integer points;
    private Integer quantity;
    private Integer status;
    private String statusDesc;
    private String pickupCode;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime createdAt;
}

