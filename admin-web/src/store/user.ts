import {defineStore} from 'pinia'
import {ref} from 'vue'
import {getStaffInfo, staffLogin, staffLogout} from '@/api/auth'
import type {LoginRequest, UserInfo} from '@/types/api'
import router from '@/router'

export const useUserStore = defineStore(
  'user',
  () => {
    // 状态
    const token = ref<string>('')
    const userInfo = ref<UserInfo | null>(null)

    // 登录
    const login = async (loginForm: LoginRequest) => {
      const response = await staffLogin(loginForm)
      token.value = response.tokenValue
      userInfo.value = response.userInfo
      return response
    }

    // 登出
    const logout = async () => {
      try {
        await staffLogout()
      } catch (error) {
        console.error('Logout error:', error)
      } finally {
        token.value = ''
        userInfo.value = null
        router.push('/login')
      }
    }

    // 获取用户信息
    const fetchUserInfo = async () => {
      if (!token.value) {
        throw new Error('No token available')
      }
      try {
        const info = await getStaffInfo()
        userInfo.value = info
        return info
      } catch (error) {
        console.error('Fetch user info error:', error)
        // 如果获取失败，清除 token
        token.value = ''
        userInfo.value = null
        throw error
      }
    }

    // 重置状态
    const reset = () => {
      token.value = ''
      userInfo.value = null
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

