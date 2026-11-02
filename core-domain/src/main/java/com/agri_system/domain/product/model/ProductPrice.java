package com.agri_system.domain.product.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品价格实体（策略价格）
 *
 * @deprecated V130 起多维价格策略已停用：写入入口在 ProductApplicationService 被拒绝，
 * 定价一律走商品档案基准单价 × 单位系数。本表仅保留历史数据只读查询（getPriceRules），
 * 禁止新增任何写入调用方或结构变更。
 * @author AI Generated
 */
@Deprecated
@Data
@Table("product_price")
public class ProductPrice {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 商品ID */
    private Long productId;
    
    /** 门店ID（为空表示全局价格） */
    @Column(tenantId = true)
    private Long storeId;
    
    /** 客户类型：1-普通客户，2-VIP客户（为空表示通用价格） */
    private Integer customerType;
    
    /** VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效） */
    private Integer vipLevel;
    
    /** 价格类型：1-建议零售价，2-VIP价，3-最低限价，4-进价 */
    private Integer priceType;
    
    /** 价格（元） */
    private BigDecimal price;
    
    /** 生效日期 */
    private LocalDate effectiveDate;
    
    /** 失效日期 */
    private LocalDate expiryDate;
    
    /** 状态：1-启用，0-停用 */
    private Integer status;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /** 创建人ID */
    private Long createdBy;
    
    /** 销售单位（为空表示旧链路按主单位换算） */
    private String saleUnit;
    
    /** 销售渠道（为空表示通用规则） */
    private String channel;
    
    /** 规则版本（报价审计追溯用） */
    private String ruleVersion;
    
    /**
     * 检查价格规则是否有效
     */
    public boolean isActive() {
        return isActiveOn(LocalDate.now());
    }
    
    /**
     * 检查价格规则在指定业务日期是否有效
     */
    public boolean isActiveOn(LocalDate businessDate) {
        if (status == null || status != 1) {
            return false;
        }
        LocalDate date = businessDate != null ? businessDate : LocalDate.now();
        if (effectiveDate != null && date.isBefore(effectiveDate)) {
            return false;
        }
        if (expiryDate != null && date.isAfter(expiryDate)) {
            return false;
        }
        return true;
    }
}
