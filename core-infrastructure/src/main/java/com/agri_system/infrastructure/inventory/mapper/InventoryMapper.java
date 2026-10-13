package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.Inventory;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存快照Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT id FROM product WHERE id=#{productId} FOR UPDATE")
    Long lockProduct(@Param("productId") Long productId);

    @Insert("INSERT INTO inventory(store_id, product_id, quantity) VALUES(#{storeId}, #{productId}, #{quantity}) ON DUPLICATE KEY UPDATE id=id")
    void ensureSnapshot(@Param("storeId") Long storeId, @Param("productId") Long productId,
                        @Param("quantity") java.math.BigDecimal quantity);

    @Update("UPDATE inventory i JOIN product p ON p.id=i.product_id JOIN product_unit u ON u.product_id=p.id AND u.unit_name=p.main_unit SET i.quantity=#{quantity}/u.base_quantity_per_unit WHERE i.store_id=#{storeId} AND i.product_id=#{productId}")
    int projectBaseQuantity(@Param("storeId") Long storeId, @Param("productId") Long productId,
                            @Param("quantity") java.math.BigDecimal quantity);

    /** 悲观锁查询库存快照,串行化同门店同商品的并发投影(Important 5)。 */
    @Select("SELECT * FROM inventory WHERE store_id = #{storeId} AND product_id = #{productId} FOR UPDATE")
    Inventory findByStoreAndProductForUpdate(@Param("storeId") Long storeId, @Param("productId") Long productId);
}






































