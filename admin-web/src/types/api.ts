/**
 * API 响应基础结构
 * 注意：后端返回的 code 可能是 int 或 string，需要兼容处理
 */
export interface ApiResponse<T = any> {
  code: number | string
  message: string
  data: T
  timestamp?: number
  traceId?: string
}

/**
 * 登录请求参数
 */
export interface LoginRequest {
  code: string
  password: string
}

/**
 * 登录响应数据
 */
export interface LoginResponse {
  tokenName: string
  tokenValue: string
  userInfo: UserInfo
}

export interface UserExtra {
  code?: string
  phone?: string
  storeId?: number
  roleCodes?: string[]
  [key: string]: any
}

/**
 * 用户信息
 */
export interface UserInfo {
  id: number
  name: string
  role?: string
  permissions?: string[]
  extra?: UserExtra
}

/**
 * 分页请求参数
 */
export interface PageRequest {
  pageNum?: number
  pageSize?: number
}

/**
 * 分页响应数据
 */
export interface PageResponse<T = any> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}
