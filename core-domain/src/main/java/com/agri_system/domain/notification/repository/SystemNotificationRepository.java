package com.agri_system.domain.notification.repository;

import com.agri_system.domain.notification.model.SystemNotification;
import com.mybatisflex.core.paginate.Page;

import java.util.List;
import java.util.Optional;

/**
 * 系统通知仓储接口
 */
public interface SystemNotificationRepository {

    SystemNotification save(SystemNotification notification);

    Optional<SystemNotification> findById(Long id);

    /**
     * 查询指定员工的通知列表（包含全员通知 + 角色通知 + 个人通知）
     */
    Page<SystemNotification> findPageByStaff(Long staffId, Long storeId, String type, Integer isRead, int pageNum, int pageSize);

    /**
     * 统计未读通知数量
     */
    long countUnread(Long staffId, Long storeId);

    /**
     * 批量标记已读
     */
    void markAsRead(List<Long> ids);

    /**
     * 批量标记已读（仅限属于指定员工的通知，防止IDOR）
     */
    void markAsReadByStaff(List<Long> ids, Long staffId);
}
