package com.agri_system.domain.agronomy.assistant;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 仅处理元数据可CAS推进；entry原文、原请求摘要与原收据不可改。 */
public record AssistantRequestMetadata(Long entryId, String state, String snapshot, String outcome,
        LocalDate budgetDay, long modelReservedMicrousd, long searchReservedMicrousd, String claimKey, LocalDateTime updatedAt, LocalDateTime closedAt) {
    public AssistantRequestMetadata(Long entryId,String state,String snapshot,String outcome,LocalDate budgetDay,long modelReservedMicrousd,long searchReservedMicrousd,String claimKey,LocalDateTime updatedAt) {
        this(entryId,state,snapshot,outcome,budgetDay,modelReservedMicrousd,searchReservedMicrousd,claimKey,updatedAt,null);
    }
    @Override public String toString() { return "AssistantMetadata[id="+entryId+",state="+state+"]"; }
}
