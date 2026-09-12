package com.agri_system.infrastructure.purchase.mapper;

import com.agri_system.domain.purchase.model.PurchaseOrder;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 采购订单Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface PurchaseMapper extends BaseMapper<PurchaseOrder> {
    
    /**
     * 根据前缀查找最大订单号（用于生成流水号）
     */
    @Select("SELECT `purchase_no` FROM `purchase_order` WHERE `purchase_no` LIKE CONCAT(#{prefix}, '%') ORDER BY `purchase_no` DESC LIMIT 1")
    String findMaxOrderNoByPrefix(@Param("prefix") String prefix);

    @Select("SELECT * FROM purchase_order WHERE id = #{id} FOR UPDATE")
    PurchaseOrder findByIdForUpdate(@Param("id") Long id);
    
    @Update("UPDATE purchase_order SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
