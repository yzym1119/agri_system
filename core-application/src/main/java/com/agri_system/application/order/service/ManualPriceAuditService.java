package com.agri_system.application.order.service;

import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.domain.order.model.OrderItemPriceAudit;
import com.agri_system.domain.order.repository.OrderItemPriceAuditRepository;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人工改价留痕服务。
 *
 * <p>记录“服务端报价 - 人工成交价”差异、原因与操作人/确认人，追加写到 order_item_price_audit。
 * 议价必须有权限、原因与操作人；低于参考底价由同一有权限的员工确认，无额外审批流程。</p>
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
        validateDeal(quote, dealPrice, reason, operatorId, confirmerId);
        boolean belowFloor = isBelowFloor(quote, dealPrice);
        if (belowFloor) {
            log.warn("低于参考底价 productId={}, saleUnit={}, suggestedUnitPrice={}, finalUnitPrice={}, referenceFloor={}",
                    quote.productId(), quote.saleUnit(), quote.suggestedUnitPrice(), dealPrice, quote.referenceFloor());
        }

        return saveAudit(quote, dealPrice, reason, operatorId, confirmerId);
    }

    /** 已在整套餐成交价处完成权限校验；这里只保存准确的子物料分摊差价，允许0元和12位参考单价。 */
    public Long recordAllocation(PriceQuote quote, BigDecimal dealPrice, String reason, Long operatorId) {
        if (quote == null || quote.orderItemId() == null || quote.suggestedUnitPrice() == null
                || dealPrice == null || dealPrice.signum() < 0 || dealPrice.scale() > 12
                || operatorId == null || !StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("套餐议价分摊记录不完整");
        }
        return saveAudit(quote, dealPrice, reason, operatorId, operatorId);
    }

    private Long saveAudit(PriceQuote quote, BigDecimal dealPrice, String reason, Long operatorId, Long confirmerId) {
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
    /** 下单保存任何明细、收款与出库之前校验，不能把报价故障当作人工议价。 */
    public void validateChange(PriceQuote quote, BigDecimal dealPrice, String reason, Long operatorId) {
        validateDeal(quote, dealPrice, reason, operatorId, operatorId);
        validateOperator(operatorId);
    }

    public void validateDiscount(BigDecimal amount, String reason, Long operatorId) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("整单优惠金额必须大于0且最多两位小数");
        }
        if (!StringUtils.hasText(reason) || reason.trim().length() > 255) {
            throw new IllegalArgumentException("整单优惠必须填写原因，最多255字");
        }
        validateOperator(operatorId);
    }

    private void validateOperator(Long operatorId) {
        if (!StpUtil.isLogin() || !("STAFF_" + operatorId).equals(StpUtil.getLoginIdAsString())) {
            throw new IllegalArgumentException("议价操作人必须是当前登录员工");
        }
        StpUtil.checkPermission("order:price:override");
    }

    private void validateDeal(PriceQuote quote, BigDecimal dealPrice, String reason,
                              Long operatorId, Long confirmerId) {
        if (quote == null || dealPrice == null || dealPrice.signum() <= 0 || dealPrice.scale() > 2) {
            throw new IllegalArgumentException("人工成交价必须大于0，且最多两位小数");
        }
        if (!StringUtils.hasText(reason) || reason.trim().length() > 255) {
            throw new IllegalArgumentException("请填写议价原因，最多255字");
        }
        if (operatorId == null) throw new IllegalArgumentException("议价操作人不能为空");
        if (isBelowFloor(quote, dealPrice) && confirmerId == null) {
            throw new IllegalArgumentException("低于参考底价需确认人");
        }
    }

    private boolean isBelowFloor(PriceQuote quote, BigDecimal dealPrice) {
        return quote.referenceFloor() != null && dealPrice.compareTo(quote.referenceFloor()) < 0;
    }

}
