package com.agri_system.application.points.dto;

import lombok.Data;

@Data
public class PointsGoodsVO {

    private Long id;
    private String code;
    private String name;
    private Integer points;
    private Integer stock;
    private String imageUrl;
    private String description;
    private Integer status;
    private Integer sortOrder;
}

