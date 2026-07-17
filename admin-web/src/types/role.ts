/**
 * 角色管理相关类型定义
 */

/**
 * 角色VO（后端返回）
 */
export interface RoleVO {
  id: number
  name: string
  code: string
  status?: number
}

/**
 * 创建角色请求
 */
export interface RoleCreateRequest {
  name: string
  code?: string // 可选，为空时后端自动生成
  status?: number
  menuIds?: number[]
}

/**
 * 更新角色请求
 */
export interface RoleUpdateRequest {
  name: string
  code?: string
  status?: number
  menuIds?: number[]
}

/**
 * 菜单树节点
 */
export interface MenuTreeNode {
  id: number
  name: string
  permission?: string
  type?: number
  parentId?: number
  path?: string
  icon?: string
  sortOrder?: number
  visible?: number
  children?: MenuTreeNode[]
}
