package com.agri_system.application.points.dto;

import lombok.Data;

@Data
public class PointsOrderQuery {

    private Long customerId;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}

