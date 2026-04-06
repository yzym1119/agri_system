package com.agri_system.application.points.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointsExchangeCommand {

    @NotNull
    private Long goodsId;

    @Min(1)
    private Integer quantity = 1;

    @NotBlank
    private String receiverName;

    @NotBlank
    private String receiverPhone;

    @NotBlank
    private String receiverAddress;
}

