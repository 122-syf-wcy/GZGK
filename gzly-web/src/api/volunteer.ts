import http from './request'
import type { BatchSupportResponse, CandidateType, GradientRanges, Result, VolunteerPlan, RankCheckResponse } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

/** 生成志愿方案：公共入口只允许当前激活招生年份，历史年份仅用于后台回测 */
export function generateVolunteerPlan(data: {
  provinceCode?: ProvinceCode
  province?: string
  year?: number
  candidateType?: CandidateType
  batchCode?: string
  totalScore: number
  provinceRank: number
  firstSubject: '物理' | '历史'
  resubjects: string[]
  preferredMajors?: string[]
  preferredRegions?: string[]
  strategyMode?: '保守型' | '均衡型' | '冲刺型'
  decisionPriority?: '学校优先' | '专业优先'
  careerGoal?: '就业优先' | '升学优先' | '城市机会优先'
  tuitionBudget?: '低预算' | '均衡预算' | '不限制'
  acceptPrivate?: boolean
  acceptSinoForeign?: boolean
  safetyCode?: string
  agreedDisclaimer: true
  disclaimerVersion: string
  gradientRanges?: GradientRanges
  qualificationTags?: string[]
  artProfessionalScore?: number
  sportsProfessionalScore?: number
  comprehensiveScore?: number
}) {
  return http.post<Result<VolunteerPlan>>('/volunteer/recommend', data)
}

export function getGzBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/gz/batch-support')
}

/** 查询历史方案 */
export function fetchPlanHistory(identifier: string) {
  return http.get<Result<VolunteerPlan[]>>('/volunteer/history', {
    params: { identifier },
  })
}

/** 通过安全码恢复单个志愿方案，后端兼容旧 accessKey */
export function fetchVolunteerPlan(planId: number, safetyCode: string) {
  return http.post<Result<VolunteerPlan>>('/volunteer/plan', { planId, safetyCode })
}

export function fetchVolunteerPlanDetail(planId: number, safetyCode?: string) {
  return http.get<Result<VolunteerPlan>>(`/volunteer/plans/${planId}`, {
    headers: safetyCode ? { 'X-Safety-Code': safetyCode } : undefined,
  })
}

export function verifyPlanSafetyCode(planId: number, safetyCode: string) {
  return http.post<Result<{ valid: boolean; planId: number }>>(
    `/volunteer/plans/${planId}/verify-safety-code`,
    {},
    { headers: { 'X-Safety-Code': safetyCode }, timeout: 10000 },
  )
}

/** AI 分析：换发短效一次性 SSE 凭证，避免长期安全码出现在 URL 中。 */
export function createAiAnalysisTicket(planId: number, safetyCode: string, profile?: string) {
  return http.post<Result<{ ticket: string; expiresInSeconds: number }>>('/volunteer/ai-analysis-ticket', {
    planId,
    safetyCode,
    profile,
  })
}

export function getAiAnalysisUrl(ticket: string): string {
  const params = new URLSearchParams({ ticket })
  return `/api/volunteer/ai-analysis?${params.toString()}`
}

export interface AiAnalysisResponse {
  planId: number
  status: string
  progress: number
  conclusion: string
  gradientSummary?: Record<string, number>
  bestKeepItem?: Record<string, unknown>
  highestRiskItem?: Record<string, unknown>
  diagnosisSections?: Array<{ title: string; content: string }>
  topKeepDirections?: string[]
  topRiskPoints?: string[]
  actionSteps?: Array<{ title: string; content: string }>
  reorderAdvice?: string[]
  disclaimer?: string
  /** 真实使用的 AI 模型名（如 "gpt-4o-mini"）；fallback 时为 "rule-template-v1"。 */
  aiModelVersion?: string
  /** 是否走规则模板兜底；true 表示未真正调用 AI 或 AI 异常。 */
  aiFallbackUsed?: boolean
  /** 兜底原因（仅 fallback 时填）。 */
  aiFallbackReason?: string
  /** 数据质量问题清单（低参考度志愿、缺失字段等），由后端从 plan 计算。 */
  dataIssues?: string[]
}

export function generateAiAnalysis(planId: number, safetyCode: string, forceRefresh = false) {
  return http.post<Result<AiAnalysisResponse>>(`/volunteer/plans/${planId}/ai-analysis`, {
    safetyCode,
    forceRefresh,
  }, { timeout: 90000 })
}

export function getAiAnalysis(planId: number, safetyCode: string) {
  return http.get<Result<AiAnalysisResponse>>(`/volunteer/plans/${planId}/ai-analysis`, {
    params: { safetyCode },
    timeout: 90000,
  })
}

export interface ZxfSkillChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ZxfSkillChatResponse {
  reply?: string
  answer?: string
  actionItems?: string[]
  referencedVolunteers?: Array<Record<string, unknown>>
  sourceChunks?: Array<Record<string, unknown>>
  disclaimer?: string
  sourceProjectName?: string
  sourceProjectUrl?: string
  sourceNote?: string
}

export function chatZxfSkill(data: {
  planId: number
  safetyCode: string
  message: string
  aiReport?: string
  messages?: ZxfSkillChatMessage[]
}) {
  return http.post<Result<ZxfSkillChatResponse>>(`/volunteer/plans/${data.planId}/skills/ask`, {
    safetyCode: data.safetyCode,
    question: data.message,
    aiReport: data.aiReport,
    messages: data.messages,
  }, {
    timeout: 90000,
  })
}

export function exportPlanLongImage(planId: number, safetyCode: string) {
  return http.post<Blob>(`/volunteer/plans/${planId}/export-long-image`, { safetyCode }, {
    responseType: 'blob',
    timeout: 90000,
  })
}

export function exportPlanExcel(planId: number, safetyCode: string) {
  return http.post<Blob>(`/volunteer/plans/${planId}/export-excel`, { safetyCode }, {
    responseType: 'blob',
    timeout: 90000,
  })
}

/**
 * 位次校验：用官方一分一段表给出区间提示；生成时未手填位次会由后端采用保守估算并标记。
 * 用于在 VolunteerForm 输入分数后给出可视化提醒。
 */
export function rankCheck(params: {
  provinceCode?: ProvinceCode
  totalScore: number
  provinceRank?: number
  firstSubject: '物理' | '历史'
}) {
  return http.get<Result<RankCheckResponse>>('/volunteer/rank-check', { params })
}

export { generateVolunteerPlan as generatePlan }

export interface VolunteerHistoryRow {
  planId: number
  createdAt?: string
  score?: number
  rank?: number
  provinceCode?: string
  batchName?: string
  strategyMode?: string
  firstSubject?: string
  itemCount?: number
}

export interface VolunteerHistoryResp {
  records: VolunteerHistoryRow[]
  total: number
  page: number
  pageSize: number
}

export function listMyVolunteerPlans(params: { page?: number; pageSize?: number } = {}) {
  return http.get<Result<VolunteerHistoryResp>>('/volunteer/plans', {
    params: { page: params.page ?? 1, pageSize: params.pageSize ?? 20 },
  })
}
