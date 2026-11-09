package com.agri_system.application.order.dto.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/** 只读物料预检，不接受客户端拆分的套餐物料或可信客户身份。 */
@Data
public class PosStockCheckCommand {
    private Long storeId;
    private Long customerId;
    @NotEmpty @Size(max=500)
    private List<@NotNull @Valid OrderItemDTO> items;
}
