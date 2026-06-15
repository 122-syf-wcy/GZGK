import axios from 'axios'
import type { Result } from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

/**
 * 三类 token 对应不同后端拦截器路径：
 *   - admin token (`gz_token`)        → /admin/* 必带
 *   - alumni token (`alumni_token`)   → /alumni/* 必带
 *   - 用户 token (`gz_user_token`)      → /me/*、/volunteer/*、/auth/* 等业务路径
 *
 * 同时具备 admin 与 user token 时，admin 调 admin 路径、user 调用户路径，互不污染。
 */
http.interceptors.request.use((config) => {
  const url = config.url || ''
  const adminToken = localStorage.getItem('gz_token')
  const alumniToken = localStorage.getItem('alumni_token')
  const userToken = localStorage.getItem('gz_user_token')
  let token: string | null = null
  if (url.startsWith('/admin/')) {
    token = adminToken
  } else if (url.startsWith('/alumni/')) {
    token = alumniToken || adminToken
  } else if (url.startsWith('/me/') || url === '/me') {
    token = userToken || adminToken
  } else {
    // 业务 API 多为公开接口：仅携带普通用户 token，不再回退 admin token，
    // 避免管理员浏览前台时把高权限 token 暴露到公开业务请求中。
    token = userToken
  }
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
    return res
  },
  (err) => {
    if (err.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求处理时间较长，请稍后重试。'))
    }
    const status = err.response?.status
    const url = err.config?.url || ''
    if (status === 401 && (url.startsWith('/me/') || url === '/me')) {
      // 仅清理用户态登录信息；activate/login 路径上抛由调用方处理
      try {
        localStorage.removeItem('gz_user_token')
        localStorage.removeItem('gz_user_profile')
      } catch {
        // ignore
      }
    }
    if (status === 401 && url.startsWith('/admin/')) {
      // 管理端 token 失效：清理本机 admin token，避免失效凭证长期驻留
      try {
        localStorage.removeItem('gz_token')
      } catch {
        // ignore
      }
    }
    const msg = err.response?.data?.message || err.message || '网络异常'
    return Promise.reject(new Error(msg))
  },
)

export default http
