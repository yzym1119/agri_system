package com.agri_system.domain.agronomy.model;

import java.time.LocalDateTime;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;

/** 不可覆盖的原话、补充、人工请求与答复，同时保留原操作的不可变收据。 */
public record AgronomyCaseEntry(Long id, Long caseId, String actorType, Long actorId,
        String requestKey, String requestHash, String type, String body, String sourceText, Facts facts,
        Long assignedStaffId, long expectedVersion, long resultVersion, long resultFactsVersion,
        String resultStatus, LocalDateTime createdAt) {
    @Override public String toString() { return "AgronomyCaseEntry[id=" + id + ",type=" + type + "]"; }
}
