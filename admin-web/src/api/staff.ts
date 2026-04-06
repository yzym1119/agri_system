import request from '@/utils/request'
import type {
    StaffCreateRequest,
    StaffListVO,
    StaffProfileVO,
    StaffQueryParams,
    StaffResetPasswordRequest,
    StaffUpdateRequest
} from '@/types/staff'
import type {PageResponse} from '@/types/store'

/**
 * 员工管理API
 */

/**
 * 分页查询员工列表
 */
export function getStaffList(params: StaffQueryParams): Promise<PageResponse<StaffListVO>> {
  return request({
    url: '/api/admin/staff',
    method: 'get',
    params
  })
}

/**
 * 获取员工档案
 */
export function getStaffProfile(id: number): Promise<StaffProfileVO> {
  return request({
    url: `/api/admin/staff/${id}/profile`,
    method: 'get'
  })
}

/**
 * 创建员工
 */
export function createStaff(data: StaffCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/staff',
    method: 'post',
    data
  })
}

/**
 * 更新员工信息
 */
export function updateStaff(id: number, data: StaffUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/staff/${id}`,
    method: 'put',
    data
  })
}

/**
 * 禁用员工
 */
export function disableStaff(id: number): Promise<void> {
  return request({
    url: `/api/admin/staff/${id}/disable`,
    method: 'post'
  })
}

/**
 * 启用员工
 */
export function enableStaff(id: number): Promise<void> {
  return request({
    url: `/api/admin/staff/${id}/enable`,
    method: 'post'
  })
}

/**
 * 重置员工密码
 */
export function resetStaffPassword(data: StaffResetPasswordRequest): Promise<void> {
  return request({
    url: '/api/admin/staff/reset-password',
    method: 'post',
    data
  })
}

