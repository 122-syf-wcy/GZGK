import http from './request'
import type { Result } from '@/types'

export interface AuthMeResponse {
  userId: number
  identifier: string
  nickname: string
  lastLoginAt?: string | null
  maxPlans?: number
  usedPlans?: number
  remainPlans?: number
  expiresAt?: string | null
}

/** 当前登录态查询（携带 user token，自动通过 axios 拦截注入）。 */
export function fetchMe() {
  return http.get<Result<AuthMeResponse>>('/me', { timeout: 8000 })
}
