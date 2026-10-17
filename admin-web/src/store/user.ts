import {defineStore} from 'pinia'
import {ref} from 'vue'
import {getStaffInfo, staffLogin, staffLogout} from '@/api/auth'
import type {LoginRequest, UserInfo} from '@/types/api'
import router, { resetDynamicRoutes } from '@/router'
import {usePermissionStore} from '@/store/permission'

export const useUserStore = defineStore(
  'user',
  () => {
    // 状态
    const token = ref<string>('')
    const userInfo = ref<UserInfo | null>(null)
    let infoRequest = 0

    // 登录
    const login = async (loginForm: LoginRequest) => {
      const permissionStore = usePermissionStore()
      const response = await staffLogin(loginForm)
      token.value = response.tokenValue
      userInfo.value = response.userInfo
      permissionStore.setPermissions(response.userInfo?.permissions || [])
      return response
    }

    // 登出
    const logout = async () => {
      const permissionStore = usePermissionStore()
      try {
        await staffLogout()
      } catch (error) {
        console.error('Logout error:', error)
      } finally {
        resetDynamicRoutes(permissionStore.routes)
        token.value = ''
        userInfo.value = null
        permissionStore.reset()
        router.push('/login')
      }
    }

    // 获取用户信息
    const fetchUserInfo = async () => {
      const permissionStore = usePermissionStore()
      if (!token.value) {
        throw new Error('No token available')
      }
      const requestToken = token.value
      const request = ++infoRequest
      const isCurrent = () => token.value === requestToken && request === infoRequest
      try {
        const info = await getStaffInfo()
        if (!isCurrent()) throw new Error('身份查询已失效')
        userInfo.value = info
        permissionStore.setPermissions(info?.permissions || [])
        return info
      } catch (error) {
        if (!isCurrent()) throw error
        console.error('读取当前身份失败')
        // 当前会话读取失败时才清理，不影响稍后登录的其他员工
        resetDynamicRoutes(permissionStore.routes)
        token.value = ''
        userInfo.value = null
        permissionStore.reset()
        throw error
      }
    }

    // 重置状态
    const reset = () => {
      const permissionStore = usePermissionStore()
      resetDynamicRoutes(permissionStore.routes)
      token.value = ''
      userInfo.value = null
      permissionStore.reset()
    }

    return {
      token,
      userInfo,
      login,
      logout,
      fetchUserInfo,
      reset
    }
  },
  {
    persist: {
      key: 'agri-user',
      storage: localStorage,
      paths: ['token', 'userInfo']
    }
  }
)
