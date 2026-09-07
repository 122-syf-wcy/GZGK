/**
 * uni.request 适配层。
 *
 * 目标是让 gzly-web 的 `api/*.ts` 能近乎原样平移过来，因此刻意保留了 axios 的两条约定：
 *   1. resolve 出去的是一层包装对象，业务层取数仍然是 `res.data.data`
 *   2. 业务失败（HTTP 200 + code != 0）走 reject
 *
 * 后端有三个必须在这一层吃掉的特性：
 *   - `default-property-inclusion: non_null`，值为 null 的字段在 JSON 里直接消失
 *   - 业务失败是 HTTP 200 + code = -1，只看状态码会把失败当成功
 *   - 401 / 403 / 429 由拦截器裸写，body 里没有 data 字段，形态与 Result 不同
 */

import { API_BASE_URL, DEFAULT_TIMEOUT, STORAGE_KEYS } from '@/constants/config'
import type { Result } from '@/types'

/** 读取登录态 token。存储读取是同步且很快的，不做内存缓存以免与注销/过期脱节。 */
function sessionToken(): string {
  try {
    const raw = uni.getStorageSync(STORAGE_KEYS.session)
    return raw && typeof raw === 'object' ? String(raw.token || '') : ''
  } catch {
    return ''
  }
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestConfig {
  /** query 参数，会被拼到 url 上。undefined / null / '' 的项自动丢弃。 */
  params?: Record<string, unknown>
  headers?: Record<string, string>
  timeout?: number
  /** 导出类接口用 arraybuffer，跳过 Result 解包 */
  responseType?: 'text' | 'arraybuffer'
}

/** 与 axios 的 AxiosResponse 保持同构，业务层继续写 res.data.data */
export interface HttpResponse<T = unknown> {
  data: Result<T>
  statusCode: number
}

export interface BinaryResponse {
  data: ArrayBuffer
  statusCode: number
}

/** 带业务码的错误，便于上层区分"网络挂了"和"后端说不行" */
export class ApiError extends Error {
  readonly code: number
  readonly statusCode: number

  constructor(message: string, code: number, statusCode: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.statusCode = statusCode
  }
}

function buildUrl(url: string, params?: Record<string, unknown>): string {
  const full = url.startsWith('http') ? url : `${API_BASE_URL}${url}`
  if (!params) return full
  const pairs: string[] = []
  Object.keys(params).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value)) {
      value.forEach(v => pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(v))}`))
      return
    }
    pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  })
  if (!pairs.length) return full
  return `${full}${full.includes('?') ? '&' : '?'}${pairs.join('&')}`
}

/**
 * 把各种失败形态收敛成一句用户看得懂的中文。
 * 不裸露英文错误，也不把后端的技术细节透给用户。
 */
function describeHttpFailure(statusCode: number, body: unknown): string {
  const message = (body as { message?: string } | undefined)?.message
  if (message) return message
  if (statusCode === 401 || statusCode === 403) return '访问凭证已失效，请重新进入。'
  if (statusCode === 404) return '服务接口暂时不可用，请稍后重试。'
  if (statusCode === 410) return '该功能已下线。'
  if (statusCode === 429) return '操作太频繁了，请稍等片刻再试。'
  if (statusCode >= 500) return '服务暂时不可用，请稍后重试。'
  return '请求失败，请稍后重试。'
}

function coreRequest<T>(
  url: string,
  method: HttpMethod,
  data: unknown,
  config: RequestConfig = {},
): Promise<HttpResponse<T> | BinaryResponse> {
  const isBinary = config.responseType === 'arraybuffer'

  const token = sessionToken()

  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl(url, config.params),
      method,
      data: data as Record<string, unknown> | undefined,
      timeout: config.timeout ?? DEFAULT_TIMEOUT,
      responseType: isBinary ? 'arraybuffer' : 'text',
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(config.headers || {}),
      },
      success: (res) => {
        const statusCode = res.statusCode

        if (statusCode < 200 || statusCode >= 300) {
          // 401 = 登录态失效（过期/注销）。清会话并广播，auth store 会同步状态。
          if (statusCode === 401 && token) {
            try {
              uni.removeStorageSync(STORAGE_KEYS.session)
            } catch {
              // ignore
            }
            uni.$emit('gzly:unauthorized')
          }
          reject(new ApiError(describeHttpFailure(statusCode, res.data), statusCode, statusCode))
          return
        }

        if (isBinary) {
          resolve({ data: res.data as ArrayBuffer, statusCode })
          return
        }

        const body = res.data as Result<T>
        if (!body || typeof body.code !== 'number') {
          reject(new ApiError('返回数据格式异常，请稍后重试。', -1, statusCode))
          return
        }
        // 关键：业务失败是 HTTP 200 + code != 0
        if (body.code !== 0) {
          reject(new ApiError(body.message || '请求失败', body.code, statusCode))
          return
        }
        resolve({ data: body, statusCode })
      },
      fail: (err) => {
        const raw = String(err?.errMsg || '')
        if (raw.includes('timeout')) {
          reject(new ApiError('请求处理时间较长，请稍后重试。', -1, 0))
          return
        }
        reject(new ApiError('网络连接异常，请检查网络后重试。', -1, 0))
      },
    })
  })
}

export const http = {
  get<T>(url: string, config?: RequestConfig) {
    return coreRequest<T>(url, 'GET', undefined, config) as Promise<HttpResponse<T>>
  },
  post<T>(url: string, data?: unknown, config?: RequestConfig) {
    return coreRequest<T>(url, 'POST', data, config) as Promise<HttpResponse<T>>
  },
  /** 导出类接口：返回二进制，不做 Result 解包 */
  postBinary(url: string, data?: unknown, config?: RequestConfig) {
    return coreRequest<never>(url, 'POST', data, {
      ...config,
      responseType: 'arraybuffer',
    }) as Promise<BinaryResponse>
  },
}

/**
 * 方案凭证走请求头，不进 query。
 *
 * safetyCode / accessKey 是无账号模型下方案的全部所有权凭证，放进 URL 会落进
 * 访问日志与 Referer。后端两种传法都支持，客户端一律用请求头。
 */
/**
 * uni-app 的 input/switch 事件在 Vue 模板类型里被推断成 DOM Event，
 * 运行时却是 `{ detail: { value } }`。集中在这里断言一次，
 * 免得每个页面各写各的 as unknown as。
 */
export function inputValue(e: unknown): string {
  return (e as { detail?: { value?: string } })?.detail?.value ?? ''
}

export function switchValue(e: unknown): boolean {
  return Boolean((e as { detail?: { value?: boolean } })?.detail?.value)
}

export function planAuthHeaders(safetyCode?: string, accessKey?: string): Record<string, string> {
  const headers: Record<string, string> = {}
  if (safetyCode) headers['X-Plan-Safety-Code'] = safetyCode
  if (accessKey) headers['X-Plan-Access-Key'] = accessKey
  return headers
}

export default http
