package com.agri_system.application.customer.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 充值结果VO
 * 
 * @author AI Generated
 */
@Data
public class RechargeResultVO {
    
    /** 充值金额 */
    private BigDecimal rechargeAmount;
    
    /** 最新本金余额 */
    private BigDecimal latestBalance;
    
    /** 最新专用金余额 */
    private BigDecimal latestSpecialFund;
    
    /** 最新VIP等级 */
    private Integer latestVipLevel;
    
    /** VIP过期时间 */
    private LocalDate vipEndDate;
    
    /** 获赠优惠券列表 */
    private List<CouponInfo> grantedCoupons;
    
    /**
     * 优惠券信息
     */
    @Data
    public static class CouponInfo {
        /** 优惠券ID */
        private Long couponId;
        
        /** 券名称 */
        private String name;
        
        /** 面额 */
        private BigDecimal amount;
    }
}







