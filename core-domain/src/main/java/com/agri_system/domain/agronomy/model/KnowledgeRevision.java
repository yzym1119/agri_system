package com.agri_system.domain.agronomy.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 正文与范围创建后不可覆盖；审核与发布只改变同一版本的生命周期。 */
public record KnowledgeRevision(Long id,String logicalKey,int revision,Long storeId,Content content,String contentHash,
        String state,long version,Long createdBy,LocalDateTime createdAt,Long reviewedBy,LocalDateTime reviewedAt,
        LocalDateTime publishedAt,LocalDateTime withdrawnAt,String requestKey,String requestHash) {
    public record Content(String title,String summary,String crop,String region,String growthStage,String appliesTo,
            String exclusions,String body,String sourceText,String audience,String riskLevel,LocalDate effectiveFrom,LocalDate effectiveUntil) {
        @Override public String toString(){return "KnowledgeContent[知识正文与来源隐藏]";}
    }
}
