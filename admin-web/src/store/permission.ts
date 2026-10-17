import {defineStore} from 'pinia'
import {ref} from 'vue'
import type {RouteRecordRaw} from 'vue-router'
import { getCurrentMenuTree } from '@/api/menu'
import type { MenuTreeNode } from '@/types/role'

const legacyMenuPathMap: Record<string, string> = {
  '/inventory': '/inventory/list',
  '/purchase': '/inventory/purchase/order',
  '/customer/list': '/marketing/customer/list'
}

export const usePermissionStore = defineStore(
  'permission',
  () => {
    // 路由列表
    const routes = ref<RouteRecordRaw[]>([])
    // 权限列表（预留，用于动态路由）
    const permissions = ref<string[]>([])
    const menuTree = ref<MenuTreeNode[]>([])
    const routesLoaded = ref(false)

    // 设置路由
    const setRoutes = (newRoutes: RouteRecordRaw[]) => {
      routes.value = newRoutes
    }

    const setRoutesLoaded = (loaded: boolean) => {
      routesLoaded.value = loaded
    }

    // 设置权限
    const setPermissions = (newPermissions: string[]) => {
      permissions.value = newPermissions
    }

    const setMenuTree = (menus: MenuTreeNode[]) => {
      menuTree.value = normalizeMenuTree(menus || [])
    }

    const loadCurrentMenus = async (isCurrent: () => boolean = () => true) => {
      const menus = await getCurrentMenuTree()
      if (!isCurrent()) return []
      setMenuTree(menus || [])
      return menuTree.value
    }

    // 重置
    const reset = () => {
      routes.value = []
      permissions.value = []
      menuTree.value = []
      routesLoaded.value = false
    }

    return {
      routes,
      permissions,
      menuTree,
      routesLoaded,
      setRoutes,
      setRoutesLoaded,
      setPermissions,
      setMenuTree,
      loadCurrentMenus,
      reset
    }
  },
  {
    persist: false // 权限信息不持久化
  }
)

function normalizeMenuTree(nodes: MenuTreeNode[]): MenuTreeNode[] {
  return nodes.map(node => ({
    ...node,
    path: normalizeMenuPath(node.path),
    children: normalizeMenuTree(node.children || [])
  }))
}

function normalizeMenuPath(path?: string): string | undefined {
  if (!path) {
    return path
  }
  return legacyMenuPathMap[path] || path
}
