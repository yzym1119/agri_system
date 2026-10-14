package com.agri_system.infrastructure.credit.mapper;

import com.agri_system.domain.credit.model.CreditAccount;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;

@Mapper
public interface CreditAccountMapper extends BaseMapper<CreditAccount> {
    /** 客户额度为全店口径：原生明确SQL仅用于内部标量计算，不绕过明细授权。 */
    @Select("SELECT COALESCE(SUM(remaining_amount),0) FROM credit_account WHERE customer_id=#{customerId}")
    BigDecimal sumRemainingAmountAllStores(@Param("customerId") Long customerId);
}






