package com.agri_system.application.order.dto.command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
/** 门店退货资金预览，省略金额时按原净成交及累计实退数量计算。 */
@Data
public class OrderRefundPreviewCommand {
    @Valid private List<Item> items;
    private Long afterSalesId;
    @Data public static class Item {
        @NotNull private Long orderItemId;
        @NotNull @DecimalMin("0.0001") private BigDecimal quantity;
        @DecimalMin("0.00") private BigDecimal refundAmount;
    }
}
