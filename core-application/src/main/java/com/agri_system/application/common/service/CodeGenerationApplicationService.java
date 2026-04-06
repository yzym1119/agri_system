package com.agri_system.application.common.service;

import com.agri_system.domain.common.repository.CodeSequenceRepository;
import com.agri_system.domain.common.service.CodeGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编码生成应用服务
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class CodeGenerationApplicationService {
    
    private final CodeSequenceRepository sequenceRepository;
    
    /**
     * 生成下一个编码
     * 
     * @param codeType 编码类型
     * @return 新的编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String generateNextCode(CodeGenerationService.CodeType codeType) {
        // 获取并递增序号
        Long sequence = sequenceRepository.getAndIncrement(codeType.name());
        
        // 生成编码
        String prefix = codeType.getPrefix();
        int length = codeType.getSequenceLength();
        return prefix + String.format("%0" + length + "d", sequence);
    }
}






