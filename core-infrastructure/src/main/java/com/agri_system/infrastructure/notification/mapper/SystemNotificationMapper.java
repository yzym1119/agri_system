package com.agri_system.infrastructure.notification.mapper;

import com.agri_system.domain.notification.model.SystemNotification;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemNotificationMapper extends BaseMapper<SystemNotification> {
}
