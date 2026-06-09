import http from './request'
import type { BatchSupportResponse, CandidateType, GradientRanges, Result, VolunteerPlan, RankCheckResponse } from '@/types'
import { normalizeProvinceCode, type ProvinceCode } from '@/constants/provinces'

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

/**
 * 四川（PROFESSIONAL_GROUP_45 省份）18 批次支持矩阵。
 * 与 getGzBatchSupport 返回结构一致，前端无需感知数据源差异。
 */
export function getScBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/sc/batch-support')
}

/**
 * 安徽（PROFESSIONAL_GROUP_45 省份）14 批次支持矩阵。
 * 与 getGzBatchSupport / getScBatchSupport 返回结构一致；
 * 批次代码以 AH_* 前缀命名（AH_BENKE 主流程 + 13 个 QUERY_ONLY 兜底批次）。
 */
export function getAhBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/ah/batch-support')
}

export function getHbBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/hb/batch-support')
}

export function getGxBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/gx/batch-support')
}

export function getHiBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/hi/batch-support')
}

export function getYnBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/yn/batch-support')
}

export function getHaBatchSupport() {
  return http.get<Result<BatchSupportResponse>>('/volunteer/ha/batch-support')
}

export function hasBatchSupportEndpoint(provinceCode: ProvinceCode | string): boolean {
  const code = normalizeProvinceCode(provinceCode)
  return ['GZ', 'SC', 'AH', 'HB', 'GX', 'HI', 'YN', 'HA'].includes(code)
}

/**
 * 按 provinceCode 自动分发到本省批次支持矩阵接口。
 * 不允许把 HB/GX/HI/YN/HA 静默发送到 SC/GZ。
 */
export function getBatchSupportByProvince(provinceCode: ProvinceCode | string): ReturnType<typeof getGzBatchSupport> {
  const code = normalizeProvinceCode(provinceCode)
  if (code === 'GZ') return getGzBatchSupport()
  if (code === 'AH') return getAhBatchSupport()
  if (code === 'SC') return getScBatchSupport()
  if (code === 'HB') return getHbBatchSupport()
  if (code === 'GX') return getGxBatchSupport()
  if (code === 'HI') return getHiBatchSupport()
  if (code === 'YN') return getYnBatchSupport()
  if (code === 'HA') return getHaBatchSupport()
  return getGzBatchSupport()
}

/**
 * 四川艺术 / 体育综合分实时计算接口。
 *
 * 响应字段：
 *   - success: 是否计算成功（普通类 / 未知统考类别 → false）
 *   - score: 综合分（艺术 ≤ 750，体育 ≤ 750）
 *   - category: 公式归属（"美术/设计/戏剧/服装/播音类" 等）
 *   - cultureRatio / professionalRatio: 文化 / 统考权重
 *   - cultureWeighted / professionalWeighted: 加权后的分数
 *   - professionalScale: 折算系数（艺术 2.5 / 体育 7.5）
 *   - formula: 可读公式描述
 *   - supportedArtCategories: 11 个艺术统考类别列表，给前端下拉用
 */
export interface ScCompositeScoreResponse {
  success: boolean
  candidateType: string
  artCategory: string
  cultureScore: number
  professionalScore: number
  score: number
  category?: string
  cultureRatio?: number
  professionalRatio?: number
  cultureWeighted?: number
  professionalWeighted?: number
  professionalScale?: number
  formula: string
  supportedArtCategories: string[]
}

export function getScCompositeScore(params: {
  candidateType: '艺术类' | '体育类'
  artCategory?: string
  cultureScore: number
  professionalScore: number
}) {
  return http.get<Result<ScCompositeScoreResponse>>('/volunteer/sc/composite-score', { params })
}

/**
 * 安徽艺术 / 体育综合分实时计算接口。
 *
 * <p>口径：皖招委〔2024〕11 号 + 2025 安徽体育文化控线公告（物理 300 / 历史 310）。</p>
 *
 * <p>艺术类两档公式：</p>
 * <ul>
 *   <li>综合分1（音乐 / 舞蹈 / 表（导）演 / 美术与设计 / 书法）：文化 × 50% + 统考 × 2.5 × 50%</li>
 *   <li>综合分2（播音与主持）：文化 × 70% + 统考 × 2.5 × 30%</li>
 * </ul>
 *
 * <p>体育类公式：综合 = 1.2 × 专业 + 0.8 × [60 + 40 × (文化 - 本科文化控线) ÷ (750 - 控线)]
 *  （firstSubject 路由：物理→300，历史→310；空值默认物理）。</p>
 */
export interface AhCompositeScoreResponse {
  success: boolean
  candidateType: string
  artCategory: string
  firstSubject: string
  cultureScore: number
  professionalScore: number
  score: number
  category?: string
  selectedCategory?: string
  cultureRatio?: number
  professionalRatio?: number
  cultureWeighted?: number
  professionalWeighted?: number
  professionalScale?: number
  /** 体育类专有：本次计算所用的本科文化控线（物理 300 / 历史 310 默认）。 */
  cultureBenkeLine?: number | null
  formula: string
  supportedArtCategories: string[]
}

export function getAhCompositeScore(params: {
  candidateType: '艺术类' | '体育类'
  artCategory?: string
  firstSubject?: '物理' | '历史'
  cultureScore: number
  professionalScore: number
}) {
  return http.get<Result<AhCompositeScoreResponse>>('/volunteer/ah/composite-score', { params })
}

/** 查询历史方案 */
export function fetchPlanHistory(identifier: string) {
  return http.get<Result<VolunteerPlan[]>>('/volunteer/history', {
    params: { identifier },
  })
}

/** 通过安全码恢复单个志愿方案，后端兼容旧 accessKey */
export function fetchVolunteerPlan(planId: number, safetyCode: string) {
  return http.post<Result<VolunteerPlan>>('/volunteer/plan', { planId, safetyCode, accessKey: safetyCode })
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
  safetyCode?: string
  accessKey?: string
  message: string
  aiReport?: string
  messages?: ZxfSkillChatMessage[]
}) {
  return http.post<Result<ZxfSkillChatResponse>>(`/volunteer/plans/${data.planId}/skills/ask`, {
    safetyCode: data.safetyCode || data.accessKey || '',
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
