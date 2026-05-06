import type { GradientRangeKey, GradientRanges, VolunteerFormData, VolunteerItem, VolunteerPlan } from '@/types'
import { DISCLAIMER_VERSION } from '@/constants/compliance'
import { getProvinceConfig, normalizeProvinceCode } from '@/constants/provinces'

export type PlanMode = '保守型' | '均衡型' | '冲刺型'

export const GRADIENT_RANGE_PRESETS: Record<PlanMode, GradientRanges> = {
  保守型: {
    chong: { rankOffsetMin: -8000, rankOffsetMax: -3000 },
    wen: { rankOffsetMin: -3000, rankOffsetMax: 5000 },
    bao: { rankOffsetMin: 5000, rankOffsetMax: 15000 },
    dian: { rankOffsetMin: 15000, rankOffsetMax: 30000 },
  },
  均衡型: {
    chong: { rankOffsetMin: -10000, rankOffsetMax: -3000 },
    wen: { rankOffsetMin: -3000, rankOffsetMax: 4000 },
    bao: { rankOffsetMin: 4000, rankOffsetMax: 12000 },
    dian: { rankOffsetMin: 12000, rankOffsetMax: 26000 },
  },
  冲刺型: {
    chong: { rankOffsetMin: -15000, rankOffsetMax: -3000 },
    wen: { rankOffsetMin: -3000, rankOffsetMax: 3000 },
    bao: { rankOffsetMin: 3000, rankOffsetMax: 10000 },
    dian: { rankOffsetMin: 10000, rankOffsetMax: 25000 },
  },
}

const rangeKeys: GradientRangeKey[] = ['chong', 'wen', 'bao', 'dian']

export function cloneGradientRanges(ranges: GradientRanges): GradientRanges {
  return rangeKeys.reduce((acc, key) => {
    acc[key] = { ...ranges[key] }
    return acc
  }, {} as GradientRanges)
}

export function presetGradientRanges(mode: PlanMode): GradientRanges {
  return cloneGradientRanges(GRADIENT_RANGE_PRESETS[mode] || GRADIENT_RANGE_PRESETS.均衡型)
}

export function rangesFromPlanSummary(plan: VolunteerPlan): GradientRanges | undefined {
  const summary = plan.gradientRangeSummary
  if (!summary?.ranges) return undefined
  const map: Array<[GradientRangeKey, '冲' | '稳' | '保' | '垫']> = [
    ['chong', '冲'],
    ['wen', '稳'],
    ['bao', '保'],
    ['dian', '垫'],
  ]
  const ranges = presetGradientRanges(plan.strategyMode || '均衡型')
  map.forEach(([key, gradient]) => {
    const detail = summary.ranges[gradient]
    if (detail) {
      ranges[key] = {
        rankOffsetMin: detail.rankOffsetMin,
        rankOffsetMax: detail.rankOffsetMax,
      }
    }
  })
  return ranges
}

const gradientOrder: Record<string, number> = {
  冲: 0,
  稳: 1,
  保: 2,
  垫: 3,
}

function cloneItems(items: VolunteerItem[]): VolunteerItem[] {
  return items.map(item => ({ ...item }))
}

function fitScore(item: VolunteerItem): number {
  if (item.referenceFitLevel === '较高') return 90
  if (item.referenceFitLevel === '中等') return 70
  if (item.referenceFitLevel === '偏低') return 42
  if (item.referenceFitLevel === '需复核') return 28
  const chance = item.chanceScore || 0
  if (chance >= 75) return 82
  if (chance >= 50) return 64
  if (chance > 0) return 38
  return 30
}

function dataConfidence(item: VolunteerItem): number {
  if (typeof item.dataConfidenceScore === 'number') return item.dataConfidenceScore
  if (item.confidenceLabel === '高可信') return 85
  if (item.confidenceLabel === '中可信') return 65
  return item.dataSourceType === '专业级' || item.dataSourceType === '院校专业组' ? 58 : 42
}

function planSignalScore(item: VolunteerItem): number {
  let score = typeof item.latestPlanCount === 'number' && item.latestPlanCount > 0
    ? Math.min(12, Math.max(2, item.latestPlanCount / 3))
    : -6
  if (item.planTrend === '扩招') score += 8
  else if (item.planTrend === '缩招') score -= 10
  else if (item.planTrend === '基本稳定') score += 5
  else if (item.planTrend === '计划数暂缺') score -= 6
  if (typeof item.planExpansionIndex === 'number' && item.planExpansionIndex > 0) {
    score += Math.max(-8, Math.min(8, (item.planExpansionIndex - 100) / 8))
  }
  if (typeof item.schoolEnrollmentIndex === 'number' && item.schoolEnrollmentIndex > 0) {
    score += Math.max(-6, Math.min(6, (item.schoolEnrollmentIndex - 55) / 7))
  }
  return score
}

function backendRecommendationScore(item: VolunteerItem): number {
  return typeof item.recommendationScore === 'number' ? item.recommendationScore : 0
}

function ambitionScore(item: VolunteerItem): number {
  return backendRecommendationScore(item) * 0.6
    + fitScore(item) * 0.9
    + dataConfidence(item) * 0.35
    + (item.matchScore || 0) * 0.55
    + (item.precisionScore || 0) * 0.25
    + planSignalScore(item)
    + (item.dataSourceType === '专业级' ? 18 : 0)
    + (item.gradient === '冲' ? 12 : item.gradient === '稳' ? 6 : 0)
}

function safeScore(item: VolunteerItem): number {
  const riskBonus = item.riskColor === 'green' ? 16 : item.riskColor === 'yellow' ? 8 : 0
  const confidenceBonus = item.confidenceLabel === '高可信' ? 10 : item.confidenceLabel === '中可信' ? 5 : 0
  const planBonus = item.planTrend === '缩招' || item.planTrend === '计划数暂缺' ? -8 : planSignalScore(item)
  return backendRecommendationScore(item) * 0.7 + fitScore(item) * 0.95 + dataConfidence(item) * 0.45 + riskBonus + confidenceBonus + planBonus + (item.matchScore || 0) * 0.25 + (item.precisionScore || 0) * 0.25
}

function balancedScore(item: VolunteerItem): number {
  const confidenceBonus = item.dataSourceType === '专业级' ? 10 : 0
  return backendRecommendationScore(item) * 0.65 + fitScore(item) * 0.8 + dataConfidence(item) * 0.5 + (item.matchScore || 0) * 0.35 + (item.precisionScore || 0) * 0.3 + planSignalScore(item) + confidenceBonus
}

function sortGroup(items: VolunteerItem[], mode: PlanMode): VolunteerItem[] {
  const cloned = [...items]
  const scorer = mode === '冲刺型'
    ? ambitionScore
    : mode === '保守型'
      ? safeScore
      : balancedScore

  cloned.sort((a, b) => {
    const diff = scorer(b) - scorer(a)
    if (diff !== 0) return diff
    return (a.historyMinRank || Number.MAX_SAFE_INTEGER) - (b.historyMinRank || Number.MAX_SAFE_INTEGER)
  })

  return cloned
}

export function buildPlanModeItems(items: VolunteerItem[], mode: PlanMode): VolunteerItem[] {
  if (!items.length) return []
  if (mode === '均衡型') return cloneItems(items).sort((a, b) => a.index - b.index)

  const groups = new Map<string, VolunteerItem[]>()
  items.forEach((item) => {
    const key = item.gradient || '稳'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(item)
  })

  const order = mode === '保守型'
    ? ['稳', '保', '垫', '冲']
    : ['冲', '稳', '保', '垫']

  const reordered = order.flatMap((key) => sortGroup(groups.get(key) || [], mode))

  return reordered.map((item, idx) => ({
    ...item,
    index: idx + 1,
  }))
}

export function summarizePlan(items: VolunteerItem[]) {
  const total = items.length
  const avgChance = total
    ? Math.round(items.reduce((sum, item) => sum + (item.chanceScore || 0), 0) / total)
    : 0
  const matched = items.filter(item => (item.matchScore || 0) >= 60).length
  const risky = items.filter(item => item.riskColor === 'red' || (item.chanceScore || 0) < 40).length
  const majorLevel = items.filter(item => item.dataSourceType === '专业级').length
  const highFit = items.filter(item => item.referenceFitLevel === '较高' || fitScore(item) >= 80).length
  const avgConfidence = total
    ? Math.round(items.reduce((sum, item) => sum + dataConfidence(item), 0) / total)
    : 0
  const avgPrecision = total
    ? Math.round(items.reduce((sum, item) => sum + (item.precisionScore || 0), 0) / total)
    : 0
  const lowConfidence = items.filter(item => dataConfidence(item) < 60 || item.referenceFitLevel === '需复核').length
  return { total, avgChance, avgProb: avgChance, matched, risky, majorLevel, highFit, avgConfidence, avgPrecision, lowConfidence }
}

export function buildAiProfileSummary(formData: VolunteerFormData): string {
  const regionText = formData.preferredRegions.length ? formData.preferredRegions.join('、') : '不限地区'
  const majorText = formData.preferredMajors.length ? formData.preferredMajors.join('、') : '未指定专业'
  return [
    `方案风格：${formData.strategyMode}`,
    `省份：${getProvinceConfig(formData.provinceCode).shortName}`,
    `决策优先级：${formData.decisionPriority}`,
    `长期目标：${formData.careerGoal}`,
    `预算偏好：${formData.tuitionBudget}`,
    `地区偏好：${regionText}`,
    `专业偏好：${majorText}`,
    `接受民办：${formData.acceptPrivate ? '是' : '否'}`,
    `接受中外合作：${formData.acceptSinoForeign ? '是' : '否'}`,
  ].join('；')
}

export function formDataFromPlan(plan: VolunteerPlan): VolunteerFormData {
  return {
    provinceCode: normalizeProvinceCode(plan.provinceCode),
    totalScore: plan.totalScore,
    provinceRank: plan.provinceRank,
    firstSubject: normalizeFirstSubject(plan.firstSubject),
    resubjects: plan.resubjects || [],
    preferredMajors: plan.preferredMajors || [],
    preferredRegions: plan.preferredRegions || [],
    strategyMode: plan.strategyMode || '均衡型',
    decisionPriority: plan.decisionPriority || '专业优先',
    careerGoal: plan.careerGoal || '就业优先',
    tuitionBudget: plan.tuitionBudget || '均衡预算',
    acceptPrivate: plan.acceptPrivate ?? true,
    acceptSinoForeign: plan.acceptSinoForeign ?? false,
    agreedDisclaimer: true,
    disclaimerVersion: DISCLAIMER_VERSION,
    gradientRanges: rangesFromPlanSummary(plan),
  }
}

function normalizeFirstSubject(value?: string): VolunteerFormData['firstSubject'] {
  return value === '历史' || value === '历史类' || value === '文科' ? '历史' : '物理'
}

export function groupGradientCount(items: VolunteerItem[]) {
  return ['冲', '稳', '保', '垫'].map((key) => ({
    key,
    count: items.filter(item => item.gradient === key).length,
    order: gradientOrder[key] || 0,
  }))
}
