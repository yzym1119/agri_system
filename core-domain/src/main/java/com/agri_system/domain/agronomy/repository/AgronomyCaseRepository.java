package com.agri_system.domain.agronomy.repository;

import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.model.AgronomyCaseEntry;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgronomyCaseRepository {
    Optional<AgronomyCase> find(Long id, boolean lock);
    Optional<AgronomyCase> findOwned(Long id, Long customerId, boolean lock);
    Optional<AgronomyCase> findCreation(Long customerId, String requestKey);
    boolean insertCase(AgronomyCase value);
    Optional<AgronomyCaseEntry> findRequest(String actorType, Long actorId, String requestKey);
    boolean insertEntry(AgronomyCaseEntry value);
    boolean update(Long id, long expectedVersion, Facts facts, String status,
                   long factsVersion, Long assignedStaffId, LocalDateTime at);
    List<AgronomyCase> page(Long customerId, Long storeId, Long assignedStaffId, String status, Long before);
    List<AgronomyCaseEntry> entries(Long caseId, boolean customerVisible, long snapshotVersion, Long before);
    Optional<AgronomyCaseEntry> latestHumanReply(Long caseId, long snapshotVersion);
}
