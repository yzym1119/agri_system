package com.agri_system.application.points.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointsAuditCommand {

    @NotNull
    private Long orderId;

    @NotNull
    private Boolean pass;

    private String remark;
}

