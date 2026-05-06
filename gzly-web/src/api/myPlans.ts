import http from './request'
import type { Result, VolunteerPlan } from '@/types'

export interface MyPlanListItem {
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

export interface MyPlanListResponse {
  items: MyPlanListItem[]
  total: number
  page: number
  size: number
}

export type MyPlanDetail = MyPlanListItem & Partial<VolunteerPlan> & {
  safetyCode?: string
  accessKey?: string
  planJson?: string | null
  manualReviewJson?: string | null
  metricsJson?: string | null
  dataQualityWarning?: string | null
  aiAnalysis?: {
    status?: string
    conclusion?: string
    complianceStatus?: string
    updatedAt?: string
    sanitizedAnalysisText?: string
  }
  skillsCount?: number
}

export interface SkillsHistoryItem {
  id: number
  question: string
  answer: string
  complianceStatus?: string
  createdAt?: string
}

export function listMyPlans(page = 1, size = 20) {
  return http.get<Result<MyPlanListResponse>>('/me/plans', { params: { page, size } })
}

export function fetchMyPlanDetail(id: number) {
  return http.get<Result<MyPlanDetail>>(`/me/plans/${id}`, { timeout: 12000 })
}

export function fetchMyPlanSkillsHistory(id: number) {
  return http.get<Result<SkillsHistoryItem[]>>(`/me/plans/${id}/skills-history`, { timeout: 12000 })
}

export interface ClaimMyPlanResponse {
  planId: number
  /** 是否本次新挂入当前账号 */
  claimed: boolean
  /** 该方案此前是否已经属于当前账号（幂等成功） */
  alreadyOwned?: boolean
}

/** 把通过安全码验证的匿名方案绑定到当前登录账号 */
export function claimMyPlan(planId: number, safetyCode: string) {
  return http.post<Result<ClaimMyPlanResponse>>('/me/plans/claim', { planId, safetyCode }, { timeout: 12000 })
}
