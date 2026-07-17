import { httpGet, httpPost, request } from '@/utils/request'
import { BASE_URL } from '@/config/env'
import type { VolunteerPlan, RankCheckResponse, AiAnalysisResponse } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

export interface BatchSupportItem {
  batchCode?: string
  batchName?: string
  supportLevel?: string
  recommendModeName?: string
  supportNote?: string
}
export interface BatchSupportResponse {
  provinceCode: string
  recommendationPhase?: string
  estimateMode?: boolean
  officialDataReady?: boolean
  targetYear?: number
  historyYears?: number[]
  items?: BatchSupportItem[]
}

/** 省份批次支持矩阵（省份专区就绪度） */
export function getBatchSupportByProvince(code: string) {
  return httpGet<BatchSupportResponse>(`/volunteer/${code.toLowerCase()}/batch-support`)
}

/** 导出志愿长图（后端渲染 PNG），返回 base64 data-uri 供 image 展示/保存 */
export function exportPlanLongImage(planId: number, safetyCode: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/volunteer/plans/${planId}/export-long-image`,
      method: 'POST',
      data: { safetyCode },
      responseType: 'arraybuffer',
      header: { 'Content-Type': 'application/json', 'X-Safety-Code': safetyCode },
      timeout: 90000,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`导出失败（${res.statusCode}）`))
          return
        }
        try {
          const b64 = uni.arrayBufferToBase64(res.data as ArrayBuffer)
          resolve('data:image/png;base64,' + b64)
        } catch (e) {
          reject(new Error('图片解析失败'))
        }
      },
      fail: (e) => reject(new Error(e.errMsg || '导出失败')),
    })
  })
}

export interface GenerateVolunteerPayload {
  provinceCode?: ProvinceCode
  totalScore: number
  provinceRank: number
  firstSubject: '物理' | '历史'
  resubjects: string[]
  strategyMode?: '保守型' | '均衡型' | '冲刺型'
  decisionPriority?: '学校优先' | '专业优先'
  careerGoal?: '就业优先' | '升学优先' | '城市机会优先'
  tuitionBudget?: '低预算' | '均衡预算' | '不限制'
  acceptPrivate?: boolean
  acceptSinoForeign?: boolean
  safetyCode: string
  agreedDisclaimer: true
  disclaimerVersion: string
}

/** 生成志愿方案（多省，需 safetyCode + 免责确认） */
export function generateVolunteerPlan(data: GenerateVolunteerPayload) {
  return request<VolunteerPlan>('/volunteer/recommend', { method: 'POST', data, timeout: 90000 })
}

/** 位次区间校验 */
export function rankCheck(params: {
  provinceCode?: ProvinceCode
  totalScore: number
  provinceRank?: number
  firstSubject: '物理' | '历史'
}) {
  return httpGet<RankCheckResponse>('/volunteer/rank-check', params)
}

/** 凭 planId + safetyCode 找回方案 */
export function fetchVolunteerPlan(planId: number, safetyCode: string) {
  return httpPost<VolunteerPlan>('/volunteer/plan', { planId, safetyCode })
}

/** 志愿 AI 解读（JSON 结构化版，非 SSE，适配 App 端） */
export function generateAiAnalysis(planId: number, safetyCode: string, forceRefresh = false) {
  return request<AiAnalysisResponse>(`/volunteer/plans/${planId}/ai-analysis`, {
    method: 'POST',
    data: { safetyCode, forceRefresh },
    timeout: 90000,
  })
}

export function getAiAnalysis(planId: number, safetyCode: string) {
  return httpGet<AiAnalysisResponse>(`/volunteer/plans/${planId}/ai-analysis`, { safetyCode })
}

/* 张雪峰 Skills 追问 */
export interface ZxfSkillChatMessage {
  role: 'user' | 'assistant'
  content: string
}
export interface ZxfSkillChatResponse {
  reply?: string
  answer?: string
  sourceChunks?: Array<Record<string, unknown>>
  referencedVolunteers?: Array<Record<string, unknown>>
  disclaimer?: string
}
export function chatZxfSkill(data: {
  planId: number
  safetyCode: string
  message: string
  aiReport?: string
  messages?: ZxfSkillChatMessage[]
}) {
  return request<ZxfSkillChatResponse>(`/volunteer/plans/${data.planId}/skills/ask`, {
    method: 'POST',
    data: {
      safetyCode: data.safetyCode,
      question: data.message,
      aiReport: data.aiReport,
      messages: data.messages,
    },
    timeout: 90000,
  })
}
