/**
 * 账号相关接口。
 *
 * 后端是「邮箱验证码注册登录一体」：send-code（60 秒冷却、每日 10 次、5 分钟有效）
 * → login（6 位码、错 5 次作废、未注册自动建号、签发 7 天 user JWT）。
 * /me/** 由后端拦截器保护，未携带有效 token 返回 HTTP 401（body 无 data 字段）。
 */

import { http } from '@/utils/request'
import { normalizePlan } from '@/utils/normalize'
import type { RawVolunteerPlan, VolunteerPlan } from '@/types'

export interface LoginResult {
  token: string
  userId: number
  email: string
  nickname: string
  newUser?: boolean
}

export async function sendEmailCode(email: string): Promise<{ cooldownSeconds: number }> {
  const res = await http.post<{ cooldownSeconds?: number }>('/auth/email/send-code', { email })
  return { cooldownSeconds: res.data.data?.cooldownSeconds ?? 60 }
}

export async function loginWithEmail(email: string, code: string): Promise<LoginResult> {
  const res = await http.post<LoginResult>('/auth/email/login', { email, code })
  return res.data.data
}

export interface MeProfile {
  userId?: number
  identifier?: string
  nickname?: string
  lastLoginAt?: string
}

export async function fetchMe(): Promise<MeProfile> {
  const res = await http.get<MeProfile>('/me')
  return res.data.data || {}
}

export interface CloudPlanItem {
  id: number
  provinceCode?: string
  targetBatch?: string
  totalScore?: number
  provinceRank?: number
  firstSubject?: string
  strategyMode?: string
  itemCount?: number
  hasAiAnalysis?: boolean
  createdAt?: string
}

export async function listCloudPlans(page = 1, size = 20): Promise<{ items: CloudPlanItem[]; total: number }> {
  const res = await http.get<{ items?: CloudPlanItem[]; total?: number }>('/me/plans', { params: { page, size } })
  return { items: res.data.data?.items || [], total: res.data.data?.total || 0 }
}

/** 云端方案详情走 token 鉴权，不需要本机安全码——这正是登录的核心价值 */
export async function getCloudPlan(planId: number): Promise<VolunteerPlan> {
  const res = await http.get<RawVolunteerPlan>(`/me/plans/${planId}`)
  return normalizePlan(res.data.data || {})
}

export async function claimPlan(planId: number, safetyCode: string): Promise<{ claimed: boolean; alreadyOwned: boolean }> {
  const res = await http.post<{ claimed?: boolean; alreadyOwned?: boolean }>('/me/plans/claim', { planId, safetyCode })
  return { claimed: !!res.data.data?.claimed, alreadyOwned: !!res.data.data?.alreadyOwned }
}

export async function deleteAccount(): Promise<void> {
  await http.post('/me/account/delete', { confirm: true })
}
