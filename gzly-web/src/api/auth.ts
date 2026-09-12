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

export interface EmailLoginResult {
  token: string
  userId: number
  email: string
  nickname: string
  newUser: boolean
}

/** 发送邮箱验证码（60 秒冷却、每日上限由后端控制）。 */
export function sendEmailCode(email: string) {
  return http.post<Result<{ cooldownSeconds: number }>>('/auth/email/send-code', { email })
}

/** 验证码登录：邮箱未注册时自动创建账号。 */
export function emailLogin(email: string, code: string) {
  return http.post<Result<EmailLoginResult>>('/auth/email/login', { email, code })
}
