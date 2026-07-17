import { BASE_URL, DEFAULT_TIMEOUT } from '@/config/env'
import type { Result } from '@/types'

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  method?: Method
  data?: unknown
  params?: Record<string, unknown>
  header?: Record<string, string>
  timeout?: number
}

/**
 * 三类 token 按路径选择，镜像 gzly-web 的 request.ts：
 *   - /admin/*  → gz_token
 *   - /alumni/* → alumni_token（回退 admin）
 *   - /me/*     → gz_user_token（回退 admin）
 *   - 其它业务  → gz_user_token
 */
function resolveToken(url: string): string {
  const admin = (uni.getStorageSync('gz_token') as string) || ''
  const alumni = (uni.getStorageSync('alumni_token') as string) || ''
  const user = (uni.getStorageSync('gz_user_token') as string) || ''
  if (url.startsWith('/admin/')) return admin
  if (url.startsWith('/alumni/')) return alumni || admin
  if (url.startsWith('/me/') || url === '/me') return user || admin
  return user
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const parts: string[] = []
  Object.keys(params).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') return
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  })
  return parts.length ? `?${parts.join('&')}` : ''
}

function clearTokenOn401(url: string) {
  if (url.startsWith('/me/') || url === '/me') {
    uni.removeStorageSync('gz_user_token')
    uni.removeStorageSync('gz_user_profile')
  }
  if (url.startsWith('/admin/')) {
    uni.removeStorageSync('gz_token')
  }
}

/**
 * 统一请求：自动拼 baseURL、带 token、解包 Result。
 * 成功时直接 resolve 出 Result.data（业务数据）。
 */
export function request<T = unknown>(url: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', data, params, header = {}, timeout = DEFAULT_TIMEOUT } = options
  const token = resolveToken(url)
  const fullUrl = BASE_URL + url + buildQuery(params)

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method,
      data: data as string | AnyObject | ArrayBuffer | undefined,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...header,
      },
      success: (res) => {
        const status = res.statusCode
        if (status === 401) clearTokenOn401(url)
        if (status < 200 || status >= 300) {
          const body = res.data as Partial<Result>
          reject(new Error(body?.message || `请求失败（${status}）`))
          return
        }
        const body = res.data as Result<T>
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code !== 0) {
            reject(new Error(body.message || '请求失败'))
            return
          }
          resolve(body.data)
          return
        }
        // 非标准包裹（如直接返回数组/对象）
        resolve(res.data as T)
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络异常，请稍后重试'))
      },
    })
  })
}

export const httpGet = <T = unknown>(url: string, params?: Record<string, unknown>) =>
  request<T>(url, { method: 'GET', params })

export const httpPost = <T = unknown>(url: string, data?: Record<string, unknown>) =>
  request<T>(url, { method: 'POST', data })
