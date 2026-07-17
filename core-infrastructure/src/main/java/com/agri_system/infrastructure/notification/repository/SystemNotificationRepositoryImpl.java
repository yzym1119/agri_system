package com.agri_system.infrastructure.notification.repository;

import com.agri_system.domain.notification.model.SystemNotification;
import com.agri_system.domain.notification.repository.SystemNotificationRepository;
import com.agri_system.infrastructure.notification.mapper.SystemNotificationMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SystemNotificationRepositoryImpl implements SystemNotificationRepository {

    private final SystemNotificationMapper mapper;

    @Override
    public SystemNotification save(SystemNotification notification) {
        if (notification.getId() == null) {
            mapper.insert(notification);
        } else {
            mapper.update(notification);
        }
        return notification;
    }

    @Override
    public Optional<SystemNotification> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public Page<SystemNotification> findPageByStaff(Long staffId, Long storeId, String type, Integer isRead, int pageNum, int pageSize) {
        // 查询条件：全员通知 OR 角色通知（此处简化为门店级别） OR 个人通知
        QueryWrapper query = QueryWrapper.create()
                .where("(target_type = 'all')")
                .or("(target_type = 'staff' AND target_id = ?)", staffId)
                .orderBy("created_at DESC");

        if (type != null && !type.isEmpty()) {
            query.and("type = ?", type);
        }
        if (isRead != null) {
            query.and("is_read = ?", isRead);
        }

        return mapper.paginate(new Page<>(pageNum, pageSize), query);
    }

    @Override
    public long countUnread(Long staffId, Long storeId) {
        QueryWrapper query = QueryWrapper.create()
                .where("is_read = 0")
                .and("((target_type = 'all') OR (target_type = 'staff' AND target_id = ?))", staffId);
        return mapper.selectCountByQuery(query);
    }

    @Override
    public void markAsRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        SystemNotification update = new SystemNotification();
        update.setIsRead(1);
        update.setReadAt(java.time.LocalDateTime.now());
        // Long IDs are safe for direct concatenation (no SQL injection risk)
        String idList = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        QueryWrapper query = QueryWrapper.create().where("id IN (" + idList + ")");
        mapper.updateByQuery(update, query);
    }

    @Override
    public void markAsReadByStaff(List<Long> ids, Long staffId) {
        if (ids == null || ids.isEmpty()) return;
        SystemNotification update = new SystemNotification();
        update.setIsRead(1);
        update.setReadAt(java.time.LocalDateTime.now());
        String idList = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        // 安全：仅标记属于该员工的通知（全员通知 OR 指定该员工的通知）
        QueryWrapper query = QueryWrapper.create()
                .where("id IN (" + idList + ")")
                .and("((target_type = 'all') OR (target_type = 'staff' AND target_id = ?))", staffId);
        mapper.updateByQuery(update, query);
    }
}
