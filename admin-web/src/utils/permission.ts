import type { RouteRecordRaw } from 'vue-router'
import type { MenuTreeNode } from '@/types/role'

export interface SidebarMenuItem {
  key: string
  path?: string
  title: string
  icon?: string
  order: number
  children?: SidebarMenuItem[]
}

interface RoutableMenuMeta {
  path: string
  name: string
  icon?: string
  sortOrder?: number
  visible?: number
  permissionSet: Set<string>
}

export function hasPermission(required: string | string[] | undefined, owned: string[] = []): boolean {
  if (!required) return true
  const requiredList = Array.isArray(required) ? required : [required]
  if (requiredList.length === 0) return true
  return requiredList.some(permission => owned.includes(permission))
}

export function filterRoutesByPermissions(routes: RouteRecordRaw[], owned: string[]): RouteRecordRaw[] {
  return routes.reduce<RouteRecordRaw[]>((acc, route) => {
    const clonedRoute: RouteRecordRaw = { ...route }
    const originalChildren = Array.isArray(route.children) ? route.children : []
    const filteredChildren = originalChildren.length > 0 ? filterRoutesByPermissions(originalChildren, owned) : []

    if (filteredChildren.length > 0) {
      clonedRoute.children = filteredChildren
    } else if (originalChildren.length > 0) {
      delete clonedRoute.children
    }

    const routePermissions = (route.meta as any)?.permissions as string[] | undefined
    const allowed = hasPermission(routePermissions, owned)
    const keepRoute = allowed || filteredChildren.length > 0 || route.path === '/'

    if (keepRoute) {
      acc.push(clonedRoute)
    }
    return acc
  }, [])
}

export function buildSidebarMenus(
  routes: RouteRecordRaw[],
  owned: string[],
  menuTree: MenuTreeNode[] = []
): SidebarMenuItem[] {
  const filtered = filterRoutesByPermissions(routes, owned).filter(route => !(route.meta as any)?.hidden)
  const routeMenus = collectRoutableMenus(menuTree)

  const convert = (list: RouteRecordRaw[], parentPath = ''): SidebarMenuItem[] =>
    list
      .map((route, index): SidebarMenuItem | null => {
        const fullPath = resolveRoutePath(route, parentPath)
        const matchedMenu = findMatchedMenu(route, fullPath, routeMenus)
        if (matchedMenu?.visible === 0) {
          return null
        }

        const children = Array.isArray(route.children) ? convert(route.children, fullPath) : []
        const title = matchedMenu?.name || ((route.meta as any)?.title as string | undefined) || String(route.name || fullPath)
        const icon = matchedMenu?.icon || ((route.meta as any)?.icon as string | undefined)
        const order = matchedMenu?.sortOrder ?? index

        if (children.length > 0) {
          return {
            key: fullPath,
            title,
            icon,
            order,
            children: sortSidebarMenus(children)
          }
        }

        if (route.component && fullPath !== '/') {
          return {
            key: fullPath,
            path: fullPath,
            title,
            icon,
            order
          }
        }

        return null
      })
      .filter((item): item is SidebarMenuItem => item !== null)

  return sortSidebarMenus(convert(filtered))
}

export function findFirstAccessiblePath(
  routes: RouteRecordRaw[],
  owned: string[],
  menuTree: MenuTreeNode[] = []
): string {
  const sidebarMenus = buildSidebarMenus(routes, owned, menuTree)
  // 从真实可访问菜单选择高频入口，不依据旧角色名称猜权限。
  const paths = new Set<string>()
  const collect = (items: SidebarMenuItem[]) => items.forEach(item => {
    if (item.path) paths.add(item.path)
    if (item.children) collect(item.children)
  })
  collect(sidebarMenus)
  const preferredPath = ['/dashboard', '/pos', '/pickup', '/order', '/inventory'].find(path => paths.has(path))
  const firstMenuPath = preferredPath || findFirstSidebarPath(sidebarMenus)
  if (firstMenuPath) {
    return firstMenuPath
  }

  const filtered = filterRoutesByPermissions(routes, owned)

  const visit = (list: RouteRecordRaw[], parentPath = ''): string | null => {
    for (const route of list) {
      const path = resolveRoutePath(route, parentPath)

      if (route.children && route.children.length > 0) {
        const childPath = visit(route.children, path)
        if (childPath) return childPath
      }

      if (route.component && path !== '/') {
        return path
      }
    }
    return null
  }

  return visit(filtered, '') || '/login'
}

function resolveRoutePath(route: RouteRecordRaw, parentPath = ''): string {
  const rawPath = route.path || ''
  if (rawPath.startsWith('/')) {
    return rawPath
  }
  if (!parentPath || parentPath === '/') {
    return `/${rawPath}`.replace(/\/+/g, '/')
  }
  return `${parentPath}/${rawPath}`.replace(/\/+/g, '/')
}

function collectRoutableMenus(menuTree: MenuTreeNode[]): RoutableMenuMeta[] {
  const result: RoutableMenuMeta[] = []

  const visit = (node: MenuTreeNode): Set<string> => {
    const permissionSet = new Set<string>()
    if (node.permission) {
      permissionSet.add(node.permission)
    }
    node.children?.forEach(child => {
      visit(child).forEach(permission => permissionSet.add(permission))
    })

    if (node.path) {
      result.push({
        path: node.path,
        name: node.name,
        icon: node.icon,
        sortOrder: node.sortOrder,
        visible: node.visible,
        permissionSet
      })
    }

    return permissionSet
  }

  ;(menuTree || []).forEach(visit)
  return result
}

function findMatchedMenu(
  route: RouteRecordRaw,
  fullPath: string,
  routeMenus: RoutableMenuMeta[]
): RoutableMenuMeta | undefined {
  // 优先使用 meta 中显式指定的 menuPath（用于父级分组精确匹配）
  const metaMenuPath = (route.meta as any)?.menuPath as string | undefined
  if (metaMenuPath) {
    const exactByMenuPath = routeMenus.find(menu => menu.path === metaMenuPath)
    if (exactByMenuPath) {
      return exactByMenuPath
    }
  }

  // 精确路径匹配，优先返回可见的菜单节点
  const exactMenus = routeMenus.filter(menu => menu.path === fullPath)
  if (exactMenus.length > 0) {
    return exactMenus.find(menu => menu.visible !== 0) || exactMenus[0]
  }

  const requiredPermissions = normalizePermissions((route.meta as any)?.permissions as string[] | undefined)
  if (requiredPermissions.length === 0) {
    return undefined
  }

  return routeMenus.find(menu =>
    requiredPermissions.some(permission => menu.permissionSet.has(permission))
  )
}

function normalizePermissions(required: string | string[] | undefined): string[] {
  if (!required) {
    return []
  }
  return Array.isArray(required) ? required.filter(Boolean) : [required]
}

function sortSidebarMenus(items: SidebarMenuItem[]): SidebarMenuItem[] {
  return [...items].sort((a, b) => a.order - b.order)
}

function findFirstSidebarPath(items: SidebarMenuItem[]): string | null {
  for (const item of items) {
    if (item.children?.length) {
      const childPath = findFirstSidebarPath(item.children)
      if (childPath) {
        return childPath
      }
    }
    if (item.path) {
      return item.path
    }
  }
  return null
}
