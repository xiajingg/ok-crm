import axios, { type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getToken } from '@/utils/auth'

/** 后端统一响应体 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
  success?: boolean
}

/** 业务错误码（与后端 ErrorCode 保持一致，只列前端需要特殊处理的） */
export const BizCode = {
  SUCCESS: 0,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  PERMISSION_DENIED: 1105,
  /** 当前租户未购买该模块 */
  MODULE_NOT_LICENSED: 1401
} as const

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 20000
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    // 文件下载等非 JSON 响应直接放行
    if (!body || typeof body.code !== 'number') {
      return response.data
    }
    if (body.code !== BizCode.SUCCESS) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code }))
    }
    // 直接返回 data，业务层不必再解一层
    return body.data as never
  },
  (error) => {
    const status = error?.response?.status
    const body = error?.response?.data as ApiResult | undefined

    if (status === 401) {
      ElMessage.error(body?.message || '登录已过期，请重新登录')
      clearToken()
      // 这里刻意不 import router：request 被 router 间接依赖，互相 import 容易形成循环
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error(body?.message || '没有操作权限')
    } else {
      ElMessage.error(body?.message || error?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return instance.get(url, { params }) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return instance.post(url, data) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  return instance.put(url, data, { params }) as unknown as Promise<T>
}

export function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return instance.delete(url, { params }) as unknown as Promise<T>
}

export default instance
