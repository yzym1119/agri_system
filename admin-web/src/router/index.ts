import type {RouteRecordRaw} from 'vue-router'
import {createRouter, createWebHistory} from 'vue-router'
import {useUserStore} from '@/store/user'
import {usePermissionStore} from '@/store/permission'
import {ElMessage} from 'element-plus'
import {findFirstAccessiblePath, hasPermission} from '@/utils/permission'
import { buildAppChildRoutes } from '@/router/registry'

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      hidden: true
    }
  },
  {
    path: '/',
    name: 'RootLayout',
    component: () => import('@/views/layout/index.vue'),
    meta: {
      hidden: true
    },
    children: []
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes
})

function ensureDynamicRoutes(permissionStore: ReturnType<typeof usePermissionStore>): boolean {
  if (permissionStore.routesLoaded) {
    return false
  }

  const allowedRoutes = buildAppChildRoutes(permissionStore.permissions, permissionStore.menuTree)
  allowedRoutes.forEach(route => {
    router.addRoute('RootLayout', route)
  })
  permissionStore.setRoutes(allowedRoutes)
  permissionStore.setRoutesLoaded(true)
  return true
}

function collectRouteNames(routes: RouteRecordRaw[]): string[] {
  return routes.flatMap(route => {
    const currentNames = typeof route.name === 'string' ? [route.name] : []
    const childNames = Array.isArray(route.children) ? collectRouteNames(route.children) : []
    return [...childNames, ...currentNames]
  })
}

export function resetDynamicRoutes(routes: RouteRecordRaw[] = []): void {
  collectRouteNames(routes).forEach(name => {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  })
}

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  // 白名单
  const whiteList = ['/login']
  if (whiteList.includes(to.path)) {
    next()
    return
  }

  // 检查 token
  if (!userStore.token) {
    next('/login')
    return
  }

  // 检查用户信息
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      ElMessage.error('获取用户信息失败')
      next('/login')
      return
    }
  }

  if (permissionStore.permissions.length === 0 && !userStore.userInfo?.permissions) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      ElMessage.error('获取权限信息失败')
      next('/login')
      return
    }
  }

  if (permissionStore.permissions.length === 0 && userStore.userInfo?.permissions?.length) {
    permissionStore.setPermissions(userStore.userInfo.permissions)
  }

  if (permissionStore.menuTree.length === 0) {
    try {
      await permissionStore.loadCurrentMenus()
    } catch (error) {
      console.warn('Load current menus failed, fallback to static route menu.', error)
    }
  }

  const dynamicRoutesLoaded = ensureDynamicRoutes(permissionStore)
  if (dynamicRoutesLoaded) {
    next({ ...to, replace: true })
    return
  }

  if (to.path === '/') {
    next(findFirstAccessiblePath(permissionStore.routes, permissionStore.permissions, permissionStore.menuTree))
    return
  }

  if (to.matched.length === 0) {
    next(findFirstAccessiblePath(permissionStore.routes, permissionStore.permissions, permissionStore.menuTree))
    return
  }

  const requiredPermissions = to.meta?.permissions as string[] | undefined
  if (!hasPermission(requiredPermissions, permissionStore.permissions)) {
    ElMessage.error('没有权限访问该页面')
    next(findFirstAccessiblePath(permissionStore.routes, permissionStore.permissions, permissionStore.menuTree))
    return
  }

  next()
})

export default router
