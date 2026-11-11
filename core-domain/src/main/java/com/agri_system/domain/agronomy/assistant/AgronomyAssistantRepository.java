package com.agri_system.domain.agronomy.assistant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/** 只操作既有entry的AI元数据，不建立第二份问题或改写原文。 */
public interface AgronomyAssistantRepository {
    record Usage(long modelCalls,long searchCalls,long reservedMicrousd) {}
    Optional<AssistantRequestMetadata> find(Long entryId);
    void initialize(AssistantRequestMetadata value);
    boolean hasOpen(Long caseId);
    boolean closeWaiting(Long entryId,LocalDateTime at);
    Usage usage(Long storeId,LocalDate day);
    boolean claim(Long entryId,String claimKey,LocalDateTime at);
    boolean failBeforeDispatch(Long entryId,String outcome,LocalDateTime at);
    boolean finish(Long entryId,String claimKey,String state,String outcome,LocalDateTime at);
    Optional<String> steps(Long entryId);
    boolean compareSteps(Long entryId,String claimKey,String expected,String next,LocalDateTime at);
}
