package com.agri_system.application.performance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "performance")
public class PerformanceProperties {

    /**
     * 单行提成最大毛利比例
     */
    private BigDecimal maxCommissionRatio = new BigDecimal("0.20");

    /**
     * 首单有效金额阈值（30 天累计）
     */
    private BigDecimal newCustomerAmountThreshold = new BigDecimal("300");

    /**
     * 首单毛利阈值
     */
    private BigDecimal newCustomerProfitThreshold = new BigDecimal("30");

    /**
     * 指定品类最小金额
     */
    private BigDecimal newCustomerMinCategoryAmount = new BigDecimal("200");

    /**
     * 拉新指定品类
     */
    private List<Long> newCustomerCategoryWhitelist = List.of();

    /**
     * 二维码有效期（秒）
     */
    private Integer qrExpireSeconds = 90;
}

