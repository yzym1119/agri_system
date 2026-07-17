import request from '@/utils/request'

export interface NotificationItem {
  id: number
  type: string
  title: string
  content: string
  isRead: number
  createdAt?: string
}

export interface NotificationPageResult {
  records: NotificationItem[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * 查询我的通知列表
 */
export function getMyNotifications(params: {
  type?: string
  isRead?: number
  pageNum?: number
  pageSize?: number
}): Promise<NotificationPageResult> {
  return request({
    url: '/api/admin/notifications',
    method: 'get',
    params
  }).then((page: any) => ({
    records: page?.records || [],
    total: page?.total ?? page?.totalRow ?? 0,
    pageNum: page?.pageNumber ?? params.pageNum ?? 1,
    pageSize: page?.pageSize ?? params.pageSize ?? 20
  }))
}

/**
 * 获取未读通知数量
 */
export function getUnreadCount() {
  return request({
    url: '/api/admin/notifications/unread-count',
    method: 'get'
  })
}

/**
 * 批量标记已读
 */
export function markAsRead(ids: number[]) {
  return request({
    url: '/api/admin/notifications/read',
    method: 'put',
    data: ids
  })
}

/**
 * 标记所有通知已读
 */
export function markAllAsRead() {
  return request({
    url: '/api/admin/notifications/read-all',
    method: 'put'
  })
}
