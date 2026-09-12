import http from './request'
import type { GradientRanges, Result, VolunteerPlan, RankCheckResponse } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

export interface BatchSupportItem {
  batchCode: string
  batchName: string
  candidateType: string
  category?: string
  supportLevel: 'FULL_RECOMMEND' | 'TRIAL_RECOMMEND' | 'ESTIMATE_RECOMMEND' | 'QUERY_ONLY' | string
  recommendMode?: string
  engine?: string
  engineName?: string
  policyVersion?: string
  algorithmFamily?: string
  generationEngine?: string
  modelRoute?: string
  modelRouteStatus?: string
  mlEligible?: boolean
  mlModelPolicy?: string
  activationGate?: string
  algorithmReason?: string
  algorithmPolicy?: Record<string, unknown>
  targetCount: number
  maxVolunteerCount: number
  majorPerSchoolCount?: number
  hasAdjustment?: boolean
  volunteerMode?: string
  policyStatus?: string
  officialSourceTitle?: string
  officialSourceUrl?: string
  generatorReady?: boolean
  supportReason?: string
  missingData?: string[]
}

export interface ProvinceBatchSupportResponse {
  provinceCode: ProvinceCode
  provinceName?: string
  year: number
  targetYear?: number
  latestOfficialDataYear?: number
  dataSourceYears?: number[]
  historyYears?: number[]
  trainingYears?: number[]
  recommendationPhase: string
  phaseGate?: Record<string, unknown>
  phaseGates?: Array<Record<string, unknown>>
  officialDataReady: boolean
  publicYearLocked?: boolean
  items: BatchSupportItem[]
  summary?: Record<string, number>
  warnings?: string[]
}

/** 生成志愿方案：新链路会先校验年度政策，再返回机会指数口径的方案 */
export function generateVolunteerPlan(data: {
  provinceCode?: ProvinceCode
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
  agreedDisclaimer: true
  disclaimerVersion: string
  gradientRanges?: GradientRanges
}) {
  return http.post<Result<VolunteerPlan>>('/volunteer/recommend', data)
}

/** 查询历史方案 */
export function fetchPlanHistory(identifier: string) {
  return http.get<Result<VolunteerPlan[]>>('/volunteer/history', {
    params: { identifier },
  })
}

/** 通过访问密钥恢复单个志愿方案 */
export function fetchVolunteerPlan(planId: number, accessKey: string) {
  return http.post<Result<VolunteerPlan>>('/volunteer/plan', { planId, accessKey })
}

/** skills 问答的推荐追问列表。 */
export function fetchSkillSuggestedQuestions(planId: number) {
  return http.get<Result<string[]>>(`/volunteer/plans/${planId}/skills/suggested-questions`)
}

/** AI 分析：换发短效一次性 SSE 凭证，避免长期 accessKey 出现在 URL 中。 */
export function createAiAnalysisTicket(planId: number, accessKey: string, profile?: string) {
  return http.post<Result<{ ticket: string; expiresInSeconds: number }>>('/volunteer/ai-analysis-ticket', {
    planId,
    accessKey,
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
}

export function generateAiAnalysis(planId: number, accessKey: string, forceRefresh = false) {
  return http.post<Result<AiAnalysisResponse>>(`/volunteer/plans/${planId}/ai-analysis`, {
    accessKey,
    forceRefresh,
  }, { timeout: 90000 })
}

export function getAiAnalysis(planId: number, accessKey: string) {
  return http.get<Result<AiAnalysisResponse>>(`/volunteer/plans/${planId}/ai-analysis`, {
    params: { accessKey },
    timeout: 90000,
  })
}

export interface ZxfSkillChatStep {
  tool: string
  label: string
  status: 'running' | 'done'
  summary?: string
}

export interface ZxfSkillChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** 模型思考过程（仅前端展示，不回传给后端） */
  thinking?: string
  /** Agent 工具调用步骤（仅前端展示，不回传给后端） */
  steps?: ZxfSkillChatStep[]
  /** 是否仍在流式生成中 */
  streaming?: boolean
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
  accessKey: string
  message: string
  aiReport?: string
  messages?: ZxfSkillChatMessage[]
}) {
  return http.post<Result<ZxfSkillChatResponse>>(`/volunteer/plans/${data.planId}/skills/ask`, {
    accessKey: data.accessKey,
    question: data.message,
    aiReport: data.aiReport,
    messages: data.messages,
  }, {
    timeout: 90000,
  })
}

export function exportPlanLongImage(planId: number, accessKey: string) {
  return http.post<Blob>(`/volunteer/plans/${planId}/export-long-image`, { accessKey }, {
    responseType: 'blob',
    timeout: 90000,
  })
}

export function exportPlanExcel(planId: number, accessKey: string) {
  return http.post<Blob>(`/volunteer/plans/${planId}/export-excel`, { accessKey }, {
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


export function getProvinceBatchSupport(provinceCode: ProvinceCode, year = 2026) {
  switch (provinceCode) {
    case 'GZ':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/gz/batch-support', { params: { year } })
    case 'SC':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/sc/batch-support', { params: { year } })
    case 'AH':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/ah/batch-support', { params: { year } })
    case 'HB':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/hb/batch-support', { params: { year } })
    case 'GX':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/gx/batch-support', { params: { year } })
    case 'HI':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/hi/batch-support', { params: { year } })
    case 'YN':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/yn/batch-support', { params: { year } })
    case 'HA':
      return http.get<Result<ProvinceBatchSupportResponse>>('/volunteer/ha/batch-support', { params: { year } })
    default:
      throw new Error(`Unsupported province code: ${provinceCode}`)
  }
}

export { generateVolunteerPlan as generatePlan }
