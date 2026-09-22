/**
 * HTTP 客户端：统一注入 Basic 头、解信封、错误提示、401 登出跳转
 * 契约：docs/api-contract-notes.md §2/§3（端点与字段的权威来源是 bifrost-api 源码与 hurl/）
 */
import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

import type { ApiEnvelope } from './types'
import { useAuthStore } from '@/stores/auth'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 静默模式：错误不弹全局 toast（如登录验证，由调用方给文案） */
    silent?: boolean
  }
}

export class ApiError extends Error {
  public readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export const http = axios.create({
  baseURL: '/',
  timeout: 30_000,
})

// 请求：注入 Basic 认证头（ADR-0002）
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const auth = useAuthStore()
  if (auth.credentials) {
    config.headers.set('Authorization', `Basic ${auth.basicHeader}`)
  }
  return config
})

// 响应：401 清登录态并跳登录页；其余错误统一提示
http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const url = error.config?.url ?? ''
    const isPing = url.includes('/api/ping')
    const status = error.response?.status
    const serverMessage = error.response?.data?.message

    if (status === 401 && !isPing) {
      const auth = useAuthStore()
      auth.clear()
      const { default: router } = await import('@/router')
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    if (!isPing && !error.config?.silent) {
      const text = serverMessage || error.message || '网络请求失败'
      ElMessage.error(text)
    }
    return Promise.reject(error)
  },
)

/**
 * 请求并解信封：code===0 → data；否则抛 ApiError
 * （防御：契约中非 0 code 均伴随真实 HTTP 状态，由上方拦截器处理）
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http.request<ApiEnvelope<T>>(config)
  const body = res.data
  if (body && typeof body.code === 'number' && body.code !== 0) {
    const message = body.message || '请求失败'
    ElMessage.error(message)
    throw new ApiError(body.code, message)
  }
  return body.data
}

/** GET /api/ping —— 唯一不走信封的端点（返回裸字符串 pong） */
export async function ping(): Promise<string> {
  const res = await http.get<string>('/api/ping')
  return res.data
}
