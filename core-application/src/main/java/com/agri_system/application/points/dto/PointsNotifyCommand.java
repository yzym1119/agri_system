package com.agri_system.application.points.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointsNotifyCommand {

    @NotNull
    private Long orderId;

    private String remark;
}

