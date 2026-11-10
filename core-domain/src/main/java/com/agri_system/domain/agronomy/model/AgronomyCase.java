package com.agri_system.domain.agronomy.model;

import java.time.LocalDateTime;

/** 同一问题是客户、门店与后续助手共用的事实锚，不是第二套客户或订单。 */
public record AgronomyCase(Long id, Long customerId, Long storeId, String requestKey, String requestHash,
        String question, Facts facts, String status, long version, long factsVersion, Long assignedStaffId,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record Facts(String region, String crop, String growthStage) {
        @Override public String toString() { return "CaseFacts[农情隐藏]"; }
    }
    @Override public String toString() { return "AgronomyCase[id=" + id + ",status=" + status + "]"; }
}
