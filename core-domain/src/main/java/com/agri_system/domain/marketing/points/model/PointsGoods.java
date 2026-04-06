package com.agri_system.domain.marketing.points.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分商品实体
 */
@Data
@Table("points_goods")
public class PointsGoods {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 礼品编码 */
    private String code;

    /** 礼品名称 */
    private String name;

    /** 所需积分 */
    private Integer points;

    /** 库存 */
    private Integer stock;

    /** 图片地址 */
    private String imageUrl;

    /** 描述 */
    private String description;

    /** 状态：1-上架 2-下架 */
    private Integer status;

    /** 排序值（越小越靠前） */
    private Integer sortOrder;

    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isOnSale() {
        return status != null && status == 1;
    }

    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于0");
        }
        if (stock == null) {
            stock = 0;
        }
        if (stock < quantity) {
            throw new IllegalArgumentException("积分商品库存不足，当前库存:" + stock);
        }
        this.stock = stock - quantity;
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            return;
        }
        if (stock == null) {
            stock = 0;
        }
        this.stock = stock + quantity;
    }
}

