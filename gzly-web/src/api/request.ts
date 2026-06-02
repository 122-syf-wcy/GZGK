import axios from 'axios'
import type { Result } from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const url = config.url || ''
  const adminToken = localStorage.getItem('gz_token')
  const alumniToken = localStorage.getItem('alumni_token')
  const token = url.startsWith('/alumni/') ? (alumniToken || adminToken) : adminToken
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
    const msg = err.response?.data?.message || err.message || '网络异常'
    return Promise.reject(new Error(msg))
  },
)

export default http
