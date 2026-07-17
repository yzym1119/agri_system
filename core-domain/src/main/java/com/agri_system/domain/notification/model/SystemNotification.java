package com.agri_system.domain.notification.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知实体
 */
@Data
@Table("system_notification")
public class SystemNotification {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 类型：stock_warning, order_event, approval, system, commission */
    private String type;

    /** 目标类型：staff, role, all */
    private String targetType;

    /** 目标ID */
    private Long targetId;

    /** 是否已读：1-已读 0-未读 */
    private Integer isRead;

    /** 阅读时间 */
    private LocalDateTime readAt;

    /** 附加数据JSON */
    private String extraData;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public boolean isUnread() {
        return isRead == null || isRead == 0;
    }

    public void markAsRead() {
        this.isRead = 1;
        this.readAt = LocalDateTime.now();
    }

    /**
     * 创建库存预警通知
     */
    public static SystemNotification stockWarning(String productName, String currentStock, String minStock) {
        SystemNotification n = new SystemNotification();
        n.setTitle("库存预警：" + productName);
        n.setContent("商品【" + productName + "】当前库存 " + currentStock + "，低于安全库存 " + minStock + "，请及时补货。");
        n.setType("stock_warning");
        n.setTargetType("all");
        n.setIsRead(0);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }

    /**
     * 创建提成通知
     */
    public static SystemNotification commissionNotify(Long staffId, String month, String amount) {
        SystemNotification n = new SystemNotification();
        n.setTitle(month + " 提成已结算");
        n.setContent("您 " + month + " 的提成已结算，金额：¥" + amount);
        n.setType("commission");
        n.setTargetType("staff");
        n.setTargetId(staffId);
        n.setIsRead(0);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}
