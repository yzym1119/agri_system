/**
 * 字典管理相关类型定义
 */

/**
 * 字典类型
 */
export interface DictType {
  code: string
  name: string
  remark?: string
}

/**
 * 字典数据VO（后端返回）
 */
export interface DictDataVO {
  label: string
  value: string
  sortOrder: number
  isDefault?: number
}

/**
 * 字典数据（包含ID，用于管理）
 */
export interface DictData {
  id?: number
  typeCode: string
  label: string
  value: string
  sortOrder: number
  status: number
  isDefault: number
}

/**
 * 字典数据管理VO（后端返回，包含ID和状态）
 */
export interface DictDataManageVO {
  id: number
  typeCode: string
  label: string
  value: string
  sortOrder: number
  status: number
  isDefault: number
}

/**
 * 创建字典数据请求
 */
export interface DictDataCreateRequest {
  typeCode: string
  label: string
  value: string
  sortOrder?: number
  status?: number
  isDefault?: number
}

/**
 * 更新字典数据请求
 */
export interface DictDataUpdateRequest {
  id: number
  typeCode: string
  label: string
  value: string
  sortOrder: number
  status: number
  isDefault: number
}

