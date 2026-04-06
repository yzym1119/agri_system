import request from '@/utils/request'
import type {LoginRequest, LoginResponse, UserInfo} from '@/types/api'

/**
 * 员工登录
 * 注意：后端直接返回 LoginResultVO，不是包装在 R 中
 * 后端接口使用 account 字段（可以是编码或手机号），前端使用 code 字段
 */
export function staffLogin(data: LoginRequest): Promise<LoginResponse> {
  return request({
    url: '/auth/staff/login',
    method: 'post',
    // 将前端的 code 字段映射为后端的 account 字段
    data: {
      account: data.code,
      password: data.password,
      loginType: 'PASSWORD'
    }
  })
}

/**
 * 员工登出
 */
export function staffLogout(): Promise<void> {
  return request({
    url: '/auth/staff/logout',
    method: 'post'
  })
}

/**
 * 获取当前员工信息
 * 注意：后端直接返回 UserInfo，不是包装在 R 中
 */
export function getStaffInfo(): Promise<UserInfo> {
  return request({
    url: '/auth/staff/info',
    method: 'get'
  })
}

