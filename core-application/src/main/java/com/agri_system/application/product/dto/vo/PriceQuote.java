package com.agri_system.application.product.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 服务端报价结果（可审计）。
 *
 * @param quoteId            报价ID（UUID，人工改价审计关联用）
 * @param orderItemId        订单行ID（报价阶段为空，审计时回填）
 * @param productId          商品ID
 * @param storeId            门店ID
 * @param channel            销售渠道
 * @param customerType       客户类型
 * @param vipLevel           VIP等级
 * @param saleUnit           实际用于报价的销售单位
 * @param saleUnitId         持久化销售单位ID
 * @param businessDate       业务日期
 * @param suggestedUnitPrice 服务端建议单价
 * @param ruleVersion        价格来源：RETAIL或MEMBER
 * @param referenceFloor     同销售单位的最低价提醒，可为空
 * @param belowReference     建议单价是否低于参考底价
 * @param legacyDerived      旧响应兼容字段，当前始终为false
 */
public record PriceQuote(
        String quoteId,
        Long orderItemId,
        Long productId,
        Long storeId,
        String channel,
        Integer customerType,
        Integer vipLevel,
        String saleUnit,
        Long saleUnitId,
        LocalDate businessDate,
        BigDecimal suggestedUnitPrice,
        String ruleVersion,
        BigDecimal referenceFloor,
        boolean belowReference,
        boolean legacyDerived) {

    /** 回填订单行ID（订单明细保存后用于人工改价审计）。 */
    public PriceQuote withOrderItemId(Long orderItemId) {
        return new PriceQuote(quoteId, orderItemId, productId, storeId, channel, customerType, vipLevel,
                saleUnit, saleUnitId, businessDate, suggestedUnitPrice, ruleVersion, referenceFloor,
                belowReference, legacyDerived);
    }
}
