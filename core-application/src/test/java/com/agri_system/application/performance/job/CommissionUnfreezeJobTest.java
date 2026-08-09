package com.agri_system.application.performance.job;

import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;

class CommissionUnfreezeJobTest {
    @Test
    void unfreezeDueDelegatesToRepository() {
        StaffPerformanceLogRepository repo = mock(StaffPerformanceLogRepository.class);
        when(repo.unfreezeDue(any())).thenReturn(12);

        CommissionUnfreezeJob job = new CommissionUnfreezeJob(repo);
        job.unfreezeDue();

        verify(repo).unfreezeDue(any(LocalDateTime.class));
    }
}
