package com.agri_system.application.notification.service;

import com.agri_system.domain.notification.model.SystemNotification;
import com.agri_system.domain.notification.repository.SystemNotificationRepository;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 站内信应用服务测试
 */
@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private SystemNotificationRepository notificationRepository;

    @InjectMocks
    private NotificationApplicationService service;

    @Test
    void listNotifications_shouldCapPageSizeAt200() {
        // Given
        Page<SystemNotification> emptyPage = new Page<>();
        when(notificationRepository.findPageByStaff(eq(1L), any(), any(), any(), eq(1), eq(200)))
                .thenReturn(emptyPage);

        // When: request 500 per page
        Page<SystemNotification> result = service.listNotifications(1L, null, null, null, 1, 500);

        // Then: capped to 200
        assertNotNull(result);
        verify(notificationRepository).findPageByStaff(eq(1L), any(), any(), any(), eq(1), eq(200));
    }

    @Test
    void listNotifications_shouldFloorPageSizeAt1() {
        // Given
        Page<SystemNotification> emptyPage = new Page<>();
        when(notificationRepository.findPageByStaff(eq(1L), any(), any(), any(), eq(1), eq(1)))
                .thenReturn(emptyPage);

        // When: request 0 per page
        Page<SystemNotification> result = service.listNotifications(1L, null, null, null, 1, 0);

        // Then: floored to 1
        verify(notificationRepository).findPageByStaff(eq(1L), any(), any(), any(), eq(1), eq(1));
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(notificationRepository.countUnread(1L, null)).thenReturn(5L);

        long count = service.getUnreadCount(1L, null);

        assertEquals(5L, count);
    }

    @Test
    void markAsRead_shouldDoNothingForEmptyList() {
        service.markAsRead(List.of(), 1L);

        verify(notificationRepository, never()).markAsReadByStaff(anyList(), anyLong());
    }

    @Test
    void markAsRead_shouldDoNothingForNullList() {
        service.markAsRead(null, 1L);

        verify(notificationRepository, never()).markAsReadByStaff(anyList(), anyLong());
    }

    @Test
    void markAsRead_shouldTruncateAt200() {
        // Given: 250 IDs
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 1; i <= 250; i++) ids.add(i);

        // When
        service.markAsRead(ids, 1L);

        // Then: only first 200 passed
        verify(notificationRepository).markAsReadByStaff(argThat(list -> list.size() == 200), eq(1L));
    }

    @Test
    void markAsRead_shouldPassValidListAsIs() {
        List<Long> ids = List.of(1L, 2L, 3L);

        service.markAsRead(ids, 1L);

        verify(notificationRepository).markAsReadByStaff(eq(ids), eq(1L));
    }

    @Test
    void markAllAsRead_shouldMarkAllUnreadNotifications() {
        // Given: 3 unread notifications
        SystemNotification n1 = new SystemNotification();
        n1.setId(1L);
        SystemNotification n2 = new SystemNotification();
        n2.setId(2L);
        SystemNotification n3 = new SystemNotification();
        n3.setId(3L);

        Page<SystemNotification> page = new Page<>();
        page.setRecords(List.of(n1, n2, n3));

        when(notificationRepository.findPageByStaff(eq(1L), any(), any(), eq(0), eq(1), eq(500)))
                .thenReturn(page);

        // When
        service.markAllAsRead(1L, null);

        // Then
        verify(notificationRepository).markAsRead(eq(List.of(1L, 2L, 3L)));
    }

    @Test
    void markAllAsRead_shouldDoNothingWhenNoUnread() {
        Page<SystemNotification> emptyPage = new Page<>();
        emptyPage.setRecords(List.of());

        when(notificationRepository.findPageByStaff(eq(1L), any(), any(), eq(0), eq(1), eq(500)))
                .thenReturn(emptyPage);

        service.markAllAsRead(1L, null);

        verify(notificationRepository, never()).markAsRead(anyList());
    }

    @Test
    void listNotifications_shouldPassTypeAndReadFilter() {
        Page<SystemNotification> emptyPage = new Page<>();
        when(notificationRepository.findPageByStaff(eq(1L), eq(100L), eq("STOCK_WARNING"), eq(0), eq(1), eq(20)))
                .thenReturn(emptyPage);

        service.listNotifications(1L, 100L, "STOCK_WARNING", 0, 1, 20);

        verify(notificationRepository).findPageByStaff(1L, 100L, "STOCK_WARNING", 0, 1, 20);
    }
}
