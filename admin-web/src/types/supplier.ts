/**
 * 供应商管理相关类型定义
 */

/**
 * 供应商详情VO
 */
export interface SupplierDetailVO {
  id: number
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  bankAccount?: string
  bankName?: string
  address?: string
  status: number
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 创建供应商请求
 */
export interface SupplierCreateRequest {
  code?: string // 可选，后端自动生成
  name: string
  contactName?: string
  contactPhone?: string
  bankAccount?: string
  bankName?: string
  address?: string
  status?: number
  remark?: string
}

/**
 * 更新供应商请求
 */
export interface SupplierUpdateRequest {
  id: number
  name: string
  contactName?: string
  contactPhone?: string
  bankAccount?: string
  bankName?: string
  address?: string
  status?: number
  remark?: string
}

/**
 * 供应商查询参数
 */
export interface SupplierQueryParams {
  code?: string
  name?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

