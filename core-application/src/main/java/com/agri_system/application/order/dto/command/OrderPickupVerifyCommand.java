package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自提核销命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "自提核销命令")
public class OrderPickupVerifyCommand {
    
    @NotBlank(message = "自提码不能为空")
    @Schema(description = "自提码（6位数字）", example = "123456")
    private String pickupCode;
}

















