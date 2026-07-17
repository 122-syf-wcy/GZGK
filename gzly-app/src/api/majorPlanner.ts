import { httpGet, httpPost, request } from '@/utils/request'

export interface MajorPlannerEvaluateRequest {
  provinceCode?: string
  subjectCategory: string
  score?: number | null
  rank?: number | null
  likedSubjects: string[]
  dislikedSubjects: string[]
  interestDirections: string[]
  personalityTraits: string[]
  careerExpectations: string[]
  acceptMedicine?: boolean | null
  acceptTeacher?: boolean | null
  acceptAgriculture?: boolean | null
  acceptSinoForeign?: boolean | null
  acceptPrivate?: boolean | null
  familyBudget?: string
  cityPreferences: string[]
  avoidDirections: string[]
}

export interface MajorPlannerRadarItem {
  category: string
  score: number
}

export interface MajorPlannerScoredMajor {
  category: string
  majorName: string
  matchScore: number
  reasons: string[]
  learningContent: string
  suitableFor: string
  employmentDirections: string
  postgraduateAndCivil: string
  risks: string[]
  advice: string
  subjectRequirement: string
  constraintWarnings?: string[]
}

export interface MajorPlannerNotRecommended {
  direction: string
  reason: string
  advice: string
}

export interface MajorPlannerProfile {
  subjectCategory: string
  mainLine?: string
  backupLine?: string
}

export interface MajorPlannerEvaluationResult {
  profile: MajorPlannerProfile
  radar: MajorPlannerRadarItem[]
  topMajors: MajorPlannerScoredMajor[]
  notRecommended: MajorPlannerNotRecommended[]
  ruleSummary: string
  disclaimer: string
  generatedAt?: string
}

export interface MajorPlannerView {
  id: number
  planNo: string
  planCode?: string
  planCodeMasked: string
  planCodeVisibleOnce: boolean
  provinceCode?: string
  subjectCategory: string
  result: MajorPlannerEvaluationResult
  aiSummary?: string
  aiGenerated?: boolean
  disclaimer: string
  createdAt?: string
}

export interface MajorPlannerAiAnalysisResult {
  content: string
  generated: boolean
  fallbackUsed: boolean
  fallbackReason?: string
}

export function evaluateMajorPlanner(data: MajorPlannerEvaluateRequest) {
  return request<MajorPlannerView>('/major-planner/evaluate', { method: 'POST', data, timeout: 30000 })
}

export function restoreMajorPlanner(planCode: string, planNo?: string) {
  return httpPost<MajorPlannerView>('/major-planner/restore', { planCode, planNo })
}

export function fetchMajorPlannerResult(id: number, planCode: string) {
  return request<MajorPlannerView>(`/major-planner/results/${id}`, {
    method: 'GET',
    header: { 'X-Major-Plan-Code': planCode },
    timeout: 15000,
  })
}

export function generateMajorPlannerAiAnalysis(id: number, planCode: string, forceRefresh = false) {
  return request<MajorPlannerAiAnalysisResult>(`/major-planner/results/${id}/ai-analysis`, {
    method: 'POST',
    data: { planCode, forceRefresh },
    timeout: 120000,
  })
}
