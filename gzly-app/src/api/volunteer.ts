/**
 * 志愿主链路接口。
 *
 * 路径与 gzly-web 的 `api/volunteer.ts` 对齐，但有三处刻意的差异：
 *   1. 生成走 `/volunteer/recommend`（唯一带就绪度门禁与政策校验的入口），不用 `/volunteer/generate`
 *   2. 方案凭证走请求头，不进 query
 *   3. AI 解读只走结构化 POST，不实现 SSE（后端本就一次性返回全文，见 D1）
 */

import { GENERATE_TIMEOUT, LONG_TASK_TIMEOUT } from '@/constants/config'
import { http, planAuthHeaders } from '@/utils/request'
import { normalizeBatchSupport, normalizePlan } from '@/utils/normalize'
import type {
  BatchSupport,
  RawBatchSupportResponse,
  RawRankCheckResponse,
  RawVolunteerPlan,
  VolunteerFormData,
  VolunteerPlan,
} from '@/types'

/**
 * 省份批次支持状态。
 * 省份入口能否点击，唯一依据是返回里的 generatorReady。
 */
export async function getBatchSupport(provinceCode: string, year?: number): Promise<BatchSupport> {
  const res = await http.get<RawBatchSupportResponse>(
    `/volunteer/${provinceCode.toLowerCase()}/batch-support`,
    { params: { year } },
  )
  return normalizeBatchSupport(res.data.data || {}, provinceCode)
}

/** 位次区间校验。系统只做提示，绝不替考生写入位次。 */
export async function rankCheck(params: {
  provinceCode: string
  totalScore: number
  firstSubject: string
  provinceRank?: number
  referenceYear?: number
}): Promise<RawRankCheckResponse> {
  const res = await http.get<RawRankCheckResponse>('/volunteer/rank-check', { params })
  return res.data.data || {}
}

/** 生成志愿方案。必须已携带风险告知确认字段，否则后端返回 code=-1。 */
export async function generatePlan(form: VolunteerFormData): Promise<VolunteerPlan> {
  const res = await http.post<RawVolunteerPlan>('/volunteer/recommend', form, {
    timeout: GENERATE_TIMEOUT,
  })
  return normalizePlan(res.data.data || {})
}

/** 按 planId + 凭证恢复方案。凭证走请求头。 */
export async function fetchPlan(planId: number, safetyCode: string, accessKey: string): Promise<VolunteerPlan> {
  const res = await http.post<RawVolunteerPlan>(
    '/volunteer/plan',
    { planId },
    { headers: planAuthHeaders(safetyCode, accessKey) },
  )
  return normalizePlan(res.data.data || {})
}

export interface AiAnalysisResponse {
  content?: string
  sections?: Array<{ title?: string; content?: string }>
  generatedAt?: string
  [key: string]: unknown
}

/**
 * AI 深度解读。
 *
 * 不用 SSE：后端把上游 AI 的 delta 全部拼完才一次性发出，SSE 只多一次 ticket 往返；
 * 且 uni-app 在 App 端的 enableChunked / onChunkReceived 不可用。
 *
 * 注意后端对 AI 解读有单 IP 并发上限 2，UI 上必须禁止同时发起多个解读。
 */
export async function fetchAiAnalysis(
  planId: number,
  safetyCode: string,
  accessKey: string,
  forceRefresh = false,
): Promise<AiAnalysisResponse> {
  const res = await http.post<AiAnalysisResponse>(
    `/volunteer/plans/${planId}/ai-analysis`,
    { forceRefresh },
    { headers: planAuthHeaders(safetyCode, accessKey), timeout: LONG_TASK_TIMEOUT },
  )
  return res.data.data || {}
}

/** 张雪峰 skills 追问。UI 必须展示来源项目与免责说明。 */
export async function askSkills(
  planId: number,
  safetyCode: string,
  accessKey: string,
  question: string,
  aiReport?: string,
): Promise<{ answer?: string; sourceProjectName?: string; sourceProjectUrl?: string }> {
  const res = await http.post<{ answer?: string; sourceProjectName?: string; sourceProjectUrl?: string }>(
    `/volunteer/plans/${planId}/skills/ask`,
    { question, aiReport },
    { headers: planAuthHeaders(safetyCode, accessKey), timeout: LONG_TASK_TIMEOUT },
  )
  return res.data.data || {}
}

/** 服务端出长图。客户端不做 html2canvas 兜底。 */
export function exportLongImage(planId: number, safetyCode: string, accessKey: string) {
  return http.postBinary(
    `/volunteer/plans/${planId}/export-long-image`,
    {},
    { headers: planAuthHeaders(safetyCode, accessKey), timeout: LONG_TASK_TIMEOUT },
  )
}

export function exportExcel(planId: number, safetyCode: string, accessKey: string) {
  return http.postBinary(
    `/volunteer/plans/${planId}/export-excel`,
    {},
    { headers: planAuthHeaders(safetyCode, accessKey), timeout: LONG_TASK_TIMEOUT },
  )
}
