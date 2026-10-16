/**
 * 员工管理相关类型定义
 */

/**
 * 角色信息
 */
export interface RoleInfo {
  id: number
  name: string
  code: string
}

/**
 * 员工列表VO（后端返回）
 */
export interface StaffListVO {
  id: number
  code: string
  name: string
  phone?: string
  storeId: number
  storeName?: string
  status: number
  roles?: RoleInfo[]
}

/**
 * 员工档案VO（后端返回）
 */
export interface StaffProfileVO {
  id: number
  code: string
  name: string
  phone?: string
  storeId: number
  storeName?: string
  roles?: string[] // 角色编码列表
  roleIds?: number[] // 角色ID列表
  status: number
}

/**
 * 创建员工请求
 */
export interface StaffCreateRequest {
  code?: string
  name: string
  phone: string // 必填
  password: string
  storeId: number
  roleId: number // 单选
}

/**
 * 更新员工请求
 */
export interface StaffUpdateRequest {
  id: number // 员工ID（必填）
  code?: string
  name: string
  phone: string // 必填
  storeId: number
  roleId?: number // 仅真正更换角色时提交，缺省保留全部原关联
}

/**
 * 重置密码请求
 */
export interface StaffResetPasswordRequest {
  staffId: number
  newPassword: string
}

/**
 * 员工查询参数
 */
export interface StaffQueryParams {
  code?: string
  name?: string
  storeId?: number
  status?: number
  pageNum?: number
  pageSize?: number
}
