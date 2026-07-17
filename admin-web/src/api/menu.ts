import request from '@/utils/request'
import type { MenuTreeNode } from '@/types/role'

export function getCurrentMenuTree(): Promise<MenuTreeNode[]> {
  return request({
    url: '/api/admin/menus/current',
    method: 'get'
  })
}
