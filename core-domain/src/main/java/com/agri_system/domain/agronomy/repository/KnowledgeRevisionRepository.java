package com.agri_system.domain.agronomy.repository;

import com.agri_system.domain.agronomy.model.KnowledgeRevision;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KnowledgeRevisionRepository {
    record Scope(String region,String crop,String growthStage) {}
    Optional<KnowledgeRevision> find(String logicalKey,int revision,boolean lock);
    Optional<KnowledgeRevision> findSubmission(Long actorId,String requestKey,boolean lock);
    int latestRevision(String logicalKey);
    int latestPublishedRevision(String logicalKey);
    boolean insertIfAbsent(KnowledgeRevision revision);
    List<KnowledgeRevision> published(String logicalKey);
    boolean changeState(Long id,long expectedVersion,String state,Long actorId,LocalDateTime now);
    List<KnowledgeRevision> page(Long storeId,Long before);
    List<Scope> publicScopes(LocalDate today);
    List<KnowledgeRevision> publicPage(Scope scope,LocalDate today,Long after,String keyword);
    Optional<KnowledgeRevision> publicCurrent(String logicalKey,Scope scope,LocalDate today);
}
