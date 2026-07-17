import axios, {AxiosInstance, AxiosResponse, InternalAxiosRequestConfig} from 'axios'
import {ElMessage} from 'element-plus'
import {useUserStore} from '@/store/user'
import router from '@/router'
import type {ApiResponse} from '@/types/api'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    const token = userStore.token
    
    // 如果有 token，添加到请求头
    if (token) {
      // Sa-Token 配置的 token-name 是 Authorization
      config.headers['Authorization'] = token
    }
    
    // 开发环境打印请求日志
    if (import.meta.env.DEV) {
      console.log('🚀 Request:', config.method?.toUpperCase(), config.url, config.data || config.params)
    }
    
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse | any>) => {
    const res = response.data
    
    // 开发环境打印响应日志
    if (import.meta.env.DEV) {
      console.log('✅ Response:', response.status, response.config.url, res)
    }
    
    // 处理直接返回数据的情况（如 AuthStaffController 直接返回 LoginResultVO）
    if (res && res.tokenValue) {
      return res
    }
    
    // 处理包装在 ApiResponse 中的情况
    if (res && typeof res === 'object' && 'code' in res) {
      const apiRes = res as ApiResponse
      const code: any = apiRes.code
      const isNumericCode =
        typeof code === 'number' || (typeof code === 'string' && /^[0-9]+$/.test(code))

      if (isNumericCode) {
        // 如果 code 为 0 或 '200'，直接返回 data
        if (code === 0 || code === '200' || code === 200) {
          return apiRes.data !== undefined ? apiRes.data : res
        }

        // 401 未授权，清除 token 并跳转到登录页
        if (code === 401 || code === '401' || code === 10003) {
          const userStore = useUserStore()
          userStore.logout()
          ElMessage.error('登录已过期，请重新登录')
          router.push('/login')
          return Promise.reject(new Error('Unauthorized'))
        }

        // 其他错误，显示错误消息（优先使用 message 字段）
        const errorMessage = apiRes.message || '请求失败'
        ElMessage.error(errorMessage)
        return Promise.reject(new Error(errorMessage))
      }
      // code 存在但非数字（如业务字段 code=CUST_xxx），视为普通数据返回
      return res
    }

    // 如果没有 code 字段，直接返回数据（兼容直接返回 VO 的情况，如 CustomerAdminController）
    return res
  },
  (error) => {
    console.error('❌ Response error:', error)
    
    // 处理 HTTP 错误
    if (error.response) {
      const { status, data } = error.response
      
      // 开发环境打印详细错误信息
      if (import.meta.env.DEV) {
        console.error('Error details:', {
          status,
          url: error.config?.url,
          method: error.config?.method,
          data
        })
      }
      
      if (status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login')
      } else if (status === 403) {
        ElMessage.error('没有权限访问')
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在')
      } else if (status >= 500) {
        ElMessage.error('服务器错误，请稍后重试')
      } else if (status === 400) {
        // 400 错误通常包含具体的错误信息
        const errorMessage = data?.message || data?.data?.message || '请求参数错误'
        ElMessage.error(errorMessage)
      } else {
        // 其他错误，尝试提取错误消息
        const errorMessage = data?.message || data?.data?.message || '请求失败'
        ElMessage.error(errorMessage)
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    
    return Promise.reject(error)
  }
)

export default service
