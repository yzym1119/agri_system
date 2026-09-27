package com.agri_system.domain.order.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单行价格审计事实（追加写）。
 *
 * <p>每次人工改价留痕“服务端报价 - 成交价”差异、原因与操作人/确认人；
 * 低于参考底价时必须记录确认人（授权分层，不拒绝成交价）。</p>
 */
@Data
@Table("order_item_price_audit")
public class OrderItemPriceAudit {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long orderItemId;

    private Long productId;

    /** 销售单位ID（旧链路无 product_unit 时为 null）。 */
    private Long saleUnitId;

    private BigDecimal suggestedUnitPrice;

    private BigDecimal finalUnitPrice;

    private BigDecimal priceDifference;

    private String priceRuleVersion;

    private String changeReason;

    private Long operatorId;

    private Long confirmerId;

    private LocalDateTime decidedAt;

    private LocalDateTime createdAt;

    /** 服务端报价ID（PriceQuote.quoteId）。 */
    private String quoteId;
}
