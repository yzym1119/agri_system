package com.agri_system.infrastructure.product.mapper;

import com.agri_system.domain.product.model.Product;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    
    /**
     * 强制更新商品的关键字段（包括 null 值和 0 值）
     * 用于处理需要清空字段或设置值为 0 的情况
     */
    @Update("UPDATE product SET " +
            "name = #{name}, " +
            "specification = #{specification}, " +
            "dosage_form = #{dosageForm}, " +
            "manufacturer = #{manufacturer}, " +
            "main_unit = #{mainUnit}, " +
            "aux_unit = #{auxUnit}, " +
            "main_to_aux_ratio = #{mainToAuxRatio}, " +
            "suggested_retail_price = #{suggestedRetailPrice}, " +
            "vip_price = #{vipPrice}, " +
            "min_price = #{minPrice}, " +
            "image_url = #{imageUrl}, " +
            "detail_text = #{detailText}, " +
            "is_online = #{isOnline}, " +
            "online_price = #{onlinePrice}, " +
            "base_shipping_fee = #{baseShippingFee}, " +
            "allow_special_fund = #{allowSpecialFund}, " +
            "remark = #{remark}, " +
            "category_id = #{categoryId}, " +
            "barcode = #{barcode}, " +
            "registration_no = #{registrationNo}, " +
            "storage_conditions = #{storageConditions}, " +
            "shelf_life_days = #{shelfLifeDays}, " +
            "usage_method = #{usageMethod}, " +
            "min_stock = #{minStock}, " +
            "max_stock = #{maxStock}, " +
            "reorder_quantity = #{reorderQuantity}, " +
            "updated_at = #{updatedAt}, " +
            "updated_by = #{updatedBy} " +
            "WHERE id = #{id}")
    int updateForce(@Param("id") Long id,
                    @Param("name") String name,
                    @Param("categoryId") Long categoryId,
                    @Param("specification") String specification,
                    @Param("dosageForm") String dosageForm,
                    @Param("manufacturer") String manufacturer,
                    @Param("mainUnit") String mainUnit,
                    @Param("auxUnit") String auxUnit,
                    @Param("mainToAuxRatio") BigDecimal mainToAuxRatio,
                    @Param("suggestedRetailPrice") BigDecimal suggestedRetailPrice,
                    @Param("vipPrice") BigDecimal vipPrice,
                    @Param("minPrice") BigDecimal minPrice,
                    @Param("imageUrl") String imageUrl,
                    @Param("detailText") String detailText,
                    @Param("isOnline") Integer isOnline,
                    @Param("onlinePrice") BigDecimal onlinePrice,
                    @Param("baseShippingFee") BigDecimal baseShippingFee,
                    @Param("allowSpecialFund") Integer allowSpecialFund,
                    @Param("remark") String remark,
                    @Param("barcode") String barcode,
                    @Param("registrationNo") String registrationNo,
                    @Param("storageConditions") String storageConditions,
                    @Param("shelfLifeDays") Integer shelfLifeDays,
                    @Param("usageMethod") String usageMethod,
                    @Param("minStock") BigDecimal minStock,
                    @Param("maxStock") BigDecimal maxStock,
                    @Param("reorderQuantity") BigDecimal reorderQuantity,
                    @Param("updatedAt") LocalDateTime updatedAt,
                    @Param("updatedBy") Long updatedBy);
}




























