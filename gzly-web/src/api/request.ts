import axios from 'axios'
import type { Result } from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

/** 后端由管理员 JWT 保护的校友审核路径（与 WebMvcConfig 拦截器规则一致）。 */
const ALUMNI_ADMIN_PREFIXES = [
  '/alumni/admin/',
  '/alumni/media/pending',
  '/alumni/media/review',
  '/alumni/content/edits',
  '/alumni/content/review',
]

/**
 * 按接口归属选择凭证，三条认证链互不回退：
 * 管理端 gz_token、校友端 alumni_token、用户端 gz_user_token。
 * 通用公开接口优先携带用户 token（便于后端关联 userId），无则退管理 token。
 */
function pickToken(url: string): string | null {
  if (ALUMNI_ADMIN_PREFIXES.some(prefix => url.startsWith(prefix))) {
    return localStorage.getItem('gz_token')
  }
  if (url.startsWith('/alumni/')) {
    return localStorage.getItem('alumni_token')
  }
  if (url.startsWith('/admin/')) {
    return localStorage.getItem('gz_token')
  }
  if (url.startsWith('/me')) {
    return localStorage.getItem('gz_user_token')
  }
  return localStorage.getItem('gz_user_token') || localStorage.getItem('gz_token')
}

http.interceptors.request.use((config) => {
  const token = pickToken(config.url || '')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    if (res.config.responseType === 'blob' || res.data instanceof Blob) {
      return res
    }
    const data = res.data as Result
    if (data.code !== 0) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    // 契约校验按需异步加载（zod 不进主包），失败仅告警不影响业务
    void import('@/schema/api-contracts')
      .then(m => m.validateApiContract(res.config.url || '', data.data))
      .catch(() => {})
    return res
  },
  (err) => {
    if (err.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求处理时间较长，请稍后重试。'))
    }
    // 后端返回的业务 message 优先；HTTP/网络层错误不裸露英文，转换为用户可读文案。
    const bizMessage = err.response?.data?.message
    if (bizMessage) {
      return Promise.reject(new Error(bizMessage))
    }
    const status = err.response?.status
    if (typeof status === 'number') {
      if (status === 401 || status === 403) {
        return Promise.reject(new Error('登录状态已失效或没有访问权限。'))
      }
      if (status === 404) {
        return Promise.reject(new Error('服务接口暂时不可用，请稍后重试。'))
      }
      if (status === 429) {
        return Promise.reject(new Error('操作太频繁了，请稍等片刻再试。'))
      }
      return Promise.reject(new Error('服务暂时不可用，请稍后重试。'))
    }
    return Promise.reject(new Error('网络连接异常，请检查网络后重试。'))
  },
)

export default http
