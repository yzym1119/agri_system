package com.agri_system.application.customer.job;

import com.agri_system.application.customer.service.VipLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * VIP降级定时任务测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class VipDowngradeJobTest {

    @Mock
    private VipLifecycleService vipLifecycleService;

    @InjectMocks
    private VipDowngradeJob vipDowngradeJob;

    /**
     * 测试用例1：正常执行降级任务
     */
    @Test
    void testExecuteDowngrade_Success() {
        // Given
        doNothing().when(vipLifecycleService).downgradeExpiredVips();

        // When
        vipDowngradeJob.executeDowngrade();

        // Then
        // 验证：调用了降级服务
        verify(vipLifecycleService, times(1)).downgradeExpiredVips();
    }

    /**
     * 测试用例2：降级服务抛出异常
     */
    @Test
    void testExecuteDowngrade_ServiceThrowsException() {
        // Given
        doThrow(new RuntimeException("降级失败")).when(vipLifecycleService).downgradeExpiredVips();

        // When & Then
        assertThrows(RuntimeException.class, () -> vipDowngradeJob.executeDowngrade());
        verify(vipLifecycleService, times(1)).downgradeExpiredVips();
    }

    /**
     * 测试用例3：多次执行
     */
    @Test
    void testExecuteDowngrade_MultipleExecutions() {
        // Given
        doNothing().when(vipLifecycleService).downgradeExpiredVips();

        // When
        vipDowngradeJob.executeDowngrade();
        vipDowngradeJob.executeDowngrade();
        vipDowngradeJob.executeDowngrade();

        // Then
        // 验证：每次执行都调用了降级服务
        verify(vipLifecycleService, times(3)).downgradeExpiredVips();
    }
}

