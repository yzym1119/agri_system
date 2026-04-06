package com.agri_system.domain.product.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品价格实体（策略价格）
 * 
 * @author AI Generated
 */
@Data
@Table("product_price")
public class ProductPrice {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 商品ID */
    private Long productId;
    
    /** 门店ID（为空表示全局价格） */
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
    
    /** 状态：1-有效，2-无效 */
    private Integer status;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /** 创建人ID */
    private Long createdBy;
    
    /**
     * 检查价格规则是否有效
     */
    public boolean isActive() {
        if (status == null || status != 1) {
            return false;
        }
        LocalDate now = LocalDate.now();
        if (effectiveDate != null && now.isBefore(effectiveDate)) {
            return false;
        }
        if (expiryDate != null && now.isAfter(expiryDate)) {
            return false;
        }
        return true;
    }
}

