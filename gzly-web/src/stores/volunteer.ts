import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  ManualReviewItem,
  PlanMetrics,
  RankEstimateSummary,
  RecommendationPhase,
  SupportLevel,
  VolunteerFormData,
  VolunteerItem,
  VolunteerPlan,
  AdvisorAdvice,
} from '@/types'
import { REFERENCE_PROBABILITY_NOTICE } from '@/constants/compliance'

const PLAN_META_KEY = 'gz_volunteer_plan_meta'

type PlanMeta = {
  planId: number
  safetyCode?: string
  accessKey?: string
}

export const useVolunteerStore = defineStore('volunteer', () => {
  const formData = ref<VolunteerFormData>({
    provinceCode: 'GZ',
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
  const warnings = ref<string[]>([])
  const recommendationPhase = ref<RecommendationPhase | ''>('')
  const supportLevel = ref<SupportLevel | ''>('')
  const estimateMode = ref(false)
  const officialDataReady = ref(false)
  const modelRetrained = ref(false)
  const targetYear = ref<number | null>(null)
  const activeAdmissionYear = ref<number | null>(null)
  const latestOfficialDataYear = ref<number | null>(null)
  const dataSourceYears = ref<number[]>([])
  const aiContent = ref('')
  const generating = ref(false)

  function setFormData(data: Partial<VolunteerFormData>) {
    formData.value = { ...formData.value, ...data }
  }

  /** 把后端返回的方案完整写入 store；兼容老版本只传 items 的调用点。 */
  function setPlanFromResponse(plan: VolunteerPlan) {
    planId.value = plan.id
    const credential = plan.safetyCode || plan.accessKey || ''
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
    warnings.value = plan.warnings || []
    recommendationPhase.value = plan.recommendationPhase || ''
    supportLevel.value = plan.supportLevel || ''
    estimateMode.value = plan.estimateMode === true
    officialDataReady.value = plan.officialDataReady === true
    modelRetrained.value = plan.modelRetrained === true
    targetYear.value = plan.targetYear ?? null
    activeAdmissionYear.value = plan.activeAdmissionYear ?? null
    latestOfficialDataYear.value = plan.latestOfficialDataYear ?? null
    dataSourceYears.value = plan.dataSourceYears || []
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
    warnings.value = []
    recommendationPhase.value = ''
    supportLevel.value = ''
    estimateMode.value = false
    officialDataReady.value = false
    modelRetrained.value = false
    targetYear.value = null
    activeAdmissionYear.value = null
    latestOfficialDataYear.value = null
    dataSourceYears.value = []
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
    warnings,
    recommendationPhase,
    supportLevel,
    estimateMode,
    officialDataReady,
    modelRetrained,
    targetYear,
    activeAdmissionYear,
    latestOfficialDataYear,
    dataSourceYears,
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
