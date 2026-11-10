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

let navigationRequest = 0

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const navigation = ++navigationRequest
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

  // 登录资料可持久化，但权限视图必须按本次进入页面时的真实身份刷新。
  const requestToken = userStore.token
  const isCurrent = () => navigation === navigationRequest && userStore.token === requestToken
  const previousAuthority = JSON.stringify([permissionStore.permissions, permissionStore.menuTree])
  try {
    await userStore.fetchUserInfo()
    if (!isCurrent()) { next(false); return }
    await permissionStore.loadCurrentMenus(isCurrent)
    if (!isCurrent()) { next(false); return }
  } catch (error) {
    // 当前查询可能已自行清理失效令牌；只有被新导航或新登录替代时才放弃。
    if (navigation !== navigationRequest || (userStore.token && userStore.token !== requestToken)) { next(false); return }
    userStore.reset()
    ElMessage.error('读取当前权限失败，请重新登录')
    next('/login')
    return
  }
  if (previousAuthority !== JSON.stringify([permissionStore.permissions, permissionStore.menuTree])) {
    resetDynamicRoutes(permissionStore.routes)
    permissionStore.setRoutesLoaded(false)
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
  if (!hasPermission(requiredPermissions, permissionStore.permissions, to.meta?.permissionMode as 'all' | undefined)) {
    ElMessage.error('没有权限访问该页面')
    next(findFirstAccessiblePath(permissionStore.routes, permissionStore.permissions, permissionStore.menuTree))
    return
  }

  next()
})

export default router
