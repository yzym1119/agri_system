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
      try {
        const info = await getStaffInfo()
        userInfo.value = info
        permissionStore.setPermissions(info?.permissions || [])
        return info
      } catch (error) {
        console.error('Fetch user info error:', error)
        // 如果获取失败，清除 token
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
