package com.agri_system.infrastructure.common.mapper;

import com.agri_system.domain.common.model.CodeSequence;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 编码序号Mapper
 * 
 * @author AI Generated
 */
@Mapper
public interface CodeSequenceMapper extends BaseMapper<CodeSequence> {
    
    /**
     * 原子操作：获取并递增序号
     * 返回更新后的序号值
     */
    @Update("UPDATE sys_code_sequence SET sequence = (@seq := sequence + 1), last_code = CONCAT(#{prefix}, LPAD(@seq, #{length}, '0')) WHERE code_type = #{codeType}")
    int incrementSequence(@Param("codeType") String codeType, @Param("prefix") String prefix, @Param("length") int length);
}

