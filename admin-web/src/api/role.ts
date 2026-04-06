import request from '@/utils/request'
import type {MenuTreeNode, RoleCreateRequest, RoleUpdateRequest, RoleVO} from '@/types/role'

/**
 * 角色管理API
 */

/**
 * 获取角色列表
 */
export function getRoleList(): Promise<RoleVO[]> {
  return request({
    url: '/api/admin/roles',
    method: 'get'
  })
}

/**
 * 创建角色
 */
export function createRole(data: RoleCreateRequest): Promise<{ id: number }> {
  return request({
    url: '/api/admin/roles',
    method: 'post',
    data
  })
}

/**
 * 更新角色
 */
export function updateRole(id: number, data: RoleUpdateRequest): Promise<void> {
  return request({
    url: `/api/admin/roles/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除角色
 */
export function deleteRole(id: number): Promise<void> {
  return request({
    url: `/api/admin/roles/${id}`,
    method: 'delete'
  })
}

/**
 * 分配角色权限
 */
export function assignRoleMenus(roleId: number, menuIds: number[]): Promise<void> {
  return request({
    url: `/api/admin/roles/${roleId}/menus`,
    method: 'post',
    data: { 
      roleId, // 必须在请求体中包含roleId
      menuIds 
    }
  })
}

/**
 * 获取菜单树
 */
export function getMenuTree(): Promise<MenuTreeNode[]> {
  return request({
    url: '/api/admin/menus/tree',
    method: 'get'
  })
}

/**
 * 获取角色已分配的菜单ID列表
 */
export function getRoleMenus(roleId: number): Promise<number[]> {
  return request({
    url: `/api/admin/roles/${roleId}/menus`,
    method: 'get'
  })
}




