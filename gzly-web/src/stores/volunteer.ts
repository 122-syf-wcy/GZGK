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

type PlanMeta = {
  planId: number
  accessKey: string
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
  const planAccessKey = ref('')
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
  const planMeta = ref<{
    provinceCode?: string
    targetYear?: number
    dataSourceYears?: number[]
    recommendationPhase?: string
    estimateMode?: boolean
    supportLevel?: string
    recommendMode?: string
    engineName?: string
    supportReason?: string
    diagnosis?: Record<string, unknown>
  }>({})
  const aiContent = ref('')
  const generating = ref(false)

  function setFormData(data: Partial<VolunteerFormData>) {
    formData.value = { ...formData.value, ...data }
  }

  /** 把后端返回的方案完整写入 store；兼容老版本只传 items 的调用点。 */
  function setPlanFromResponse(plan: VolunteerPlan) {
    const savedMeta = getSavedPlanMeta()
    const existingAccessKey = planId.value === plan.id ? planAccessKey.value : ''
    const savedAccessKey = savedMeta?.planId === plan.id ? savedMeta.accessKey : ''
    const nextAccessKey = plan.accessKey || existingAccessKey || savedAccessKey || ''
    planId.value = plan.id
    planAccessKey.value = nextAccessKey
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
    planMeta.value = {
      provinceCode: plan.provinceCode,
      targetYear: plan.targetYear,
      dataSourceYears: plan.dataSourceYears,
      recommendationPhase: plan.recommendationPhase,
      estimateMode: plan.estimateMode,
      supportLevel: plan.supportLevel,
      recommendMode: plan.recommendMode,
      engineName: plan.engineName,
      supportReason: plan.supportReason,
      diagnosis: plan.diagnosis,
    }
    aiContent.value = ''
    if (nextAccessKey) {
      localStorage.setItem(PLAN_META_KEY, JSON.stringify({ planId: plan.id, accessKey: nextAccessKey }))
    }
  }

  /** 兼容旧调用：仅写入核心字段。新代码请使用 setPlanFromResponse。 */
  function setPlanResult(id: number, items: VolunteerItem[], accessKey = '', warning = '') {
    planId.value = id
    planAccessKey.value = accessKey
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
    planMeta.value = {}
    aiContent.value = ''
    localStorage.setItem(PLAN_META_KEY, JSON.stringify({ planId: id, accessKey }))
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
    planAccessKey.value = ''
    dataQualityWarning.value = ''
    manualReviewItems.value = []
    planMetrics.value = null
    policy.value = null
    modelInfo.value = null
    warnings.value = []
    planMeta.value = {}
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
      if (!parsed.planId || !parsed.accessKey) return null
      return parsed
    } catch {
      return null
    }
  }

  return {
    formData,
    planItems,
    planId,
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
    planMeta,
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
