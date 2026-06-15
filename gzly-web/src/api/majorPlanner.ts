import http from './request'
import type { Result } from '@/types'

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
  provinceCode?: string
  subjectCategory: string
  score?: number | null
  rank?: number | null
  likedSubjects: string[]
  dislikedSubjects: string[]
  interestDirections: string[]
  personalityTraits: string[]
  careerExpectations: string[]
  familyBudget?: string
  cityPreferences: string[]
  avoidDirections: string[]
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
  score?: number | null
  rank?: number | null
  result: MajorPlannerEvaluationResult
  aiSummary?: string
  aiGenerated?: boolean
  aiFallbackReason?: string
  disclaimer: string
  createdAt?: string
}

export interface MajorPlannerRestoreRequest {
  planNo?: string
  planCode: string
}

export interface MajorPlannerAiAnalysisResult {
  content: string
  generated: boolean
  fallbackUsed: boolean
  fallbackReason?: string
}

export async function evaluateMajorPlanner(data: MajorPlannerEvaluateRequest): Promise<MajorPlannerView> {
  const res = await http.post<Result<MajorPlannerView>>('/major-planner/evaluate', data, { timeout: 30000 })
  return res.data.data
}

export async function restoreMajorPlanner(data: MajorPlannerRestoreRequest): Promise<MajorPlannerView> {
  const res = await http.post<Result<MajorPlannerView>>('/major-planner/restore', data, { timeout: 15000 })
  return res.data.data
}

export async function fetchMajorPlannerResult(id: number, planCode: string): Promise<MajorPlannerView> {
  const res = await http.get<Result<MajorPlannerView>>(`/major-planner/results/${id}`, {
    headers: { 'X-Major-Plan-Code': planCode },
    timeout: 15000,
  })
  return res.data.data
}

export async function generateMajorPlannerAiAnalysis(
  id: number,
  planCode: string,
  forceRefresh = false,
): Promise<MajorPlannerAiAnalysisResult> {
  const res = await http.post<Result<MajorPlannerAiAnalysisResult>>(
    `/major-planner/results/${id}/ai-analysis`,
    { planCode, forceRefresh },
    { timeout: 120000 },
  )
  return res.data.data
}
