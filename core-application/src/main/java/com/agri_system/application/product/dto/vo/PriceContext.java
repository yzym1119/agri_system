package com.agri_system.application.product.dto.vo;

import java.time.LocalDate;

/**
 * 报价上下文：按商品/门店/渠道/客户分层与销售单位解析价格。
 *
 * @param productId    商品ID
 * @param storeId      门店ID（可为空）
 * @param channel      销售渠道（可为空，如 POS/小程序）
 * @param customerType 客户类型（可由调用方解析后传入）
 * @param vipLevel     VIP等级
 * @param saleUnit     销售单位（为空表示按商品主单位）
 * @param businessDate 业务日期（为空表示今天，用于生效期过滤）
 */
public record PriceContext(
        Long productId,
        Long storeId,
        String channel,
        Integer customerType,
        Integer vipLevel,
        String saleUnit,
        LocalDate businessDate) {
}
