package com.agri_system.application.points.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointsGoodsSaveCommand {

    private String code; // 可选，如果为空则后端自动生成

    @NotBlank
    private String name;

    @NotNull
    @Min(1)
    private Integer points;

    @NotNull
    @Min(0)
    private Integer stock;

    private String imageUrl;

    private String description;

    private Integer sortOrder = 0;

    private Integer status = 1;
}

