import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  ManualReviewItem,
  PlanMetrics,
  RankEstimateSummary,
  VolunteerFormData,
  VolunteerItem,
  VolunteerPlan,
  AdvisorAdvice,
} from '@/types'
import { REFERENCE_PROBABILITY_NOTICE } from '@/constants/compliance'

const PLAN_META_KEY = 'gz_volunteer_plan_meta'
const CURRENT_SAFETY_CODE_KEY = 'gz_current_safety_code'

type PlanMeta = {
  planId: number
  safetyCode?: string
  accessKey?: string
}

export function getStoredSafetyCode(planId?: number | string | null): string {
  try {
    const raw = localStorage.getItem(PLAN_META_KEY)
    if (!raw) return ''
    const parsed = JSON.parse(raw) as PlanMeta
    if (planId && String(parsed.planId) !== String(planId)) return ''
    return parsed.safetyCode || parsed.accessKey || ''
  } catch {
    return ''
  }
}

export function persistSafetyCode(planId: number | string, safetyCode: string) {
  if (!planId || !safetyCode) return
  localStorage.setItem(PLAN_META_KEY, JSON.stringify({ planId: Number(planId), safetyCode, accessKey: safetyCode }))
}

export function clearStoredSafetyCode(planId?: number | string | null) {
  if (planId && !getStoredSafetyCode(planId)) return
  localStorage.removeItem(PLAN_META_KEY)
}

export function getCurrentSafetyCode(): string {
  return localStorage.getItem(CURRENT_SAFETY_CODE_KEY) || getStoredSafetyCode()
}

export function setCurrentSafetyCode(safetyCode: string) {
  if (!safetyCode) {
    clearCurrentSafetyCode()
    return
  }
  localStorage.setItem(CURRENT_SAFETY_CODE_KEY, safetyCode)
}

export function clearCurrentSafetyCode() {
  localStorage.removeItem(CURRENT_SAFETY_CODE_KEY)
}

export const useVolunteerStore = defineStore('volunteer', () => {
  const formData = ref<VolunteerFormData>({
    provinceCode: 'GZ',
    candidateType: '普通类',
    batchCode: '',
    totalScore: 0,
    provinceRank: 0,
    firstSubject: '物理',
    resubjects: [],
    preferredMajors: [],
    preferredRegions: [],
    strategyMode: '均衡型',
    decisionPriority: '专业优先',
    careerGoal: '就业优先',
    tuitionBudget: '均衡预算',
    acceptPrivate: true,
    acceptSinoForeign: false,
    agreedDisclaimer: false,
    disclaimerVersion: '',
    gradientRanges: undefined,
  })

  const planItems = ref<VolunteerItem[]>([])
  const planId = ref<number | null>(null)
  const planSafetyCode = ref('')
  const planAccessKey = planSafetyCode
  const dataQualityWarning = ref('')
  const manualReviewItems = ref<ManualReviewItem[]>([])
  const planMetrics = ref<PlanMetrics | null>(null)
  const referenceProbabilityNotice = ref<string>(REFERENCE_PROBABILITY_NOTICE)
  const gradientRangeSummary = ref<VolunteerPlan['gradientRangeSummary'] | null>(null)
  const rankEstimate = ref<RankEstimateSummary | null>(null)
  const advisorAdvice = ref<AdvisorAdvice | null>(null)
  const policy = ref<VolunteerPlan['policy'] | null>(null)
  const modelInfo = ref<VolunteerPlan['modelInfo'] | null>(null)
  const activeAdmissionYear = ref<number | null>(null)
  const latestOfficialDataYear = ref<number | null>(null)
  const trainingYears = ref<number[]>([])
  const recommendationPhase = ref('')
  const estimateMode = ref(false)
  const officialDataReady = ref(false)
  const warnings = ref<string[]>([])
  const aiContent = ref('')
  const generating = ref(false)

  function setFormData(data: Partial<VolunteerFormData>) {
    formData.value = { ...formData.value, ...data }
  }

  /** 把后端返回的方案完整写入 store；兼容老版本只传 items 的调用点。 */
  function setPlanFromResponse(plan: VolunteerPlan, fallbackCredential = '') {
    planId.value = plan.id
    const credential = plan.safetyCode || plan.accessKey || fallbackCredential || getStoredSafetyCode(plan.id)
    planSafetyCode.value = credential
    planItems.value = plan.items || []
    dataQualityWarning.value = plan.dataQualityWarning || ''
    manualReviewItems.value = plan.manualReviewItems || []
    planMetrics.value = plan.metrics ?? null
    referenceProbabilityNotice.value = plan.referenceProbabilityNotice || REFERENCE_PROBABILITY_NOTICE
    gradientRangeSummary.value = plan.gradientRangeSummary ?? null
    rankEstimate.value = plan.rankEstimate ?? null
    advisorAdvice.value = plan.advisorAdvice ?? null
    policy.value = plan.policy ?? null
    modelInfo.value = plan.modelInfo ?? null
    activeAdmissionYear.value = plan.activeAdmissionYear || plan.modelInfo?.activeAdmissionYear || null
    latestOfficialDataYear.value = plan.latestOfficialDataYear || plan.modelInfo?.latestOfficialDataYear || null
    trainingYears.value = plan.trainingYears || plan.modelInfo?.trainingYears || []
    recommendationPhase.value = plan.recommendationPhase || plan.modelInfo?.recommendationPhase || ''
    estimateMode.value = Boolean(plan.estimateMode || plan.modelInfo?.estimateMode)
    officialDataReady.value = Boolean(plan.officialDataReady || plan.modelInfo?.officialDataReady)
    warnings.value = plan.warnings || []
    aiContent.value = ''
    localStorage.setItem(PLAN_META_KEY, JSON.stringify({ planId: plan.id, safetyCode: credential, accessKey: plan.accessKey || '' }))
  }

  /** 兼容旧调用：仅写入核心字段。新代码请使用 setPlanFromResponse。 */
  function setPlanResult(id: number, items: VolunteerItem[], safetyCode = '', warning = '') {
    planId.value = id
    planSafetyCode.value = safetyCode
    planItems.value = items
    dataQualityWarning.value = warning
    manualReviewItems.value = []
    planMetrics.value = null
    referenceProbabilityNotice.value = REFERENCE_PROBABILITY_NOTICE
    gradientRangeSummary.value = null
    rankEstimate.value = null
    advisorAdvice.value = null
    policy.value = null
    modelInfo.value = null
    activeAdmissionYear.value = null
    latestOfficialDataYear.value = null
    trainingYears.value = []
    recommendationPhase.value = ''
    estimateMode.value = false
    officialDataReady.value = false
    warnings.value = []
    aiContent.value = ''
    localStorage.setItem(PLAN_META_KEY, JSON.stringify({ planId: id, safetyCode }))
  }

  function setAiContent(content: string) {
    aiContent.value = content
  }

  function appendAiContent(chunk: string) {
    aiContent.value += chunk
  }

  function clearPlan() {
    planItems.value = []
    planId.value = null
    planSafetyCode.value = ''
    dataQualityWarning.value = ''
    manualReviewItems.value = []
    planMetrics.value = null
    policy.value = null
    modelInfo.value = null
    activeAdmissionYear.value = null
    latestOfficialDataYear.value = null
    trainingYears.value = []
    recommendationPhase.value = ''
    estimateMode.value = false
    officialDataReady.value = false
    warnings.value = []
    aiContent.value = ''
    gradientRangeSummary.value = null
    rankEstimate.value = null
    advisorAdvice.value = null
    localStorage.removeItem(PLAN_META_KEY)
  }

  function getSavedPlanMeta(): PlanMeta | null {
    try {
      const raw = localStorage.getItem(PLAN_META_KEY)
      if (!raw) return null
      const parsed = JSON.parse(raw) as PlanMeta
      if (!parsed.planId || !(parsed.safetyCode || parsed.accessKey)) return null
      return parsed
    } catch {
      return null
    }
  }

  return {
    formData,
    planItems,
    planId,
    planSafetyCode,
    planAccessKey,
    dataQualityWarning,
    manualReviewItems,
    planMetrics,
    referenceProbabilityNotice,
    gradientRangeSummary,
    rankEstimate,
    advisorAdvice,
    policy,
    modelInfo,
    activeAdmissionYear,
    latestOfficialDataYear,
    trainingYears,
    recommendationPhase,
    estimateMode,
    officialDataReady,
    warnings,
    aiContent,
    generating,
    setFormData,
    setPlanResult,
    setPlanFromResponse,
    setAiContent,
    appendAiContent,
    clearPlan,
    getSavedPlanMeta,
  }
})
