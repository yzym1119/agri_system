package com.agri_system.infrastructure.common.repository;

import com.agri_system.domain.common.model.CodeSequence;
import com.agri_system.domain.common.repository.CodeSequenceRepository;
import com.agri_system.domain.common.service.CodeGenerationService;
import com.agri_system.infrastructure.common.mapper.CodeSequenceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 编码序号仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CodeSequenceRepositoryImpl implements CodeSequenceRepository {
    
    private final CodeSequenceMapper sequenceMapper;
    
    @Override
    public Optional<CodeSequence> findByCodeType(String codeType) {
        CodeSequence sequence = sequenceMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("code_type = ?", codeType)
        );
        return Optional.ofNullable(sequence);
    }
    
    @Override
    public void save(CodeSequence sequence) {
        if (sequence.getId() == null) {
            sequenceMapper.insert(sequence);
        } else {
            sequenceMapper.update(sequence);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getAndIncrement(String codeType) {
        CodeSequence sequence = findByCodeType(codeType)
                .orElseThrow(() -> new IllegalArgumentException("编码类型不存在: " + codeType));
        
        CodeGenerationService.CodeType type = CodeGenerationService.CodeType.valueOf(codeType);
        String prefix = type.getPrefix();
        int length = type.getSequenceLength();
        
        // 原子操作：递增序号并更新 last_code
        int updated = sequenceMapper.incrementSequence(codeType, prefix, length);
        if (updated == 0) {
            throw new IllegalStateException("更新编码序号失败: " + codeType);
        }
        
        // 重新查询获取最新序号
        sequence = findByCodeType(codeType)
                .orElseThrow(() -> new IllegalStateException("编码序号不存在: " + codeType));
        
        return sequence.getSequence();
    }
}

