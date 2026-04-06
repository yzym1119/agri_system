package com.agri_system.application.points.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PointsVerifyCommand {

    @NotBlank
    private String pickupCode;
}

