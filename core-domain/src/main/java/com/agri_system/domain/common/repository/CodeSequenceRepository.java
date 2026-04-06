package com.agri_system.domain.common.repository;

import com.agri_system.domain.common.model.CodeSequence;

import java.util.Optional;

/**
 * 编码序号仓储接口
 * 
 * @author AI Generated
 */
public interface CodeSequenceRepository {
    
    /**
     * 根据编码类型查找序号
     */
    Optional<CodeSequence> findByCodeType(String codeType);
    
    /**
     * 保存或更新序号
     */
    void save(CodeSequence sequence);
    
    /**
     * 获取并递增序号（原子操作）
     * 
     * @param codeType 编码类型
     * @return 新的序号
     */
    Long getAndIncrement(String codeType);
}






