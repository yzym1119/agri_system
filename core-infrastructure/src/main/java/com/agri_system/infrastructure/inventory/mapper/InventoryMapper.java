package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.Inventory;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存快照Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /** 悲观锁查询库存快照,串行化同门店同商品的并发投影(Important 5)。 */
    @Select("SELECT * FROM inventory WHERE store_id = #{storeId} AND product_id = #{productId} FOR UPDATE")
    Inventory findByStoreAndProductForUpdate(@Param("storeId") Long storeId, @Param("productId") Long productId);
}






































