import {defineStore} from 'pinia'
import {ref} from 'vue'
import type {RouteRecordRaw} from 'vue-router'

export const usePermissionStore = defineStore(
  'permission',
  () => {
    // 路由列表
    const routes = ref<RouteRecordRaw[]>([])
    // 权限列表（预留，用于动态路由）
    const permissions = ref<string[]>([])

    // 设置路由
    const setRoutes = (newRoutes: RouteRecordRaw[]) => {
      routes.value = newRoutes
    }

    // 设置权限
    const setPermissions = (newPermissions: string[]) => {
      permissions.value = newPermissions
    }

    // 重置
    const reset = () => {
      routes.value = []
      permissions.value = []
    }

    return {
      routes,
      permissions,
      setRoutes,
      setPermissions,
      reset
    }
  },
  {
    persist: false // 权限信息不持久化
  }
)



