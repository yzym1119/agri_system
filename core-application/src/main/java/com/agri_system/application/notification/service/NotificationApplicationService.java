package com.agri_system.application.notification.service;

import com.agri_system.domain.notification.model.SystemNotification;
import com.agri_system.domain.notification.repository.SystemNotificationRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内信应用服务
 */
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private final SystemNotificationRepository notificationRepository;

    /**
     * 分页查询员工通知列表
     */
    public Page<SystemNotification> listNotifications(Long staffId, Long storeId, String type, Integer isRead, int pageNum, int pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 200);
        return notificationRepository.findPageByStaff(staffId, storeId, type, isRead, pageNum, pageSize);
    }

    /**
     * 获取未读通知数量
     */
    public long getUnreadCount(Long staffId, Long storeId) {
        return notificationRepository.countUnread(staffId, storeId);
    }

    /**
     * 批量标记已读（仅限本人通知）
     */
    public void markAsRead(List<Long> ids, Long staffId) {
        if (ids == null || ids.isEmpty()) return;
        if (ids.size() > 200) {
            ids = ids.subList(0, 200);
        }
        notificationRepository.markAsReadByStaff(ids, staffId);
    }

    /**
     * 标记全部已读
     */
    public void markAllAsRead(Long staffId, Long storeId) {
        Page<SystemNotification> unread = notificationRepository.findPageByStaff(staffId, storeId, null, 0, 1, 500);
        List<Long> ids = unread.getRecords().stream()
                .map(SystemNotification::getId)
                .toList();
        if (!ids.isEmpty()) {
            notificationRepository.markAsRead(ids);
        }
    }
}
