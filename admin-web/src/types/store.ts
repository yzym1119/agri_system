/**
 * 门店管理相关类型定义
 */

/**
 * 门店详情VO（后端返回）
 */
export interface StoreDetailVO {
  id: number
  code: string
  name: string
  phone?: string
  address?: string
  managerId?: number
  managerName?: string
  supportPickup: number
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建门店请求
 */
export interface StoreCreateRequest {
  code?: string // 可选，为空时后端自动生成
  name: string
  phone?: string
  address?: string
  managerId?: number
  supportPickup: number
  status: number
  remark?: string
}

/**
 * 更新门店请求
 */
export interface StoreUpdateRequest {
  id: number // 门店ID（必填）
  name: string
  phone?: string
  address?: string
  managerId?: number
  supportPickup: number
  remark?: string
}

/**
 * 门店查询参数
 */
export interface StoreQueryParams {
  code?: string
  name?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 分页响应
 */
export interface PageResponse<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

