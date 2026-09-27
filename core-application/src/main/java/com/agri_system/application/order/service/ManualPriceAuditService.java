package com.agri_system.application.order.service;

import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.domain.order.model.OrderItemPriceAudit;
import com.agri_system.domain.order.repository.OrderItemPriceAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人工改价留痕服务。
 *
 * <p>记录“服务端报价 - 人工成交价”差异、原因与操作人/确认人，追加写到 order_item_price_audit。
 * 低于参考底价属于授权分层问题：必须记录确认人，但绝不拒绝或改写人工成交价。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPriceAuditService {

    private final OrderItemPriceAuditRepository orderItemPriceAuditRepository;

    /**
     * 记录一次人工改价。
     *
     * @param quote       服务端报价（必须已回填 orderItemId）
     * @param dealPrice   人工成交价
     * @param reason      改价原因
     * @param operatorId  操作人ID
     * @param confirmerId 确认人ID（低于参考底价时必填）
     * @return 审计记录ID
     */
    public Long record(PriceQuote quote, BigDecimal dealPrice, String reason, Long operatorId, Long confirmerId) {
        if (quote == null || quote.orderItemId() == null) {
            throw new IllegalArgumentException("人工改价必须关联服务端报价与订单行");
        }
        if (dealPrice == null || dealPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("人工成交价不能为空或负数");
        }
        if (quote.belowReference() && confirmerId == null) {
            throw new IllegalArgumentException("低于参考底价需确认人");
        }
        if (quote.belowReference()) {
            log.warn("BELOW_REFERENCE_PRICE productId={}, saleUnit={}, suggestedUnitPrice={}, finalUnitPrice={}, referenceFloor={}",
                    quote.productId(), quote.saleUnit(), quote.suggestedUnitPrice(), dealPrice, quote.referenceFloor());
        }

        OrderItemPriceAudit audit = new OrderItemPriceAudit();
        audit.setOrderItemId(quote.orderItemId());
        audit.setProductId(quote.productId());
        audit.setSaleUnitId(quote.saleUnitId());
        audit.setSuggestedUnitPrice(quote.suggestedUnitPrice());
        audit.setFinalUnitPrice(dealPrice);
        audit.setPriceDifference(dealPrice.subtract(quote.suggestedUnitPrice()));
        audit.setPriceRuleVersion(quote.ruleVersion());
        audit.setChangeReason(reason);
        audit.setOperatorId(operatorId);
        audit.setConfirmerId(confirmerId);
        audit.setQuoteId(quote.quoteId());
        audit.setDecidedAt(LocalDateTime.now());
        audit.setCreatedAt(LocalDateTime.now());
        return orderItemPriceAuditRepository.save(audit).getId();
    }
}
