<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import {
  chatZxfSkill,
  fetchVolunteerPlan,
  generateAiAnalysis,
  type AiAnalysisResponse,
  type ZxfSkillChatMessage,
} from '@/api/volunteer'
import { fetchMyPlanDetail } from '@/api/myPlans'
import { useVolunteerStore } from '@/stores/volunteer'
import { useAuthStore } from '@/stores/auth'
import RecommendSection from '@/components/RecommendSection.vue'
import PlanRestoreDialog from '@/components/PlanRestoreDialog.vue'
import SafeExternalLink from '@/components/SafeExternalLink.vue'
import type { GradientRangeDetail, VolunteerItem, VolunteerPlan } from '@/types'
import { buildPlanModeItems, formDataFromPlan, summarizePlan, type PlanMode } from '@/utils/volunteer-plan'
import { renderMarkdown, sanitizeHttpUrl } from '@/utils/markdown'
import DisclaimerNotice from '@/components/DisclaimerNotice.vue'
import { AI_ANALYSIS_NOTICE, RESULT_NOTICE } from '@/constants/disclaimer'
import { getProvinceConfig, normalizeProvinceCode } from '@/constants/provinces'
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  ChevronDown,
  ClipboardCheck,
  Download,
  ExternalLink,
  FileSpreadsheet,
  Info,
  MapPin,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  SplitSquareVertical,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const volunteerStore = useVolunteerStore()
const authStore = useAuthStore()

const activeTab = ref<'all' | '冲' | '稳' | '保' | '垫'>('all')
const activeMode = ref<PlanMode>(volunteerStore.formData.strategyMode || '均衡型')
const compareIds = ref<string[]>([])
const restoring = ref(false)
const visibleCount = ref(12)
const manualReviewExpanded = ref(false)
const showAllReview = ref(false)
const expandedRows = ref<Set<string>>(new Set())
const aiPanelRef = ref<HTMLElement | null>(null)
const aiAnalysis = ref<AiAnalysisResponse | null>(null)
const aiLoading = ref(false)
const aiError = ref('')
const aiDeepOpen = ref(false)
const skillInput = ref('')
const skillLoading = ref(false)
const skillError = ref('')
const skillMessages = ref<ZxfSkillChatMessage[]>([])
const showPlanRestore = ref(false)
type PlanRowLike = Pick<VolunteerItem, 'majorName' | 'index'> & Pick<Partial<VolunteerItem>, 'schoolId' | 'groupCode'>

/** 志愿草稿状态：保留 / 待查 / 淘汰 / 默认。本地持久化。 */
type DraftStatus = 'keep' | 'review' | 'drop' | 'default'
const STATUS_STORAGE_KEY = computed(() => `gz_volunteer_status_${volunteerStore.planId ?? 0}`)
const itemStatuses = ref<Record<string, DraftStatus>>(loadStatuses())

function statusKey(item: PlanRowLike) {
  return `${item.schoolId || ''}|${item.groupCode || ''}|${item.majorName}|${item.index}`
}

function loadStatuses(): Record<string, DraftStatus> {
  try {
    const raw = localStorage.getItem(STATUS_STORAGE_KEY.value)
    if (!raw) return {}
    return JSON.parse(raw) as Record<string, DraftStatus>
  } catch {
    return {}
  }
}

watch(
  () => volunteerStore.planId,
  () => {
    itemStatuses.value = loadStatuses()
    expandedRows.value = new Set()
  },
)

function persistStatuses() {
  try {
    localStorage.setItem(STATUS_STORAGE_KEY.value, JSON.stringify(itemStatuses.value))
  } catch {
    // 忽略存储失败
  }
}

const STATUS_CYCLE: DraftStatus[] = ['default', 'keep', 'review', 'drop']
const STATUS_LABEL: Record<DraftStatus, string> = {
  default: '标记状态',
  keep: '✓ 保留',
  review: '⚠ 待查',
  drop: '× 淘汰',
}

function nextStatus(current: DraftStatus): DraftStatus {
  const idx = STATUS_CYCLE.indexOf(current)
  return STATUS_CYCLE[(idx + 1) % STATUS_CYCLE.length]
}

function cycleStatus(item: PlanRowLike) {
  const key = statusKey(item)
  const current = itemStatuses.value[key] || 'default'
  const next = nextStatus(current)
  if (next === 'default') {
    delete itemStatuses.value[key]
  } else {
    itemStatuses.value[key] = next
  }
  persistStatuses()
}

function statusOf(item: PlanRowLike): DraftStatus {
  return itemStatuses.value[statusKey(item)] || 'default'
}

function isExpanded(item: PlanRowLike) {
  return expandedRows.value.has(statusKey(item))
}

function toggleDetail(item: PlanRowLike) {
  const key = statusKey(item)
  const next = new Set(expandedRows.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedRows.value = next
}

const statusSummary = computed(() => {
  const counts = { keep: 0, review: 0, drop: 0 }
  const statuses = Object.values(itemStatuses.value) as DraftStatus[]
  statuses.forEach((s) => {
    if (s in counts) counts[s as keyof typeof counts]++
  })
  return counts
})

const visibleManualReviews = computed(() => {
  const list = volunteerStore.manualReviewItems
  if (!list || !list.length) return []
  if (!manualReviewExpanded.value) return []
  return showAllReview.value ? list : list.slice(0, 5)
})

const manualReviewPreview = computed(() => (
  (volunteerStore.manualReviewItems || []).slice(0, 3)
))

const highRiskReviewCount = computed(() => (
  (volunteerStore.manualReviewItems || []).filter((entry) => (
    /高|红|必须|缺|限制|军警|医学|体检|单科|语种/.test([
      entry.confidenceLabel,
      entry.dataSourceType,
      entry.subjectRequirementSource,
      ...(entry.reasons || []),
    ].filter(Boolean).join(' '))
  )).length
))

function toggleManualReview() {
  manualReviewExpanded.value = !manualReviewExpanded.value
  if (!manualReviewExpanded.value) showAllReview.value = false
}

const gradientConfig: Record<string, { color: string; bg: string }> = {
  冲: { color: '#dc2626', bg: '#fef2f2' },
  稳: { color: '#2563eb', bg: '#eff6ff' },
  保: { color: '#059669', bg: '#ecfdf5' },
  垫: { color: '#d97706', bg: '#fffbeb' },
}

/**
 * 解析 4 段式 recommendReason：后端用 " | " 把"梯度定位 / 适配理由 / 风险信号 / 可调节项"
 * 拼接为单字符串透出；前端按前缀拆开后渲染成结构化卡片，遗留单段文本走兜底。
 */
interface ReasonSegment {
  key: 'gradient' | 'fit' | 'risk' | 'adjust'
  label: string
  body: string
}
const REASON_SEGMENT_LABELS: ReasonSegment[] = [
  { key: 'gradient', label: '梯度定位', body: '' },
  { key: 'fit', label: '适配理由', body: '' },
  { key: 'risk', label: '风险信号', body: '' },
  { key: 'adjust', label: '可调节项', body: '' },
]
function parseRecommendReason(raw?: string): ReasonSegment[] {
  if (!raw) return []
  const parts = raw.split(' | ').map((p) => p.trim()).filter(Boolean)
  if (!parts.length) return []
  const map = new Map<string, string>()
  for (const part of parts) {
    const sep = part.indexOf('：')
    if (sep > 0) {
      map.set(part.slice(0, sep).trim(), part.slice(sep + 1).trim())
    }
  }
  if (map.size === 0) {
    return [{ key: 'gradient', label: '说明', body: raw }]
  }
  return REASON_SEGMENT_LABELS
    .map((seg) => ({ ...seg, body: map.get(seg.label) || '' }))
    .filter((seg) => seg.body)
}
const REASON_SEGMENT_TONE: Record<ReasonSegment['key'], string> = {
  gradient: 'reason-segment--gradient',
  fit: 'reason-segment--fit',
  risk: 'reason-segment--risk',
  adjust: 'reason-segment--adjust',
}

/** 自动调平摘要展示。 */
const autoRebalanceSummary = computed(() => {
  const m = volunteerStore.planMetrics
  if (!m || !m.autoRebalanceCount || m.autoRebalanceCount <= 0) return null
  const before = typeof m.autoRebalanceBeforeProbability === 'number' ? m.autoRebalanceBeforeProbability : null
  const after = typeof m.autoRebalanceAfterProbability === 'number' ? m.autoRebalanceAfterProbability : null
  const improvement = before !== null && after !== null ? Math.max(0, after - before) : null
  return {
    count: m.autoRebalanceCount,
    before,
    after,
    improvement,
    note: m.autoRebalanceNote || '',
  }
})

const modeItems = computed<VolunteerItem[]>(() => buildPlanModeItems(volunteerStore.planItems, activeMode.value))

const decisionDraftItems = computed<VolunteerItem[]>(() =>
  modeItems.value.filter((item: VolunteerItem) => {
    const status = statusOf(item)
    return status === 'keep' || status === 'review'
  }),
)

const summary = computed(() => summarizePlan(modeItems.value))
const gradientCounts = computed(() => ({
  冲: modeItems.value.filter((item: VolunteerItem) => item.gradient === '冲').length,
  稳: modeItems.value.filter((item: VolunteerItem) => item.gradient === '稳').length,
  保: modeItems.value.filter((item: VolunteerItem) => item.gradient === '保').length,
  垫: modeItems.value.filter((item: VolunteerItem) => item.gradient === '垫').length,
}))
const rangeSummary = computed(() => volunteerStore.gradientRangeSummary)
const rankEstimateSummary = computed(() => volunteerStore.rankEstimate)
const topNoticeItems = computed(() => {
  const items: Array<{ key: string; tone: 'warning' | 'info' | 'credential'; text: string }> = []
  if (restoring.value) {
    items.push({ key: 'restoring', tone: 'info', text: '正在恢复已生成的志愿方案...' })
  }
  if (volunteerStore.dataQualityWarning) {
    items.push({ key: 'data-quality', tone: 'warning', text: volunteerStore.dataQualityWarning })
  }
  volunteerStore.warnings.forEach((warning, index) => {
    items.push({ key: `warning-${index}`, tone: 'warning', text: warning })
  })
  if (yearPhaseNotice.value) {
    items.push({ key: 'year-phase', tone: 'warning', text: yearPhaseNotice.value })
  }
  if (rankEstimateSummary.value) {
    items.push({
      key: 'rank-estimate',
      tone: rankEstimateSummary.value.rankEstimated ? 'warning' : 'info',
      text: rankEstimateSummary.value.reminder || rankEstimateSummary.value.note || '位次信息为历史估算参考，请结合官方一分一段复核。',
    })
  }
  if (volunteerStore.referenceProbabilityNotice) {
    items.push({ key: 'reference-probability', tone: 'info', text: volunteerStore.referenceProbabilityNotice })
  }
  if (volunteerStore.planSafetyCode) {
    items.push({
      key: 'plan-safety-code',
      tone: 'credential',
      text: `方案 ID：${volunteerStore.planId}；方案查看凭证已通过本机验证。结果页不会再次明文展示凭证，跨设备查看请使用生成时保存的凭证。`,
    })
  }
  return items
})
const rangeSummaryRows = computed(() => {
  const ranges = rangeSummary.value?.ranges
  if (!ranges) return [] as GradientRangeDetail[]
  return (['冲', '稳', '保', '垫'] as const)
    .flatMap(key => ranges[key] ? [ranges[key] as GradientRangeDetail] : [])
})

/** 招生类型枚举显示文案，与 com.gzly.algorithm.RecruitTypeClassifier 常量一一对应。 */
const RECRUIT_TYPE_LABELS: Record<string, string> = {
  NORMAL: '普通批次',
  ART_SPORTS: '艺术 / 体育类',
  GENDER_RESTRICTED: '性别限制',
  FREE_NORMAL: '免费师范生',
  DIRECTED: '定向 / 委培',
  SUPPLEMENT: '本科预科 / 补录',
}
const recruitTypeRows = computed(() => {
  const breakdown = volunteerStore.planMetrics?.recruitTypeBreakdown
  if (!breakdown) return [] as Array<{ key: string; label: string; count: number; percent: string; warn: boolean }>
  const entries = Object.entries(breakdown)
    .map(([key, count]) => ({ key, count: typeof count === 'number' ? count : Number(count || 0) }))
    .filter(row => row.count > 0)
  const total = entries.reduce((sum, row) => sum + row.count, 0)
  if (!total) return []
  return entries
    .sort((a, b) => b.count - a.count)
    .map(({ key, count }) => ({
      key,
      label: RECRUIT_TYPE_LABELS[key] ?? key,
      count,
      percent: `${((count / total) * 100).toFixed(1)}%`,
      warn: key !== 'NORMAL',
    }))
})

/** 监控阈值告警提示：当 overRisk / firstTwentyHit 突破 strategyMode 自适应基线时给出文案。 */
const metricsBaselineNote = computed(() => {
  const m = volunteerStore.planMetrics
  if (!m) return ''
  const segments: string[] = []
  if (m.overRiskExposureBreached && typeof m.overRiskExposure === 'number' && typeof m.overRiskExposureBaseline === 'number') {
    segments.push(`前 20 高风险占比 ${(m.overRiskExposure * 100).toFixed(0)}%（${m.strategyMode || '均衡型'} 基线 ${(m.overRiskExposureBaseline * 100).toFixed(0)}%），建议核对冲档分布是否过多`)
  }
  if (m.firstTwentyHitRateBreached && typeof m.firstTwentyHitRate === 'number' && typeof m.firstTwentyHitRateBaseline === 'number') {
    segments.push(`前 20 高机会指数占比 ${(m.firstTwentyHitRate * 100).toFixed(0)}%（${m.strategyMode || '均衡型'} 基线 ${(m.firstTwentyHitRateBaseline * 100).toFixed(0)}%），可适度增厚稳/保档`)
  }
  return segments.join('；')
})
const metricsBaselineWarn = computed(() => Boolean(metricsBaselineNote.value))
const planProvinceCode = computed(() => normalizeProvinceCode(volunteerStore.formData.provinceCode || volunteerStore.planItems[0]?.provinceCode || 'GZ'))
const planProvinceConfig = computed(() => getProvinceConfig(planProvinceCode.value))
const isProfessionalGroupPlan = computed(() => planProvinceConfig.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45')
const planProvinceName = computed(() => planProvinceConfig.value.shortName)
const planBatchLabel = computed(() => volunteerStore.policy?.batchName || volunteerStore.formData.batchCode || planProvinceConfig.value.targetBatch)
const planUnitLabel = computed(() => volunteerStore.policy?.volunteerMode || (isProfessionalGroupPlan.value ? '院校专业组' : '志愿'))
const isQueryOnlyPlan = computed(() => (
  volunteerStore.policy?.recommendMode === 'QUERY_ONLY' ||
  volunteerStore.policy?.supportLevel === 'QUERY_ONLY' ||
  volunteerStore.modelInfo?.visibleMetric === 'query_only'
))
const isPreOfficialDataPlan = computed(() => (
  volunteerStore.estimateMode ||
  volunteerStore.recommendationPhase === 'PRE_OFFICIAL_DATA' ||
  volunteerStore.modelInfo?.recommendationPhase === 'PRE_OFFICIAL_DATA'
))
const isOfficialDataPartialPlan = computed(() => (
  volunteerStore.recommendationPhase === 'OFFICIAL_DATA_PARTIAL' ||
  volunteerStore.modelInfo?.recommendationPhase === 'OFFICIAL_DATA_PARTIAL'
))
/**
 * 方案已成功生成/加载（有 planId）但候选为 0 且非“只查策略”。
 * 用于在结果页展示“候选不足/已放宽”说明卡片，避免静默把用户弹回表单页造成“点了没反应”。
 */
const planLoadedButEmpty = computed(() => (
  !isQueryOnlyPlan.value &&
  Number(volunteerStore.planId || 0) > 0 &&
  volunteerStore.planItems.length === 0
))
function recommendModeText(mode?: string) {
  const normalized = String(mode || '').trim()
  return ({
    PARALLEL_MAJOR: '历史估算',
    PARALLEL_MAJOR_60: '历史估算',
    PARALLEL_GROUP: '历史估算',
    SEQUENTIAL_COLLEGE: '只查策略',
    ART_COMPOSITE: '只查策略',
    SPORTS_COMPOSITE: '只查策略',
    ELIGIBILITY_QUERY: '只查策略',
    QUERY_ONLY: '只查策略',
    historical_estimate: '历史估算',
  } as Record<string, string>)[normalized] || normalized || '待核验'
}
const trainingYearText = computed(() => (
  volunteerStore.trainingYears.length ? volunteerStore.trainingYears.join('、') : '2024、2025'
))
/**
 * v7.42：识别批次大类，给艺术 / 体育 / 8 类专项的 QUERY_ONLY 页面单独的说明文案，
 * 避免把"2026 数据未发布"作为唯一原因展示（这些批次本来就不依赖普通位次推荐）。
 */
const planBatchCode = computed(() => (
  String(volunteerStore.policy?.batchCode || volunteerStore.formData.batchCode || '')
))
const ART_BATCHES = [
  // 贵州
  'ART_UNDERGRADUATE_A', 'ART_UNDERGRADUATE_B', 'ART_SPECIALTY',
  // 四川
  'SC_ART_TIQIAN', 'SC_ART_BENKE', 'SC_ART_ZHUANKE',
  // 安徽
  'AH_ART_XIAOKAO_BENKE', 'AH_ART_TONGKAO_BENKE', 'AH_ART_TONGKAO_ZHUANKE',
]
const SPORTS_BATCHES = [
  // 贵州
  'SPORTS_UNDERGRADUATE', 'SPORTS_SPECIALTY',
  // 四川
  'SC_SPORTS_BENKE', 'SC_SPORTS_ZHUANKE',
  // 安徽
  'AH_SPORTS_BENKE', 'AH_SPORTS_ZHUANKE',
]
const SPECIAL_PROGRAM_BATCHES = [
  // 贵州
  'NATIONAL_SPECIAL', 'LOCAL_SPECIAL', 'UNIVERSITY_SPECIAL',
  'ETHNIC_CLASS', 'PREPARATORY', 'ORIENTED',
  'FREE_MEDICAL', 'TEACHER_EXCELLENCE',
  // 四川
  'SC_TIQIAN_BEFORE_A_NATIONAL', 'SC_GAOXIAO_SPECIAL_PRE_B', 'SC_BENKE_A_NATIONAL',
  'SC_BENKE_A_LOCAL', 'SC_BENKE_GAOXIAO_SPECIAL', 'SC_BENKE_REGION_BALANCE',
  'SC_BENKE_MINORITY_PRE',
  // 安徽
  'AH_NATIONAL_SPECIAL', 'AH_LOCAL_SPECIAL', 'AH_UNIVERSITY_SPECIAL',
]
const SEQUENTIAL_BATCHES = [
  // 贵州
  'EARLY_A_B', 'SPECIALTY_EARLY',
  // 四川
  'SC_TIQIAN_A', 'SC_ZHUANKE_EARLY', 'SC_BENKE_SPORTS_TEAM',
  // 安徽（含本科提前批顺序、高职提前批顺序、高校专项顺序）
  'AH_TIQIAN_BENKE_SEQUENTIAL', 'AH_TIQIAN_ZHUANKE_SEQUENTIAL', 'AH_UNIVERSITY_SPECIAL',
]
const isArtBatch = computed(() => ART_BATCHES.includes(planBatchCode.value))
const isSportsBatch = computed(() => SPORTS_BATCHES.includes(planBatchCode.value))
const isSpecialProgramBatch = computed(() => SPECIAL_PROGRAM_BATCHES.includes(planBatchCode.value))
const isSequentialBatch = computed(() => SEQUENTIAL_BATCHES.includes(planBatchCode.value))
const planOfficialSourceName = computed(() => planProvinceConfig.value.officialSource || '省级招生考试院')
const yearPhaseNotice = computed(() => {
  if (isArtBatch.value) {
    return `艺术类批次以"高考文化分 + 校考/统考专业成绩"按${planProvinceName.value}综合分公式独立投档，本系统不替代专业课校考成绩计算；当前页面仅做政策说明与历史候选展示，正式志愿需以${planOfficialSourceName.value}文件和高校招生章程为准。`
  }
  if (isSportsBatch.value) {
    return `体育类批次以"高考文化分 + 体育统考成绩"按${planProvinceName.value}综合分公式独立投档，本系统不替代体育统考成绩计算；当前页面仅做政策说明与历史候选展示，正式志愿需以${planOfficialSourceName.value}文件和高校招生章程为准。`
  }
  if (isSpecialProgramBatch.value) {
    return `国家专项 / 地方专项 / 高校专项 / 区域均衡 / 民族 / 预科 / 定向 / 免费医学定向 / 优师 等专项类批次需先按${planOfficialSourceName.value}公布的户籍 / 学籍 / 综合素质 / 履约协议等条件做资格审核；本系统不替代资格审核与单独投档程序，请到对应高校招生章程和${planOfficialSourceName.value}专项公告核验。`
  }
  if (isSequentialBatch.value) {
    return `当前批次走"院校顺序志愿"，按"根据志愿、从高分到低分、按比例投档"规则录取，与平行志愿口径完全不同；本页仅展示批次规则、资格条件与历史候选，最终以${planOfficialSourceName.value}发布的志愿表为准。`
  }
  if (isPreOfficialDataPlan.value) {
    return `${volunteerStore.activeAdmissionYear || 2026} 年官方招生计划和一分一段表尚未发布；本页仅展示基于 ${trainingYearText.value} 年历史数据的预估/缺口说明，不是正式推荐方案。`
  }
  if (isOfficialDataPartialPlan.value) {
    return `${volunteerStore.activeAdmissionYear || 2026} 年官方数据正在分批导入和质检；本页仅展示数据准备进度或缺口说明，不开放完整推荐。`
  }
  return ''
})
const hasPersistedPlan = computed(() => Number(volunteerStore.planId || 0) > 0)
const canUsePlanActions = computed(() => !isQueryOnlyPlan.value && hasPersistedPlan.value)
const planTitle = computed(() => (
  isQueryOnlyPlan.value
    ? `${planProvinceName.value}${planBatchLabel.value}支持说明`
    : `${planProvinceName.value}${planUnitLabel.value}方案`
))
const targetCountText = computed(() => `${volunteerStore.planMetrics?.targetCount || planProvinceConfig.value.targetCount} 个`)
const rankHeroText = computed(() => {
  const rank = volunteerStore.formData.provinceRank
  if (!rank) return '位次待核验'
  return rankEstimateSummary.value?.rankEstimated
    ? `估算第${rank.toLocaleString()}位`
    : `第${rank.toLocaleString()}位`
})

const tabs = computed(() => {
  const items = modeItems.value
  return [
    { key: 'all', label: '全部', count: items.length },
    { key: '冲', label: '冲', count: items.filter((i: VolunteerItem) => i.gradient === '冲').length },
    { key: '稳', label: '稳', count: items.filter((i: VolunteerItem) => i.gradient === '稳').length },
    { key: '保', label: '保', count: items.filter((i: VolunteerItem) => i.gradient === '保').length },
    { key: '垫', label: '垫', count: items.filter((i: VolunteerItem) => i.gradient === '垫').length },
  ] as const
})

const filteredItems = computed<VolunteerItem[]>(() => {
  if (activeTab.value === 'all') return modeItems.value
  return modeItems.value.filter((item: VolunteerItem) => item.gradient === activeTab.value)
})

const visibleVolunteerItems = computed<VolunteerItem[]>(() => filteredItems.value.slice(0, visibleCount.value))
const hiddenVolunteerCount = computed(() => Math.max(0, filteredItems.value.length - visibleVolunteerItems.value.length))
const listSummaryText = computed(() => (
  `共 ${modeItems.value.length} 条${planUnitLabel.value}，当前展示 ${visibleVolunteerItems.value.length} 条`
))
const activeTabLabel = computed(() => tabs.value.find(tab => tab.key === activeTab.value)?.label || '全部')
const canLoadMore = computed(() => visibleCount.value < filteredItems.value.length)

watch([activeTab, activeMode], () => {
  expandedRows.value = new Set()
  visibleCount.value = 12
})

const topKeeps = computed<VolunteerItem[]>(() =>
  [...modeItems.value]
    .sort((a, b) => ((b.recommendationScore || 0) * 1.5 + (fitRank(b) * 1.2) + (b.dataConfidenceScore || 0) + (b.matchScore || 0)) - ((a.recommendationScore || 0) * 1.5 + (fitRank(a) * 1.2) + (a.dataConfidenceScore || 0) + (a.matchScore || 0)))
    .slice(0, 5),
)

const topRisks = computed<VolunteerItem[]>(() =>
  [...modeItems.value]
    .sort((a, b) => {
      const aRisk = (a.riskColor === 'red' ? 100 : a.riskColor === 'yellow' ? 60 : 20) + (40 - (a.chanceScore || 0))
      const bRisk = (b.riskColor === 'red' ? 100 : b.riskColor === 'yellow' ? 60 : 20) + (40 - (b.chanceScore || 0))
      const aPlanRisk = a.planTrend === '缩招' ? 35 : a.planTrend === '计划数暂缺' ? 22 : (a.latestPlanCount && a.latestPlanCount <= 3) ? 18 : 0
      const bPlanRisk = b.planTrend === '缩招' ? 35 : b.planTrend === '计划数暂缺' ? 22 : (b.latestPlanCount && b.latestPlanCount <= 3) ? 18 : 0
      return (bRisk + bPlanRisk + (b.dataConfidenceScore && b.dataConfidenceScore < 60 ? 18 : 0)) - (aRisk + aPlanRisk + (a.dataConfidenceScore && a.dataConfidenceScore < 60 ? 18 : 0))
    })
    .slice(0, 3),
)

const contextChips = computed(() => {
  const form = volunteerStore.formData
  const chips = [
    { label: '省份', value: planProvinceName.value },
    { label: '分数', value: form.totalScore ? `${form.totalScore} 分` : '待核验' },
    { label: '位次', value: form.provinceRank ? `第 ${form.provinceRank.toLocaleString()} 位` : '待核验' },
    { label: '选科', value: [form.firstSubject, ...(form.resubjects || [])].filter(Boolean).join(' + ') || '待核验' },
    { label: '方案类型', value: activeMode.value },
    { label: planUnitLabel.value, value: `${modeItems.value.length} 条` },
    { label: '偏好', value: [form.decisionPriority, form.careerGoal, form.tuitionBudget].filter(Boolean).join(' / ') || '待核验' },
    { label: '分布', value: `冲${gradientCounts.value.冲} / 稳${gradientCounts.value.稳} / 保${gradientCounts.value.保} / 垫${gradientCounts.value.垫}` },
  ]
  return chips
})

const localAiSummaryCards = computed(() => {
  const advice = volunteerStore.advisorAdvice
  return [
    {
      key: 'position',
      title: '定位',
      body: trimForSummary(
        aiAnalysis.value?.diagnosisSections?.find(section => /定位|分数|位次/.test(section.title || ''))?.content ||
          advice?.positioning ||
          `当前以${trainingYearText.value}年历史数据估算，${activeMode.value}方案共 ${modeItems.value.length} 条。`,
        120,
      ),
    },
    {
      key: 'tradeoff',
      title: '取舍',
      body: trimForSummary(
        aiAnalysis.value?.diagnosisSections?.find(section => /取舍|优先|城市|专业/.test(section.title || ''))?.content ||
          advice?.priorityAdvice ||
          `优先按${volunteerStore.formData.decisionPriority}和${volunteerStore.formData.careerGoal}筛看，再复核不符合家庭预算或城市偏好的条目。`,
        120,
      ),
    },
    {
      key: 'risk',
      title: '风险',
      body: trimForSummary(
        (aiAnalysis.value?.topRiskPoints || [])[0] ||
          advice?.riskChecklist?.[0] ||
          yearPhaseNotice.value ||
          '当前为历史估算参考，最终仍需按官方招生计划、章程和专业限制人工复核。',
        120,
      ),
    },
    {
      key: 'next',
      title: '下一步',
      body: trimForSummary(
        aiAnalysis.value?.actionSteps?.[0]?.content ||
          advice?.actionItems?.[0] ||
          '先看冲稳保分布，再导出表格和家长一起筛选；2026 官方数据发布后再做最终复核。',
        150,
      ),
    },
  ]
})

const aiFullSections = computed(() => {
  if (aiAnalysis.value?.diagnosisSections?.length) return aiAnalysis.value.diagnosisSections
  const advice = volunteerStore.advisorAdvice
  return [
    { title: '定位分析', content: advice?.positioning || localAiSummaryCards.value[0].body },
    { title: '冲稳保结构', content: advice?.gradientAdvice || `当前分布为冲${gradientCounts.value.冲}、稳${gradientCounts.value.稳}、保${gradientCounts.value.保}、垫${gradientCounts.value.垫}。` },
    { title: '城市和专业建议', content: [advice?.cityAdvice, advice?.majorAdvice].filter(Boolean).join('；') || localAiSummaryCards.value[1].body },
    { title: '风险核查', content: (advice?.riskChecklist || []).slice(0, 3).join('；') || localAiSummaryCards.value[2].body },
    { title: '最终建议', content: (advice?.actionItems || []).slice(0, 3).join('；') || localAiSummaryCards.value[3].body },
  ]
})

const aiReportText = computed(() => [
  aiAnalysis.value?.conclusion,
  ...aiFullSections.value.map(section => `${section.title}：${section.content}`),
].filter(Boolean).join('\n'))

const quickQuestions = [
  '这份方案稳吗？',
  '帮我删掉民办院校',
  '优先保留省内院校',
  '想去大城市怎么调？',
  '哪些专业就业更好？',
  '适合保守填报吗？',
]

onMounted(async () => {
  if (!volunteerStore.planItems.length) {
    await restorePlan()
  }
  // 方案确实生成/加载成功但候选为 0（非只查策略）：留在结果页展示“候选不足/已放宽”说明，
  // 不再静默弹回表单页（否则用户感知为“点击生成没反应、进不去结果页”）。
  if (!volunteerStore.planItems.length && !isQueryOnlyPlan.value && !planLoadedButEmpty.value) {
    showToast('暂无志愿数据，请重新生成方案')
    router.push('/volunteer')
  }
})

function supportLevelText(level?: string) {
  if (isPreOfficialDataPlan.value && level === 'TRIAL_RECOMMEND') return '历史估算'
  if (isPreOfficialDataPlan.value && level === 'QUERY_ONLY') return '只查策略'
  if (level === 'FULL_RECOMMEND') return '历史估算'
  if (level === 'TRIAL_RECOMMEND') return '历史估算'
  if (level === 'QUERY_ONLY') return '只查策略'
  if (level === 'UNSUPPORTED') return '暂不支持'
  return level || '待核验'
}

function trimForSummary(text?: string, limit = 120) {
  const normalized = String(text || '').replace(/\s+/g, ' ').trim()
  if (!normalized) return '暂无摘要，请结合志愿表和官方资料继续复核。'
  return normalized.length > limit ? `${normalized.slice(0, limit)}...` : normalized
}

function currentSafetyCode() {
  const saved = volunteerStore.getSavedPlanMeta()
  return volunteerStore.planSafetyCode || saved?.safetyCode || saved?.accessKey || ''
}

function loadMoreVolunteers() {
  visibleCount.value = Math.min(filteredItems.value.length, visibleCount.value + 12)
}

function showAllVolunteers() {
  visibleCount.value = filteredItems.value.length
}

function collapseVolunteers() {
  visibleCount.value = 12
  expandedRows.value = new Set()
}

function displayOrder(index: number | string) {
  return Number(index) + 1
}

async function restorePlan() {
  const saved = volunteerStore.getSavedPlanMeta()
  const planId = Number(route.query.planId || saved?.planId)
  if (!planId) return
  let safetyCode = String(route.query.safetyCode || route.query.accessKey || saved?.safetyCode || saved?.accessKey || '')
  if (!safetyCode && authStore.isAuthenticated) {
    try {
      const detailRes = await fetchMyPlanDetail(planId)
      const detail = detailRes.data.data as VolunteerPlan
      if (detail.items?.length) {
        const formData = formDataFromPlan(detail)
        volunteerStore.setPlanFromResponse(detail)
        volunteerStore.setFormData(formData)
        activeMode.value = formData.strategyMode
        return
      }
      safetyCode = detail.safetyCode || detail.accessKey || ''
    } catch {
      safetyCode = ''
    }
  }
  if (!safetyCode) return
  restoring.value = true
  try {
    const res = await fetchVolunteerPlan(planId, safetyCode)
    const plan = res.data.data
    const formData = formDataFromPlan(plan)
    volunteerStore.setPlanFromResponse(plan, safetyCode)
    volunteerStore.setFormData(formData)
    activeMode.value = formData.strategyMode
    if (route.query.safetyCode || route.query.accessKey) {
      router.replace({ path: route.path, query: { ...route.query, safetyCode: undefined, accessKey: undefined } })
    }
  } catch {
    volunteerStore.clearPlan()
  } finally {
    restoring.value = false
  }
}

function confidenceTone(label?: string) {
  if (label === '高可信') return 'confidence--high'
  if (label === '中可信') return 'confidence--mid'
  return 'confidence--low'
}

function fitRank(item: { referenceFitLevel?: string; chanceScore?: number }) {
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

function referenceFitText(item: { referenceFitLevel?: string; chanceScore?: number }) {
  if (item.referenceFitLevel) return item.referenceFitLevel
  const chance = item.chanceScore || 0
  if (chance >= 75) return '较高'
  if (chance >= 50) return '中等'
  if (chance > 0) return '偏低'
  return '待评估'
}

function displayUnitName(item: { groupCode?: string; groupName?: string; majorName: string }) {
  if (isProfessionalGroupPlan.value) {
    return [item.groupCode, item.groupName || item.majorName].filter(Boolean).join(' · ') || item.majorName
  }
  return item.majorName
}

function confidenceText(item: { dataConfidenceScore?: number; confidenceLabel?: string }) {
  if (typeof item.dataConfidenceScore === 'number') return `${item.dataConfidenceScore}分`
  return item.confidenceLabel || '待评估'
}

function indexText(value?: number, suffix = '') {
  if (typeof value !== 'number' || value <= 0) return '待核验'
  return `${Math.round(value)}${suffix}`
}

function toggleCompare(schoolId?: string) {
  if (!schoolId) return
  const idx = compareIds.value.indexOf(schoolId)
  if (idx > -1) {
    compareIds.value.splice(idx, 1)
    return
  }
  if (compareIds.value.length >= 3) {
    showToast('最多同时对比3所学校')
    return
  }
  compareIds.value.push(schoolId)
}

function openCompare() {
  if (compareIds.value.length < 2) {
    showToast('至少选择2所学校再对比')
    return
  }
  router.push({
    path: '/volunteer/compare',
    query: {
      schools: compareIds.value.join(','),
      mode: activeMode.value,
    },
  })
}

function goAi() {
  if (!canUsePlanActions.value) {
    showToast('当前批次暂不支持 AI 解读')
    return
  }
  aiPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  if (!aiAnalysis.value && !aiLoading.value) {
    void runAiAnalysis()
  }
}

async function runAiAnalysis(forceRefresh = false) {
  if (!volunteerStore.planId) {
    showToast('缺少当前方案，暂时无法解读')
    return
  }
  const safetyCode = currentSafetyCode()
  if (!safetyCode) {
    showToast('缺少方案查看凭证，暂时无法解读')
    return
  }
  aiLoading.value = true
  aiError.value = ''
  try {
    const res = await generateAiAnalysis(volunteerStore.planId, safetyCode, forceRefresh)
    aiAnalysis.value = res.data.data
    aiDeepOpen.value = false
    showSuccessToast('AI 解读已更新')
  } catch (error) {
    aiError.value = formatAiError(error)
  } finally {
    aiLoading.value = false
  }
}

function fillSkillSuggestion(text: string) {
  skillInput.value = text
}

function handleAiDeepToggle(event: Event) {
  aiDeepOpen.value = Boolean((event.target as HTMLDetailsElement).open)
}

function formatAiError(error: unknown) {
  const message = error instanceof Error ? error.message : ''
  if (/timeout|超时|时间较长/i.test(message)) return 'AI 服务响应较慢，请稍后重试。'
  if (/403|unauthorized|forbidden|凭证|无效/i.test(message)) return '方案凭证校验失败，请用正确凭证重新打开结果页。'
  return message || 'AI 解读暂时失败，请稍后再试。'
}

async function sendSkillMessage(preset?: string) {
  const message = (preset || skillInput.value).trim()
  if (!message || skillLoading.value) return
  if (message.length > 800) {
    skillError.value = '单条问题请控制在 800 字以内。'
    return
  }
  if (!volunteerStore.planId) {
    skillError.value = '缺少当前志愿方案，暂时无法咨询。'
    return
  }
  const safetyCode = currentSafetyCode()
  if (!safetyCode) {
    skillError.value = '缺少方案查看凭证，暂时无法咨询。'
    return
  }

  skillError.value = ''
  skillInput.value = ''
  skillMessages.value = [...skillMessages.value, { role: 'user', content: message }]
  skillLoading.value = true

  try {
    const res = await chatZxfSkill({
      planId: volunteerStore.planId,
      safetyCode,
      message,
      aiReport: aiReportText.value,
      messages: skillMessages.value.slice(-8),
    })
    const reply = res.data.data.answer || res.data.data.reply || '暂时没有生成有效回复，请稍后再试。'
    skillMessages.value = [...skillMessages.value, { role: 'assistant', content: reply }]
  } catch (error: unknown) {
    skillError.value = formatAiError(error)
    skillMessages.value = skillMessages.value.filter(item => !(item.role === 'user' && item.content === message))
    skillInput.value = message
  } finally {
    skillLoading.value = false
  }
}

function safeUrl(url?: string | null) {
  return sanitizeHttpUrl(url)
}

function renderedSkillMarkdown(content: string) {
  return renderMarkdown(content || '', { autoSectionHeadings: true })
}

function manualReviewLinks(entry: { evidenceLinks?: string[] }) {
  return (entry.evidenceLinks || []).map(safeUrl).filter(Boolean)
}

function formatOffset(value?: number) {
  if (value === undefined || value === null) return '-'
  return value > 0 ? `+${value.toLocaleString()}` : value.toLocaleString()
}

function formatRank(value?: number) {
  return value ? value.toLocaleString() : '-'
}

function rankSourceLabel(type?: string) {
  if (type === 'original') return '原始录取位次'
  if (type === 'score_rank_converted') return '一分一段换算'
  return '缺位次需复核'
}

function rankSourceClass(type?: string) {
  if (type === 'original') return 'is-original'
  if (type === 'score_rank_converted') return 'is-converted'
  return 'is-missing'
}

function historyFallbackText(item: { dataSourceType?: string; confidenceLabel?: string }) {
  if (item.dataSourceType === '院校级') return '暂无同专业近三年记录，当前展示院校级回退数据，需人工复核。'
  return item.confidenceLabel === '需复核' ? '近三年专业记录不完整，请以官方专业目录和学校章程复核。' : '暂无更多历史记录。'
}

function hasEvidence(item: {
  needsManualReview?: boolean
  admissionBrochureUrl?: string | null
  majorCatalogUrl?: string | null
  tuitionInfoUrl?: string | null
  requirementSourceUrl?: string | null
}) {
  return Boolean(
    item.needsManualReview ||
      safeUrl(item.admissionBrochureUrl) ||
      safeUrl(item.majorCatalogUrl) ||
      safeUrl(item.tuitionInfoUrl) ||
      safeUrl(item.requirementSourceUrl),
  )
}

function openUniversity(item: { schoolId?: string | null }) {
  if (!item.schoolId) return
  router.push({
    path: `/university/${item.schoolId}`,
    query: { schoolId: item.schoolId },
  })
}

async function loadXlsx() {
  return await import('xlsx')
}

function csvCell(value: unknown) {
  const text = value === undefined || value === null || value === '' ? '-' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

function exportCsv() {
  if (!canUsePlanActions.value) {
    showToast('当前批次暂无可导出的完整推荐')
    return
  }
  const items = modeItems.value
  if (!items.length) {
    showToast('暂无志愿数据可导出')
    return
  }
  const headers = [
    '序号', '梯度', '院校', isProfessionalGroupPlan.value ? '院校专业组/组内专业' : '专业',
    '地区', '参考年份', '参考分数', '参考位次', '位次差', '计划数', '计划趋势',
    '参考匹配', '数据参考度', '机会指数', '风险等级', '数据层级', '算法解释', '风险提醒',
  ]
  const rows = items.map((item: VolunteerItem) => [
    item.index,
    item.gradient,
    item.universityName,
    isProfessionalGroupPlan.value ? `${displayUnitName(item)} ${(item.groupMajors || []).join('、')}` : item.majorName,
    [item.province, item.city].filter(Boolean).join(' / ') || '-',
    item.referenceYear,
    item.historyMinScore,
    item.historyMinRank,
    typeof item.rankGap === 'number' ? item.rankGap : '-',
    item.latestPlanCount || '-',
    item.planTrend || '-',
    referenceFitText(item),
    confidenceText(item),
    item.chanceScore || '-',
    item.riskLevel || '-',
    item.dataSourceType || '-',
    item.algorithmExplanation || item.recommendReason || '-',
    item.riskReason || '-',
  ])
  const csv = [headers, ...rows].map(row => row.map(csvCell).join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const file = new File([blob], `${planProvinceName.value}高考${planUnitLabel.value}方案_${activeMode.value}.csv`, {
    type: 'text/csv;charset=utf-8',
  })
  fallbackDownload(file, file.name)
}

async function copySummary() {
  const form = volunteerStore.formData
  const text = [
    `${planProvinceName.value}${planUnitLabel.value}方案摘要`,
    `分数：${form.totalScore || '-'} 分`,
    `位次：${form.provinceRank ? form.provinceRank.toLocaleString() : '-'}`,
    `批次：${planBatchLabel.value}`,
    `生成类型：${isQueryOnlyPlan.value ? '策略建议' : '历史估算'}`,
    `数据来源年份：${trainingYearText.value}`,
    `志愿数量：${modeItems.value.length}`,
    `冲稳保分布：冲${gradientCounts.value.冲} / 稳${gradientCounts.value.稳} / 保${gradientCounts.value.保} / 垫${gradientCounts.value.垫}`,
    yearPhaseNotice.value || '当前结果仅供志愿填报参考，不构成录取承诺。',
  ].filter(Boolean).join('\n')
  try {
    await navigator.clipboard.writeText(text)
    showSuccessToast('方案摘要已复制')
  } catch {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', 'true')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    document.execCommand('copy')
    area.remove()
    showSuccessToast('方案摘要已复制')
  }
}

async function exportExcel() {
  if (!canUsePlanActions.value) {
    showToast('当前批次暂无可导出的完整推荐')
    return
  }
  try {
    const XLSX = await loadXlsx()
    const items = modeItems.value
    if (!items.length) {
      showToast('暂无志愿数据可导出')
      return
    }
    const form = volunteerStore.formData
    const profileLine = [
      `${form.strategyMode}`,
      `${form.decisionPriority}`,
      `${form.careerGoal}`,
      `${form.tuitionBudget}`,
      form.acceptPrivate ? '接受民办' : '只看公办',
      form.acceptSinoForeign ? '接受合作办学' : '排除合作办学',
    ].join(' | ')

    const rows = [
      [`${planProvinceName.value}高考${planUnitLabel.value}方案（决策版）`],
      [`考生信息：总分 ${form.totalScore} 分 | 全省位次 ${form.provinceRank} | 首选 ${form.firstSubject} | 再选 ${form.resubjects.join('、')}`],
      [`决策偏好：${profileLine}`],
      [],
      ['序号', '梯度', '院校', isProfessionalGroupPlan.value ? '院校专业组/组内专业' : '专业', '地区', '参考位次', '位次差', '计划数', '计划趋势', '扩招指数', '招生指数', '精度分', '推荐分', '参考匹配', '数据参考度', '机会指数', '风险等级', '数据层级', '算法解释', '风险提醒'],
      ...items.map((item: VolunteerItem) => [
        item.index,
        item.gradient,
        item.universityName,
        isProfessionalGroupPlan.value ? `${displayUnitName(item)}\n${(item.groupMajors || []).join('、') || '-'}` : item.majorName,
        [item.province, item.city].filter(Boolean).join(' / ') || '-',
        item.historyMinRank || '-',
        typeof item.rankGap === 'number' ? item.rankGap : '-',
        item.latestPlanCount || '-',
        item.planTrend || '-',
        item.planExpansionIndex || '-',
        item.schoolEnrollmentIndex || '-',
        item.precisionScore || '-',
        item.recommendationScore || '-',
        referenceFitText(item),
        confidenceText(item),
        item.chanceScore || '-',
        item.riskLevel || '-',
        item.dataSourceType || '-',
        item.algorithmExplanation || item.recommendReason || '-',
        item.riskReason || '-',
      ]),
    ]

    const ws = XLSX.utils.aoa_to_sheet(rows)
    ws['!cols'] = [
      { wch: 8 }, { wch: 6 }, { wch: 22 }, { wch: 24 }, { wch: 16 },
      { wch: 12 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 },
      { wch: 48 }, { wch: 36 },
    ]
    ws['!merges'] = [
      { s: { r: 0, c: 0 }, e: { r: 0, c: 12 } },
      { s: { r: 1, c: 0 }, e: { r: 1, c: 12 } },
      { s: { r: 2, c: 0 }, e: { r: 2, c: 12 } },
    ]

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, activeMode.value)
    const fileName = `${planProvinceName.value}高考${planUnitLabel.value}方案_${activeMode.value}_${form.totalScore}分_${form.provinceRank}位.xlsx`
    const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
    const file = new File([wbout], fileName, {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    const nav = navigator as Navigator & {
      canShare?: (data: { files?: File[] }) => boolean
      share?: (data: { files?: File[]; title?: string; text?: string }) => Promise<void>
      msSaveOrOpenBlob?: (blob: Blob, defaultName?: string) => boolean
    }

    if (typeof nav.canShare === 'function' && typeof nav.share === 'function' && nav.canShare({ files: [file] })) {
      nav.share({
        files: [file],
        title: fileName,
        text: `${planProvinceName.value}高考${planUnitLabel.value}方案导出文件`,
      }).then(() => {
        showSuccessToast('导出成功')
      }).catch((error) => {
        if (error?.name !== 'AbortError') {
          fallbackDownload(file, fileName, nav)
        }
      })
      return
    }

    fallbackDownload(file, fileName, nav)
  } catch (error) {
    console.error(error)
    showToast('导出失败，请稍后重试')
  }
}

async function exportDecisionDraft() {
  if (!canUsePlanActions.value) {
    showToast('当前批次暂无可导出的人工核验草稿')
    return
  }
  try {
    const XLSX = await loadXlsx()
    const items = decisionDraftItems.value
    if (!items.length) {
      showToast('请先标记“保留”或“待查”的志愿')
      return
    }
    const form = volunteerStore.formData
    const rows = [
      [`${planProvinceName.value}高考${planUnitLabel.value}草稿（人工核验版）`],
      [`考生信息：总分 ${form.totalScore} 分 | 全省位次 ${form.provinceRank} | 首选 ${form.firstSubject} | 再选 ${form.resubjects.join('、')}`],
      ['说明：本表只导出已标记“保留 / 待查”的志愿，淘汰项不会进入草稿。请逐条按官方章程、专业目录和一分一段表复核。'],
      [],
      ['草稿序号', '原序号', '草稿状态', '梯度', '院校', isProfessionalGroupPlan.value ? '院校专业组/组内专业' : '专业', '地区', '参考位次', '位次差', '计划数', '计划趋势', '扩招指数', '招生指数', '精度分', '参考匹配', '数据参考度', '机会指数', '风险等级', '数据层级', '选科来源', '复核原因', '官方证据链接'],
      ...items.map((item: VolunteerItem, idx: number) => [
        idx + 1,
        item.index,
        STATUS_LABEL[statusOf(item)].replace(/[✓⚠×]/g, '').trim(),
        item.gradient,
        item.universityName,
        isProfessionalGroupPlan.value ? `${displayUnitName(item)}\n${(item.groupMajors || []).join('、') || '-'}` : item.majorName,
        [item.province, item.city].filter(Boolean).join(' / ') || '-',
        item.historyMinRank || '-',
        typeof item.rankGap === 'number' ? item.rankGap : '-',
        item.latestPlanCount || '-',
        item.planTrend || '-',
        item.planExpansionIndex || '-',
        item.schoolEnrollmentIndex || '-',
        item.precisionScore || '-',
        referenceFitText(item),
        confidenceText(item),
        item.chanceScore || '-',
        item.riskLevel || '-',
        item.dataSourceType || '-',
        item.subjectRequirementSource || '-',
        (item.reviewFlags || []).join('、') || (item.needsManualReview ? '需人工复核' : '-'),
        [
          safeUrl(item.admissionBrochureUrl),
          safeUrl(item.majorCatalogUrl),
          safeUrl(item.tuitionInfoUrl),
          safeUrl(item.requirementSourceUrl),
        ].filter(Boolean).join('\n') || '-',
      ]),
    ]

    const ws = XLSX.utils.aoa_to_sheet(rows)
    ws['!cols'] = [
      { wch: 8 }, { wch: 8 }, { wch: 10 }, { wch: 6 }, { wch: 22 }, { wch: 24 },
      { wch: 16 }, { wch: 12 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 14 },
      { wch: 24 }, { wch: 48 },
    ]
    ws['!merges'] = [
      { s: { r: 0, c: 0 }, e: { r: 0, c: 15 } },
      { s: { r: 1, c: 0 }, e: { r: 1, c: 15 } },
      { s: { r: 2, c: 0 }, e: { r: 2, c: 15 } },
    ]

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '人工核验草稿')
    const fileName = `${planProvinceName.value}高考${planUnitLabel.value}草稿_${activeMode.value}_${form.totalScore}分_${form.provinceRank}位.xlsx`
    const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
    const file = new File([wbout], fileName, {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    fallbackDownload(file, fileName)
  } catch (error) {
    console.error(error)
    showToast('草稿导出失败，请稍后重试')
  }
}

function fallbackDownload(file: File, fileName: string, nav?: Navigator & {
  msSaveOrOpenBlob?: (blob: Blob, defaultName?: string) => boolean
}) {
  if (nav?.msSaveOrOpenBlob) {
    nav.msSaveOrOpenBlob(file, fileName)
    showSuccessToast('导出成功')
    return
  }
  const blobUrl = URL.createObjectURL(file)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  showSuccessToast('导出成功')
}
</script>

<template>
  <div class="result-page">
    <header class="result-header">
      <div class="header-inner">
        <button class="header-back" @click="router.back()"><ArrowLeft :size="20" /></button>
        <h1 class="header-title">{{ planTitle }}</h1>
        <button v-if="canUsePlanActions" class="header-btn" @click="exportExcel" title="导出 Excel">
          <FileSpreadsheet :size="18" />
        </button>
      </div>
    </header>

    <section class="hero-panel">
      <div class="hero-main">
        <div v-if="topNoticeItems.length" class="top-notice-panel">
          <div class="top-notice-panel__title">
            <ShieldAlert :size="15" />
            <span>数据与凭证提醒</span>
          </div>
          <ul class="top-notice-list">
            <li
              v-for="notice in topNoticeItems"
              :key="notice.key"
              :class="`top-notice-list__item top-notice-list__item--${notice.tone}`"
            >
              {{ notice.text }}
            </li>
          </ul>
        </div>
        <div class="hero-metrics">
          <span class="hero-tag">{{ volunteerStore.formData.totalScore }}分</span>
          <span class="hero-tag">{{ planProvinceName }} · {{ targetCountText }}</span>
          <span class="hero-tag">{{ rankHeroText }}</span>
          <span class="hero-tag">{{ volunteerStore.formData.firstSubject }} + {{ volunteerStore.formData.resubjects.join('、') }}</span>
        </div>
        <h2 class="hero-title">{{ activeMode }}推荐视图</h2>
        <p class="hero-desc">
          系统已根据你的{{ volunteerStore.formData.decisionPriority }}、{{ volunteerStore.formData.careerGoal }}和{{ volunteerStore.formData.tuitionBudget }}
          重新整理当前{{ planUnitLabel }}草稿顺序，帮助你更快做最终决策。
        </p>
        <div class="hero-profile">
          <span class="profile-chip">{{ volunteerStore.formData.strategyMode }}</span>
          <span class="profile-chip">{{ volunteerStore.formData.decisionPriority }}</span>
          <span class="profile-chip">{{ volunteerStore.formData.careerGoal }}</span>
          <span class="profile-chip">{{ volunteerStore.formData.acceptPrivate ? '接受民办' : '只看公办' }}</span>
          <span class="profile-chip">{{ volunteerStore.formData.acceptSinoForeign ? '接受合作办学' : '排除合作办学' }}</span>
        </div>
      </div>
      <div class="hero-side">
        <div
          v-if="typeof volunteerStore.planMetrics?.portfolioSafetyProbability === 'number'"
          class="metric-card metric-card--accent"
          :title="volunteerStore.planMetrics?.portfolioSafetyNote || ''"
        >
          <div class="metric-label">
            整表安全
            <span v-if="autoRebalanceSummary" class="metric-flag">已调平</span>
          </div>
          <div class="metric-value">{{ volunteerStore.planMetrics.portfolioSafetyProbability.toFixed(1) }}%</div>
          <div class="metric-sub">{{ volunteerStore.planMetrics?.portfolioSafetyLevel || '—' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">较高匹配</div>
          <div class="metric-value">{{ summary.highFit }} 个</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">平均置信度</div>
          <div class="metric-value">{{ summary.avgConfidence }}分</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">低置信提醒</div>
          <div class="metric-value">{{ volunteerStore.planMetrics?.lowConfidenceCount ?? summary.lowConfidence }} 个</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">专业级数据</div>
          <div class="metric-value">{{ isProfessionalGroupPlan ? summary.total : summary.majorLevel }} 个</div>
        </div>
      </div>
    </section>

    <section v-if="isQueryOnlyPlan" class="query-only-card">
      <div class="query-only-card__badge">{{ supportLevelText(volunteerStore.policy?.supportLevel) }}</div>
      <h2>{{ isPreOfficialDataPlan ? `${planBatchLabel}仅输出预估/缺口说明` : `${planBatchLabel}暂不输出完整志愿表` }}</h2>
      <p>{{ yearPhaseNotice || volunteerStore.dataQualityWarning || volunteerStore.policy?.supportNote || '当前批次需要按专项政策、专业成绩或单独投档规则人工复核。' }}</p>
      <div class="query-only-card__grid">
        <span><em>考生类别</em><strong>{{ volunteerStore.formData.candidateType || volunteerStore.policy?.candidateType || '待核验' }}</strong></span>
        <span><em>推荐模式</em><strong>{{ recommendModeText(volunteerStore.policy?.recommendMode || volunteerStore.modelInfo?.visibleMetric) }}</strong></span>
        <span><em>目标年份</em><strong>{{ volunteerStore.activeAdmissionYear || volunteerStore.policy?.year || '待核验' }}</strong></span>
        <span><em>数据年份</em><strong>{{ trainingYearText }}</strong></span>
      </div>
      <ul v-if="volunteerStore.warnings.length" class="query-only-card__warnings">
        <li v-for="warning in volunteerStore.warnings" :key="warning">{{ warning }}</li>
      </ul>
      <button class="query-only-card__action" type="button" @click="router.push('/volunteer')">
        返回修改批次或类别
      </button>
    </section>

    <section v-if="planLoadedButEmpty" class="query-only-card empty-plan-card">
      <div class="query-only-card__badge">候选不足</div>
      <h2>{{ planBatchLabel }}当前条件下未匹配到可用志愿</h2>
      <p>{{ volunteerStore.dataQualityWarning || '当前分数、位次或专业/地区偏好较窄，未匹配到候选。常见原因是分数与位次填写不一致，或偏好条件过窄。可调整后重试。' }}</p>
      <ul v-if="volunteerStore.warnings.length" class="query-only-card__warnings">
        <li v-for="warning in volunteerStore.warnings" :key="warning">{{ warning }}</li>
      </ul>
      <div class="empty-plan-card__actions">
        <button class="query-only-card__action" type="button" @click="router.push('/volunteer')">
          返回修改分数 / 位次或偏好
        </button>
        <button class="query-only-card__action query-only-card__action--ghost" type="button" @click="showPlanRestore = true">
          找回历史方案
        </button>
      </div>
    </section>

    <div v-if="!isQueryOnlyPlan && !planLoadedButEmpty" class="result-workbench">
      <main class="result-main">
    <DisclaimerNotice :text="RESULT_NOTICE" tone="warn" class="result-disclaimer" />

    <section class="mode-switch">
      <button
        v-for="mode in ['保守型', '均衡型', '冲刺型']"
        :key="mode"
        class="mode-btn"
        :class="{ active: activeMode === mode }"
        @click="activeMode = mode as PlanMode"
      >
        {{ mode }}
      </button>
    </section>

    <section v-if="!isQueryOnlyPlan && rangeSummary" class="algorithm-card">
      <details class="algorithm-card__details" open>
        <summary>
          <span>本次生成逻辑</span>
          <strong>{{ rangeSummary.source === 'custom' ? '自定义区间' : `${rangeSummary.strategyMode}预设` }}</strong>
        </summary>
        <p>{{ rangeSummary.explanation }}</p>
        <div v-if="volunteerStore.planMetrics" class="algorithm-metrics">
          <span>排除特殊类型 {{ volunteerStore.planMetrics.specialExcludedCount || 0 }} 条</span>
          <span>官方选科 {{ volunteerStore.planMetrics.officialRequirementCount || 0 }} 条</span>
          <span>低置信 {{ volunteerStore.planMetrics.lowConfidenceCount || 0 }} 条</span>
          <span>精度均值 {{ volunteerStore.planMetrics.avgPrecisionScore || summary.avgPrecision }} 分</span>
          <span>扩招 {{ volunteerStore.planMetrics.expandedPlanCount || 0 }} 条</span>
          <span>缩招 {{ volunteerStore.planMetrics.shrunkPlanCount || 0 }} 条</span>
          <span>供给强 {{ volunteerStore.planMetrics.highSupplyCount || 0 }} 条</span>
          <span>旧文理参考 {{ volunteerStore.planMetrics.legacyFallbackCount || 0 }} 条</span>
          <span v-if="volunteerStore.planMetrics.portfolioSafetyProbability">
            整表安全 {{ volunteerStore.planMetrics.portfolioSafetyProbability.toFixed(1) }}%
          </span>
        </div>
        <p v-if="volunteerStore.planMetrics?.portfolioSafetyNote" class="algorithm-card__note">
          {{ volunteerStore.planMetrics.portfolioSafetyLevel }}：{{ volunteerStore.planMetrics.portfolioSafetyNote }}
        </p>
        <div v-if="autoRebalanceSummary" class="auto-rebalance-card">
          <div class="auto-rebalance-card__head">
            <span class="auto-rebalance-card__badge">
              <Sparkles :size="13" /> 列表仿真自动调平
            </span>
            <span class="auto-rebalance-card__count">
              升档 <strong>{{ autoRebalanceSummary.count }}</strong> 条
            </span>
          </div>
          <div v-if="autoRebalanceSummary.before !== null && autoRebalanceSummary.after !== null" class="auto-rebalance-card__metrics">
            <div class="auto-rebalance-card__metric">
              <em>调整前</em>
              <strong>{{ autoRebalanceSummary.before.toFixed(1) }}%</strong>
            </div>
            <ArrowRight :size="14" class="auto-rebalance-card__arrow" />
            <div class="auto-rebalance-card__metric auto-rebalance-card__metric--after">
              <em>调整后</em>
              <strong>{{ autoRebalanceSummary.after.toFixed(1) }}%</strong>
            </div>
            <div v-if="autoRebalanceSummary.improvement !== null && autoRebalanceSummary.improvement > 0" class="auto-rebalance-card__delta">
              +{{ autoRebalanceSummary.improvement.toFixed(1) }}%
            </div>
          </div>
          <p v-if="autoRebalanceSummary.note" class="auto-rebalance-card__note">
            {{ autoRebalanceSummary.note }}
          </p>
        </div>
        <div v-if="recruitTypeRows.length" class="algorithm-recruit-breakdown">
          <div class="algorithm-recruit-breakdown__title">
            <strong>主列表招生类型分布</strong>
            <span v-if="volunteerStore.planMetrics?.ruleViolationCount">
              规则前置异常 {{ volunteerStore.planMetrics.ruleViolationCount }} 条
            </span>
          </div>
          <ul class="algorithm-recruit-breakdown__list">
            <li v-for="row in recruitTypeRows" :key="row.key" :class="{ 'is-warn': row.warn }">
              <span>{{ row.label }}</span>
              <strong>{{ row.count }}</strong>
              <small>{{ row.percent }}</small>
            </li>
          </ul>
        </div>
        <p
          v-if="metricsBaselineNote"
          :class="['algorithm-card__note', { 'algorithm-card__note--warn': metricsBaselineWarn }]"
        >
          {{ metricsBaselineNote }}
        </p>
        <div class="algorithm-range-grid">
          <div v-for="range in rangeSummaryRows" :key="range.gradient" class="algorithm-range-item">
            <span>{{ range.gradient }}</span>
            <strong>第{{ formatRank(range.rankLow) }} ~ {{ formatRank(range.rankHigh) }}位</strong>
            <small>
              偏移 {{ formatOffset(range.rankOffsetMin) }} ~ {{ formatOffset(range.rankOffsetMax) }}
              <template v-if="range.rankRatioMin && range.rankRatioMax"> · 比例 {{ range.rankRatioMin }}R~{{ range.rankRatioMax }}R</template>
              · {{ range.actualCount }}/{{ range.targetCount }} 条
            </small>
            <small v-if="range.rangeSourceNote">{{ range.rangeSourceNote }}</small>
          </div>
        </div>
      </details>
    </section>

    <section v-if="!isQueryOnlyPlan && volunteerStore.advisorAdvice" class="advisor-card">
      <div class="advisor-card__head">
        <div>
          <span class="advisor-card__eyebrow">GitHub 张雪峰.skill · 公开策略参考</span>
          <h3>{{ volunteerStore.advisorAdvice.title || '报考顾问建议' }}</h3>
        </div>
        <div class="advisor-card__actions">
          <SafeExternalLink
            v-if="volunteerStore.advisorAdvice.sourceProjectUrl"
            :url="volunteerStore.advisorAdvice.sourceProjectUrl"
            class="advisor-card__source-link"
          >
            <ExternalLink :size="12" /> {{ volunteerStore.advisorAdvice.sourceProjectName || 'GitHub 项目' }}
          </SafeExternalLink>
          <span class="advisor-card__badge">非本人意见</span>
        </div>
      </div>

      <div class="advisor-card__grid">
        <article>
          <strong>定位</strong>
          <p>{{ volunteerStore.advisorAdvice.positioning }}</p>
        </article>
        <article>
          <strong>取舍</strong>
          <p>{{ volunteerStore.advisorAdvice.priorityAdvice }}</p>
        </article>
        <article>
          <strong>梯度</strong>
          <p>{{ volunteerStore.advisorAdvice.gradientAdvice }}</p>
        </article>
        <article>
          <strong>计划变化</strong>
          <p>{{ volunteerStore.advisorAdvice.planChangeAdvice }}</p>
        </article>
        <article>
          <strong>城市</strong>
          <p>{{ volunteerStore.advisorAdvice.cityAdvice }}</p>
        </article>
        <article>
          <strong>专业</strong>
          <p>{{ volunteerStore.advisorAdvice.majorAdvice }}</p>
        </article>
      </div>

      <div class="advisor-card__lists">
        <div>
          <h4>风险检查</h4>
          <ul>
            <li v-for="(risk, index) in volunteerStore.advisorAdvice.riskChecklist || []" :key="`advisor-risk-${index}`">{{ risk }}</li>
          </ul>
        </div>
        <div>
          <h4>下一步</h4>
          <ol>
            <li v-for="(action, index) in volunteerStore.advisorAdvice.actionItems || []" :key="`advisor-action-${index}`">{{ action }}</li>
          </ol>
        </div>
      </div>
      <p class="advisor-card__note">{{ volunteerStore.advisorAdvice.sourceNote }}</p>
    </section>

    <section class="draft-status-bar" v-if="!isQueryOnlyPlan && modeItems.length">
      <div class="draft-status-bar__head">
        <span class="draft-status-bar__title">{{ planUnitLabel }}草稿</span>
        <span class="draft-status-bar__hint">点击每条志愿右下角按钮可在「保留 / 待查 / 淘汰」之间切换，状态保存在本机。</span>
      </div>
      <div class="draft-status-bar__metrics">
        <span class="draft-status-pill draft-status-pill--keep">保留 {{ statusSummary.keep }}</span>
        <span class="draft-status-pill draft-status-pill--review">待查 {{ statusSummary.review }}</span>
        <span class="draft-status-pill draft-status-pill--drop">淘汰 {{ statusSummary.drop }}</span>
        <button class="draft-export-btn" type="button" @click="exportDecisionDraft">
          导出人工核验草稿
        </button>
      </div>
    </section>

    <section
      v-if="!isQueryOnlyPlan && volunteerStore.manualReviewItems && volunteerStore.manualReviewItems.length"
      class="manual-review-card"
    >
      <header class="manual-review-card__head">
        <div>
          <h3 class="manual-review-card__title">
            <ClipboardCheck :size="16" /> 强制人工复核清单
          </h3>
          <p class="manual-review-card__desc">
            默认只显示摘要，完整条目展开后再逐条核对。
          </p>
        </div>
        <div class="manual-review-card__summary">
          <span class="manual-review-card__count">共 {{ volunteerStore.manualReviewItems.length }} 条</span>
          <span class="manual-review-card__count manual-review-card__count--risk">高风险 {{ highRiskReviewCount }} 条</span>
          <button class="manual-review-card__toggle" type="button" @click="toggleManualReview">
            {{ manualReviewExpanded ? '收起' : '展开复核清单' }}
          </button>
        </div>
      </header>
      <ul v-if="!manualReviewExpanded" class="manual-review-preview">
        <li v-for="entry in manualReviewPreview" :key="`mr-preview-${entry.index}`">
          <strong>{{ entry.universityName }} · {{ entry.majorName }}</strong>
          <span>{{ (entry.reasons || []).slice(0, 1).join('') || '建议人工复核招生章程。' }}</span>
        </li>
      </ul>
      <ul v-if="manualReviewExpanded" class="manual-review-list">
        <li v-for="entry in visibleManualReviews" :key="`mr-${entry.index}`" class="manual-review-list__item">
          <div class="manual-review-list__row">
            <span class="manual-review-list__index">{{ entry.index }}</span>
            <span class="manual-review-list__title">{{ entry.universityName }} · {{ entry.majorName }}</span>
            <span class="manual-review-list__gradient">{{ entry.gradient }}</span>
          </div>
          <ul class="manual-review-list__reasons">
            <li v-for="(reason, ri) in entry.reasons" :key="`mr-${entry.index}-r-${ri}`">{{ reason }}</li>
          </ul>
          <div v-if="manualReviewLinks(entry).length" class="manual-review-list__links">
            <SafeExternalLink
              v-for="(link, li) in manualReviewLinks(entry)"
              :key="`mr-${entry.index}-l-${li}`"
              :url="link"
            >
              <ExternalLink :size="12" /> 官方资料 {{ displayOrder(li) }}
            </SafeExternalLink>
          </div>
        </li>
      </ul>
      <div v-if="manualReviewExpanded && volunteerStore.manualReviewItems.length > 5" class="manual-review-card__actions">
        <button
        class="manual-review-card__toggle"
        type="button"
        @click="showAllReview = !showAllReview"
      >
          {{ showAllReview ? '只看前 5 条' : `查看全部 ${volunteerStore.manualReviewItems.length} 条` }}
      </button>
        <button class="manual-review-card__toggle" type="button" @click="toggleManualReview">收起</button>
      </div>
    </section>

    <section v-if="!isQueryOnlyPlan" class="digest-grid">
      <div class="digest-card">
        <div class="digest-head">
          <Sparkles :size="16" />
          <h3>最值得保留</h3>
        </div>
        <div class="digest-list">
          <button
            v-for="item in topKeeps"
            :key="item.index"
            class="digest-item"
            @click="openUniversity(item)"
          >
            <strong>{{ item.universityName }}</strong>
            <span>{{ item.majorName }}</span>
          </button>
        </div>
      </div>

      <div class="digest-card digest-card--warn">
        <div class="digest-head">
          <AlertTriangle :size="16" />
          <h3>最需要警惕</h3>
        </div>
        <div class="digest-list">
          <div v-for="item in topRisks" :key="item.index" class="digest-item digest-item--warn">
            <strong>{{ item.universityName }}</strong>
            <span>{{ item.riskReason || '建议重点复核招生章程与位次波动' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section v-if="!isQueryOnlyPlan" class="tabs-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span class="tab-num">{{ tab.count }}</span>
      </button>
    </section>

    <div v-if="!isQueryOnlyPlan" class="list-status">
      <span>{{ listSummaryText }}</span>
      <strong>{{ activeTabLabel }}筛选</strong>
      <small v-if="hiddenVolunteerCount">还有 {{ hiddenVolunteerCount }} 条未展开</small>
    </div>

    <section v-if="!isQueryOnlyPlan" class="compare-bar" :class="{ 'compare-bar--active': compareIds.length > 0 }">
      <div class="compare-copy">
        <div class="compare-title">院校对比</div>
        <div class="compare-desc">已选 {{ compareIds.length }} 所学校，可快速横向比较平台、风险和适配度</div>
      </div>
      <button class="compare-btn" :disabled="compareIds.length < 2" @click="openCompare">
        <SplitSquareVertical :size="16" />
        去对比
      </button>
    </section>

    <section v-if="!isQueryOnlyPlan" class="plan-list">
      <article
        v-for="item in visibleVolunteerItems"
        :key="`${activeMode}-${item.index}-${item.schoolId}`"
        :class="[
          'plan-row',
          'gz-card',
          `plan-row--status-${statusOf(item)}`,
          { 'plan-row--expanded': isExpanded(item) },
        ]"
      >
        <div class="plan-row__summary" role="button" tabindex="0" @click="toggleDetail(item)" @keydown.enter="toggleDetail(item)" @keydown.space.prevent="toggleDetail(item)">
          <div class="plan-row__header">
            <div class="plan-row__rank" :style="{ color: gradientConfig[item.gradient].color, background: gradientConfig[item.gradient].bg }">
              {{ item.index }}
            </div>

            <div class="plan-row__main">
              <div class="plan-row__badges">
                <span class="grad-badge" :style="{ color: gradientConfig[item.gradient].color, background: gradientConfig[item.gradient].bg }">{{ item.gradient }}</span>
                <span
                  v-if="item.autoRebalanced && item.originalGradient && item.originalGradient !== item.gradient"
                  class="rebalance-badge"
                  :title="item.rebalanceReason || '列表仿真自动调平：参考概率较高的志愿自动升档到 保/垫 档'"
                >
                  <Sparkles :size="11" /> 自动调档 {{ item.originalGradient }}→{{ item.gradient }}
                </span>
                <span v-if="item.matchTag" class="match-badge">{{ item.matchTag }}</span>
                <span v-if="item.dataSourceType" class="source-badge">{{ item.dataSourceType }}</span>
                <span v-if="item.confidenceLabel" class="confidence-badge" :class="confidenceTone(item.confidenceLabel)">{{ item.confidenceLabel }}</span>
              </div>
              <h3 class="plan-row__school">{{ item.universityName }}</h3>
              <p class="plan-row__major">{{ displayUnitName(item) }}</p>
              <div class="plan-row__meta">
                <span><MapPin :size="13" /> {{ [item.province, item.city].filter(Boolean).join(' · ') || '地区待补充' }}</span>
                <span v-if="item.schoolNature">{{ item.schoolNature }}</span>
                <span v-for="tag in item.tags" :key="tag" class="meta-tag">{{ tag }}</span>
              </div>
            </div>

            <div class="plan-row__aside" @click.stop @keydown.enter.stop>
              <div>
                <div class="plan-row__aside-prob plan-row__aside-fit">{{ referenceFitText(item) }}</div>
                <div class="plan-row__aside-label">参考匹配</div>
              </div>
              <button class="compare-toggle" :class="{ active: compareIds.includes(item.schoolId || '') }" @click="toggleCompare(item.schoolId)">
                {{ compareIds.includes(item.schoolId || '') ? '已加入对比' : '加入对比' }}
              </button>
            </div>
          </div>

          <div class="plan-row__quick">
            <span><em>年份</em><strong>{{ item.referenceYear }}</strong></span>
            <span><em>位次</em><strong>{{ item.historyMinRank?.toLocaleString() || '-' }}</strong></span>
            <span><em>位次差</em><strong>{{ typeof item.rankGap === 'number' ? item.rankGap.toLocaleString() : '-' }}</strong></span>
            <span><em>计划</em><strong>{{ item.latestPlanCount ? `${item.latestPlanCount}人` : '待核验' }}</strong></span>
            <span><em>参考匹配</em><strong>{{ referenceFitText(item) }}</strong></span>
            <span><em>置信度</em><strong>{{ confidenceText(item) }}</strong></span>
            <span><em>计划趋势</em><strong>{{ item.planTrend || '待观察' }}</strong></span>
            <span><em>扩招指数</em><strong>{{ indexText(item.planExpansionIndex) }}</strong></span>
            <span><em>招生指数</em><strong>{{ indexText(item.schoolEnrollmentIndex, '分') }}</strong></span>
            <span><em>精度</em><strong>{{ item.precisionScore ? `${item.precisionScore}分` : '待核验' }}</strong></span>
            <span><em>顺位</em><strong>{{ item.index }}</strong></span>
          </div>

          <div class="plan-row__compact-footer">
            <span class="fit-text">{{ item.suitableFor || '适合作为梯度中的功能位志愿，建议横向比较后排序。' }}</span>
            <button class="detail-toggle" type="button" :aria-expanded="isExpanded(item)" @click.stop="toggleDetail(item)">
              {{ isExpanded(item) ? '收起详情' : '查看详情' }}
              <ChevronDown :size="15" :class="{ rotated: isExpanded(item) }" />
            </button>
          </div>
        </div>

        <transition name="detail-fade">
          <div v-if="isExpanded(item)" class="plan-row__detail">
            <div class="plan-row__insights">
              <div class="reason-box reason-box--full">
                <div class="reason-title"><ShieldCheck :size="14" /> 上榜原因（4 段式）</div>
                <ul v-if="parseRecommendReason(item.recommendReason).length" class="reason-segment-list">
                  <li
                    v-for="seg in parseRecommendReason(item.recommendReason)"
                    :key="seg.key"
                    :class="['reason-segment', REASON_SEGMENT_TONE[seg.key]]"
                  >
                    <span class="reason-segment__label">{{ seg.label }}</span>
                    <p class="reason-segment__body">{{ seg.body }}</p>
                  </li>
                </ul>
                <p v-else class="reason-fallback">建议结合学校平台、专业方向和官方章程综合判断。</p>
              </div>
              <div v-if="item.autoRebalanced" class="reason-box reason-box--rebalance">
                <div class="reason-title"><Sparkles :size="14" /> 列表仿真自动调平</div>
                <p>
                  原梯度 <strong>{{ item.originalGradient || '冲' }}</strong> → 当前 <strong>{{ item.gradient }}</strong>。
                  {{ item.rebalanceReason || '整张志愿表自动吸收风险。' }}
                </p>
              </div>
              <div class="reason-box reason-box--warn">
                <div class="reason-title"><AlertTriangle :size="14" /> 风险提醒</div>
                <p>{{ item.riskReason || '当前项暂无明显异常，但仍需核对招生章程、学费和专业限制。' }}</p>
              </div>
              <div class="reason-box">
                <div class="reason-title"><ArrowRight :size="14" /> 排列建议</div>
                <p>{{ item.alternativeOption || '建议与同梯度院校对比后决定具体排序。' }}</p>
              </div>
              <div class="reason-box reason-box--info">
                <div class="reason-title"><Info :size="14" /> 算法解释</div>
                <p>
                  {{ item.algorithmExplanation || `本条按${item.gradient}档区间、历史位次、数据层级、选科要求和偏好匹配排序；参考匹配为${referenceFitText(item)}，数据置信度为${confidenceText(item)}。` }}
                </p>
              </div>
              <div class="reason-box reason-box--precision">
                <div class="reason-title"><ShieldCheck :size="14" /> 精度信号</div>
                <p>{{ item.precisionNote || `${item.precisionLabel || '精度待评估'}；扩招指数${indexText(item.planExpansionIndex)}，招生供给${indexText(item.schoolEnrollmentIndex, '分')}。` }}</p>
              </div>
            </div>

            <div class="history-card">
              <div class="history-card__head">
                <div>
                  <h4>近三年录取记录</h4>
                  <p>{{ item.rangeNote || '按本次梯度区间和历史投档数据筛选，请结合官方信息复核。' }}</p>
                </div>
                <span :class="['history-card__badge', item.dataSourceType === '院校级' ? 'history-card__badge--warn' : '']">
                  {{ item.dataSourceType || '数据待补' }}
                </span>
              </div>
              <div v-if="item.historyRecords && item.historyRecords.length" class="history-record-list">
                <div v-for="record in item.historyRecords" :key="`${item.index}-${record.year}-${record.dataSourceType}`" class="history-record">
                  <span class="history-record__year">{{ record.year || '-' }}</span>
                  <span><em>最低分</em><strong>{{ record.minScore || '-' }}</strong></span>
                  <span><em>最低位次</em><strong>{{ formatRank(record.minRank) }}</strong></span>
                  <span><em>计划</em><strong>{{ record.planCount || '-' }}</strong></span>
                  <span><em>批次</em><strong>{{ record.batch || '-' }}</strong></span>
                  <span class="history-record__source">{{ record.dataSourceType || '需复核' }}</span>
                  <span
                    :class="['history-record__rank-source', rankSourceClass(record.rankSourceType)]"
                    :title="record.rankSourceNote || rankSourceLabel(record.rankSourceType)"
                  >
                    {{ rankSourceLabel(record.rankSourceType) }}
                  </span>
                  <p v-if="record.rankSourceNote && record.rankSourceType !== 'original'" class="history-record__note">
                    {{ record.rankSourceNote }}
                  </p>
                </div>
              </div>
              <div v-else class="history-empty">{{ historyFallbackText(item) }}</div>
            </div>

            <div v-if="isProfessionalGroupPlan" class="history-card">
              <div class="history-card__head">
                <div>
                  <h4>组内专业与调剂提示</h4>
                  <p>{{ planProvinceName }}院校专业组内最多填 6 个专业，并选择是否服从专业调剂；以下专业仅来自已导入公开来源，需按当年专业目录复核。</p>
                </div>
                <span class="history-card__badge">{{ item.obeyAdjustment ? '调剂需复核' : '未设置调剂' }}</span>
              </div>
              <div v-if="item.groupMajors && item.groupMajors.length" class="group-major-tags">
                <span v-for="major in item.groupMajors" :key="`${item.index}-${major}`">{{ major }}</span>
              </div>
              <div v-else class="history-empty">该专业组的组内专业尚未结构化导入，必须查看官方专业目录。</div>
            </div>

            <div v-if="hasEvidence(item)" class="evidence-chain">
              <div class="evidence-chain__head">
                <ShieldAlert :size="13" />
                <span>证据链 / 复核入口</span>
              </div>
              <div class="evidence-chain__links">
                <SafeExternalLink v-if="safeUrl(item.admissionBrochureUrl)" :url="item.admissionBrochureUrl">
                  <ExternalLink :size="12" /> 招生章程
                </SafeExternalLink>
                <SafeExternalLink v-if="safeUrl(item.majorCatalogUrl)" :url="item.majorCatalogUrl">
                  <ExternalLink :size="12" /> 专业目录
                </SafeExternalLink>
                <SafeExternalLink v-if="safeUrl(item.tuitionInfoUrl)" :url="item.tuitionInfoUrl">
                  <ExternalLink :size="12" /> 收费标准
                </SafeExternalLink>
                <SafeExternalLink v-if="safeUrl(item.requirementSourceUrl)" :url="item.requirementSourceUrl">
                  <ExternalLink :size="12" /> 选科要求来源{{ item.requirementSourceYear ? `(${item.requirementSourceYear})` : '' }}
                </SafeExternalLink>
                <span v-if="item.needsManualReview" class="evidence-chain__flag">需复核</span>
              </div>
            </div>

            <div class="plan-row__footer">
              <div class="plan-row__footer-actions">
                <button
                  class="status-toggle"
                  :class="`status-toggle--${statusOf(item)}`"
                  type="button"
                  @click="cycleStatus(item)"
                >
                  {{ STATUS_LABEL[statusOf(item)] }}
                </button>
                <button class="detail-link" @click="openUniversity(item)">查看院校详情</button>
              </div>
            </div>
          </div>
        </transition>
      </article>
      <div v-if="filteredItems.length > 12" class="plan-list-controls">
        <button v-if="canLoadMore" type="button" @click="loadMoreVolunteers">展开更多</button>
        <button v-if="canLoadMore" type="button" @click="showAllVolunteers">查看全部</button>
        <button v-if="visibleCount > 12" type="button" @click="collapseVolunteers">收起</button>
      </div>
    </section>

    <RecommendSection v-if="!isQueryOnlyPlan" />

    <section ref="aiPanelRef" class="ai-workbench-card">
      <div class="ai-workbench-card__head">
        <div>
          <span class="ai-workbench-card__eyebrow">已继承当前方案上下文</span>
          <h3>AI 志愿解读助手</h3>
        </div>
        <button class="ai-workbench-card__refresh" type="button" :disabled="aiLoading" @click="runAiAnalysis(true)">
          {{ aiLoading ? '解读中...' : '刷新解读' }}
        </button>
      </div>

      <div class="ai-context-grid">
        <div v-for="chip in contextChips" :key="chip.label" class="ai-context-chip">
          <span>{{ chip.label }}</span>
          <strong>{{ chip.value }}</strong>
        </div>
      </div>

      <div v-if="aiError" class="ai-error">{{ aiError }}</div>
      <div class="ai-summary-grid">
        <article v-for="card in localAiSummaryCards" :key="card.key" class="ai-summary-card">
          <strong>{{ card.title }}</strong>
          <p>{{ card.body }}</p>
        </article>
      </div>

      <details class="ai-deep-details" :open="aiDeepOpen" @toggle="handleAiDeepToggle">
        <summary>展开完整解读</summary>
        <div class="ai-deep-sections">
          <article v-for="section in aiFullSections" :key="section.title">
            <h4>{{ section.title }}</h4>
            <p>{{ section.content }}</p>
          </article>
        </div>
      </details>

      <div class="skill-chat-box">
        <div class="skill-chat-box__head">
          <h4>继续追问这个方案</h4>
          <p>追问会携带当前 resultId 和方案凭证，由后端读取本次志愿方案上下文。</p>
        </div>
        <div class="quick-question-row">
          <button
            v-for="question in quickQuestions"
            :key="question"
            type="button"
            @click="fillSkillSuggestion(question)"
          >
            {{ question }}
          </button>
        </div>
        <div v-if="skillMessages.length" class="skill-message-list">
          <div
            v-for="(message, index) in skillMessages"
            :key="`skill-${index}`"
            :class="['skill-message', `skill-message--${message.role}`]"
          >
            <div
              v-if="message.role === 'assistant'"
              class="skill-message__markdown"
              v-html="renderedSkillMarkdown(message.content)"
            />
            <p v-if="message.role === 'assistant'" class="ai-reply-notice">{{ AI_ANALYSIS_NOTICE }}</p>
            <template v-else>{{ message.content }}</template>
          </div>
        </div>
        <div v-if="skillError" class="ai-error">{{ skillError }}</div>
        <form class="skill-input-row" @submit.prevent="sendSkillMessage()">
          <input
            v-model="skillInput"
            type="text"
            placeholder="例如：帮我把学校优先的志愿再筛一版"
            autocomplete="off"
            maxlength="800"
          >
          <button type="submit" :disabled="skillLoading || !skillInput.trim()">
            {{ skillLoading ? '生成中...' : '发送' }}
          </button>
        </form>
      </div>
    </section>
      </main>

      <aside v-if="canUsePlanActions" class="result-tools" aria-label="方案操作">
        <button class="result-tool result-tool--primary" type="button" @click="goAi">
          <Sparkles :size="16" />
          AI 深度解读
        </button>
        <button class="result-tool" type="button" @click="exportDecisionDraft">
          <ClipboardCheck :size="16" />
          导出草稿
        </button>
        <button class="result-tool" type="button" @click="exportCsv">
          <Download :size="16" />
          导出 CSV
        </button>
        <button class="result-tool" type="button" @click="exportExcel">
          <FileSpreadsheet :size="16" />
          导出 Excel
        </button>
        <button class="result-tool" type="button" @click="copySummary">
          <ClipboardCheck :size="16" />
          复制摘要
        </button>
        <button class="result-tool" type="button" @click="showPlanRestore = true">
          <ShieldCheck :size="16" />
          找回方案
        </button>
        <button class="result-tool result-tool--muted" type="button" @click="router.push('/volunteer')">
          <ArrowLeft :size="16" />
          返回修改
        </button>
      </aside>
    </div>

    <PlanRestoreDialog v-model="showPlanRestore" :initial-plan-id="volunteerStore.planId" />
  </div>
</template>

<style scoped>
.result-page {
  min-height: 100dvh;
  padding-bottom: 40px;
  background: #f8fafc;
}

.result-header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-back,
.header-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
  color: #334155;
}

.header-title {
  flex: 1;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.hero-panel,
.result-workbench {
  max-width: 1200px;
  margin: 0 auto;
  padding-left: 16px;
  padding-right: 16px;
}

.mode-switch,
.algorithm-card,
.advisor-card,
.digest-grid,
.tabs-bar,
.compare-bar,
.plan-list,
.list-status,
.draft-status-bar,
.manual-review-card,
.ai-workbench-card {
  width: 100%;
}

.result-workbench {
  display: grid;
  gap: 16px;
  align-items: start;
  padding-top: 18px;
}

.result-main {
  min-width: 0;
  display: grid;
  gap: 14px;
}

.result-tools {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
}

.result-tool {
  min-height: 38px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  font-size: 13px;
  font-weight: 800;
}

.result-tool--primary {
  background: #0f172a;
  color: #fff;
  border-color: #0f172a;
}

.result-tool--muted {
  background: #fff;
  color: #475569;
  border-color: #e2e8f0;
}

.hero-panel {
  display: grid;
  gap: 16px;
  padding-top: 20px;
  align-items: stretch;
}

.result-warning {
  padding: 12px 14px;
  border: 1px solid #fde68a;
  border-radius: 16px;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
  line-height: 1.6;
}

.rank-estimate-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.6;
}

.rank-estimate-banner--auto {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.top-notice-panel {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.top-notice-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
}

.top-notice-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 6px;
}

.top-notice-list__item {
  position: relative;
  padding-left: 14px;
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

.top-notice-list__item::before {
  content: '';
  position: absolute;
  top: 0.68em;
  left: 0;
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: #60a5fa;
}

.top-notice-list__item--warning {
  color: #92400e;
}

.top-notice-list__item--warning::before {
  background: #f59e0b;
}

.top-notice-list__item--credential {
  color: #065f46;
  font-weight: 800;
}

.top-notice-list__item--credential::before {
  background: #10b981;
}

.hero-main {
  min-width: 0;
  padding: 22px;
  border-radius: 24px;
  background: linear-gradient(145deg, #ffffff, #eef4ff);
  border: 1px solid rgba(37, 99, 235, 0.08);
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.08);
}

.hero-metrics,
.hero-profile {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-tag,
.profile-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: #fff;
  color: #334155;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.hero-title {
  margin-top: 14px;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 900;
  color: #0f172a;
}

.hero-desc {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
}

.hero-profile {
  margin-top: 14px;
}

.hero-side {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.metric-card {
  padding: 16px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.06);
  position: relative;
  min-width: 0;
}

.metric-card--accent {
  background: linear-gradient(140deg, #ecfdf5 0%, #f0fdfa 100%);
  border-color: #a7f3d0;
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.08);
}

.metric-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.metric-flag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: #fef3c7;
  color: #92400e;
  font-size: 10px;
  font-weight: 800;
  border: 1px solid #fde68a;
}

.metric-sub {
  margin-top: 4px;
  font-size: 11px;
  color: #047857;
  letter-spacing: 0.02em;
}

.metric-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 900;
  color: #0f172a;
}

.mode-switch {
  display: flex;
  gap: 10px;
  padding-top: 18px;
}

.mode-btn {
  flex: 1;
  min-height: 42px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  color: #475569;
  font-size: 14px;
  font-weight: 700;
}

.mode-btn.active {
  background: #0f172a;
  color: #fff;
  border-color: #0f172a;
}

.algorithm-card {
  padding-top: 12px;
}

.algorithm-card__details {
  border: 1px solid #dbeafe;
  border-radius: 18px;
  background: #fff;
  padding: 14px 16px;
}

.algorithm-card__details summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.algorithm-card__details summary::marker {
  color: #2563eb;
}

.algorithm-card__details summary strong {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
}

.algorithm-card__details p {
  margin-top: 10px;
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.algorithm-card__note {
  padding: 8px 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.algorithm-card__note--warn {
  background: #fff7ed;
  border-color: #fed7aa;
  color: #c2410c;
}

.algorithm-recruit-breakdown {
  margin-top: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.algorithm-recruit-breakdown__title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  font-size: 13px;
}

.algorithm-recruit-breakdown__title span {
  color: #c2410c;
  font-size: 12px;
}

.algorithm-recruit-breakdown__list {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.algorithm-recruit-breakdown__list li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  font-size: 12px;
  color: #475569;
}

.algorithm-recruit-breakdown__list li.is-warn {
  background: #fff1f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.algorithm-recruit-breakdown__list li strong {
  color: #0f172a;
  font-size: 13px;
}

.algorithm-recruit-breakdown__list li.is-warn strong {
  color: #b91c1c;
}

.algorithm-recruit-breakdown__list li small {
  color: #94a3b8;
}

.algorithm-metrics {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 6px;
}

.algorithm-metrics span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.algorithm-metrics span:nth-child(odd) {
  background: #f9fafb;
}

.algorithm-range-grid {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.algorithm-range-item {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  display: grid;
  gap: 3px;
}

.algorithm-range-item span {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 900;
}

.algorithm-range-item strong {
  color: #0f172a;
  font-size: 13px;
}

.algorithm-range-item small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.5;
}

.query-only-card {
  margin-top: 18px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid #fed7aa;
  background: linear-gradient(180deg, #fff7ed, #ffffff);
  box-shadow: 0 18px 48px rgba(194, 65, 12, 0.08);
}

.query-only-card__badge {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  padding: 0 10px;
  border-radius: 999px;
  background: #ffedd5;
  color: #c2410c;
  font-size: 12px;
  font-weight: 900;
}

.query-only-card h2 {
  margin: 12px 0 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.35;
}

.query-only-card p {
  margin: 10px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}

.query-only-card__grid {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.query-only-card__grid span {
  padding: 12px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #fed7aa;
  display: grid;
  gap: 4px;
}

.query-only-card__grid em {
  color: #94a3b8;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.query-only-card__grid strong {
  color: #0f172a;
  font-size: 13px;
}

.query-only-card__warnings {
  margin: 14px 0 0;
  padding-left: 18px;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.8;
}

.query-only-card__action {
  margin-top: 16px;
  min-height: 42px;
  padding: 0 16px;
  border: none;
  border-radius: 14px;
  background: #0f172a;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.empty-plan-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.query-only-card__action--ghost {
  background: #ffffff;
  color: #0f172a;
  border: 1px solid #cbd5e1;
}

.advisor-card {
  margin-top: 12px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid #dbeafe;
  background: linear-gradient(145deg, #f8fbff, #ffffff);
  box-shadow: 0 16px 42px rgba(37, 99, 235, 0.08);
}

.advisor-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.advisor-card__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.advisor-card__eyebrow {
  display: inline-flex;
  margin-bottom: 4px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.advisor-card__head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.advisor-card__badge {
  flex-shrink: 0;
  padding: 5px 10px;
  border-radius: 999px;
  background: #0f172a;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.advisor-card__source-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.advisor-card__grid {
  display: grid;
  gap: 10px;
}

.advisor-card__grid article {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
}

.advisor-card__grid strong,
.advisor-card__lists h4 {
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
}

.advisor-card__grid p,
.advisor-card__note {
  margin: 6px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.advisor-card__lists {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.advisor-card__lists > div {
  padding: 12px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.advisor-card__lists h4 {
  margin: 0 0 8px;
}

.advisor-card__lists ul,
.advisor-card__lists ol {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 12px;
  line-height: 1.75;
}

.advisor-card__note {
  margin-top: 10px;
  color: #64748b;
}

.digest-grid {
  display: grid;
  gap: 14px;
  padding-top: 18px;
}

.digest-card {
  border-radius: 22px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.06);
  padding: 18px;
}

.digest-card--warn {
  background: linear-gradient(180deg, #fff, #fff7ed);
}

.digest-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.digest-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.digest-item {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: #f8fafc;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #334155;
}

.digest-item strong {
  font-size: 14px;
  color: #0f172a;
}

.digest-item span {
  font-size: 12px;
  line-height: 1.6;
}

.tabs-bar {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-top: 18px;
}

.tab-btn {
  flex-shrink: 0;
  min-height: 40px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.tab-btn.active {
  background: #eff6ff;
  color: #1d4ed8;
  border-color: #93c5fd;
}

.tab-num {
  margin-left: 6px;
  color: #94a3b8;
}

.compare-bar {
  margin-top: 18px;
  padding-top: 16px;
  padding-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 20px;
}

.compare-bar--active {
  background: #eff6ff;
}

.compare-copy {
  flex: 1;
}

.compare-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.compare-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.compare-btn {
  padding: 10px 14px;
  border: none;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #0f172a;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.compare-btn:disabled {
  opacity: 0.45;
}

.list-status {
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.list-status strong {
  color: #0f172a;
  font-size: 13px;
}

.list-status small {
  color: #2563eb;
  font-weight: 800;
}

.plan-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 8px;
}

.plan-list-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  padding: 10px 0 2px;
}

.plan-list-controls button {
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.plan-row {
  padding: 0;
  overflow: hidden;
}

.plan-row__summary {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  outline: none;
}

.plan-row__summary:focus-visible {
  box-shadow: inset 0 0 0 2px rgba(37, 99, 235, 0.35);
}

.plan-row--expanded .plan-row__summary {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.plan-row__header {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.plan-row__rank {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 900;
}

.plan-row__main {
  flex: 1;
  min-width: 0;
}

.plan-row__badges,
.plan-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.grad-badge,
.match-badge,
.source-badge,
.confidence-badge,
.meta-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.grad-badge {
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.match-badge {
  color: #1d4ed8;
  background: #eff6ff;
}

.source-badge {
  color: #0f766e;
  background: #ecfdf5;
}

.confidence--high {
  color: #047857;
  background: #ecfdf5;
}

.confidence--mid {
  color: #b45309;
  background: #fffbeb;
}

.confidence--low {
  color: #b91c1c;
  background: #fef2f2;
}

.plan-row__school {
  margin-top: 8px;
  font-size: 18px;
  line-height: 1.2;
  font-weight: 900;
  color: #0f172a;
}

.plan-row__major {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 700;
  color: #2563eb;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.plan-row__meta {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.plan-row__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.plan-row__aside {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 12px;
  background: #f8fafc;
}

.plan-row__aside-prob {
  font-size: 22px;
  font-weight: 900;
  color: #0f172a;
}

.plan-row__aside-fit {
  font-size: 18px;
  letter-spacing: 0;
}

.plan-row__aside-label {
  font-size: 12px;
  color: #64748b;
}

.meta-tag {
  color: #334155;
  background: #f8fafc;
}

.compare-toggle {
  align-self: flex-start;
  min-height: 30px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(37, 99, 235, 0.15);
  background: #fff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.compare-toggle.active {
  background: #eff6ff;
}

.plan-row__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.plan-row__quick span {
  min-height: 30px;
  padding: 5px 8px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
  display: flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
}

.plan-row__quick em {
  font-style: normal;
  font-size: 11px;
  color: #64748b;
}

.plan-row__quick strong {
  min-width: 0;
  font-size: 12px;
  color: #0f172a;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.plan-row__compact-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-toggle {
  align-self: flex-start;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 800;
}

.detail-toggle svg {
  transition: transform 0.18s ease;
}

.detail-toggle svg.rotated {
  transform: rotate(180deg);
}

.plan-row__detail {
  padding: 0 16px 16px;
}

.plan-row__insights {
  display: grid;
  gap: 10px;
}

.reason-box {
  padding: 14px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.reason-box--warn {
  background: #fff7ed;
}

.reason-box--info {
  background: #f8fbff;
  border-color: #bfdbfe;
}

.reason-box--precision {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.reason-box--full {
  background: linear-gradient(160deg, #fffefb 0%, #f8fbff 100%);
  border-color: #d8e6ff;
}

.reason-box--rebalance {
  background: linear-gradient(135deg, #fff8eb 0%, #fefce8 100%);
  border-color: #fde68a;
}

.reason-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
}

.reason-box p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: #475569;
}

.reason-fallback {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: #64748b;
}

.reason-segment-list {
  display: grid;
  gap: 8px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.reason-segment {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  border-left: 3px solid currentColor;
  background: #ffffff;
}

.reason-segment__label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: currentColor;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid currentColor;
  white-space: nowrap;
}

.reason-segment__body {
  margin: 0;
  font-size: 13px;
  line-height: 1.72;
  color: #1f2933;
}

.reason-segment--gradient {
  color: #1d4ed8;
  background: #eff6ff;
}

.reason-segment--fit {
  color: #047857;
  background: #ecfdf5;
}

.reason-segment--risk {
  color: #b45309;
  background: #fff7ed;
}

.reason-segment--adjust {
  color: #7c3aed;
  background: #f5f3ff;
}

.auto-rebalance-card {
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid #fde68a;
  background: linear-gradient(135deg, #fff8eb 0%, #fef3c7 100%);
  display: grid;
  gap: 10px;
}

.auto-rebalance-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.auto-rebalance-card__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #fef3c7;
  color: #92400e;
  font-size: 12px;
  font-weight: 800;
  border: 1px solid #fbbf24;
}

.auto-rebalance-card__count {
  font-size: 13px;
  color: #78350f;
}

.auto-rebalance-card__count strong {
  font-size: 18px;
  font-weight: 850;
  color: #b45309;
  margin: 0 2px;
}

.auto-rebalance-card__metrics {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.auto-rebalance-card__metric {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.7);
}

.auto-rebalance-card__metric em {
  font-style: normal;
  font-size: 11px;
  color: #92400e;
  letter-spacing: 0.04em;
}

.auto-rebalance-card__metric strong {
  font-size: 16px;
  font-weight: 850;
  color: #78350f;
}

.auto-rebalance-card__metric--after strong {
  color: #047857;
}

.auto-rebalance-card__arrow {
  color: #b45309;
}

.auto-rebalance-card__delta {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #d1fae5;
  color: #065f46;
  font-size: 12px;
  font-weight: 800;
}

.auto-rebalance-card__note {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  color: #78350f;
}

.rebalance-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fbbf24;
  white-space: nowrap;
}

.history-card {
  margin-top: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.history-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.history-card__head h4 {
  font-size: 14px;
  color: #0f172a;
  font-weight: 900;
}

.history-card__head p {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.history-card__badge {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 11px;
  font-weight: 800;
}

.history-card__badge--warn {
  background: #fff7ed;
  color: #b45309;
}

.group-major-tags {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.group-major-tags span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #dbeafe;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.history-record-list {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.history-record {
  display: grid;
  grid-template-columns: 52px repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.history-record__year {
  width: 44px;
  height: 32px;
  border-radius: 10px;
  background: #0f172a;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 900;
}

.history-record span {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-record em {
  font-style: normal;
  color: #64748b;
  font-size: 11px;
}

.history-record strong {
  color: #0f172a;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-record__source {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  justify-content: center;
}

.history-record__rank-source {
  justify-self: start;
  align-self: center;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.2;
}

.history-record__rank-source.is-original {
  color: #047857;
  background: #ecfdf5;
}

.history-record__rank-source.is-converted {
  color: #1d4ed8;
  background: #eff6ff;
}

.history-record__rank-source.is-missing {
  color: #b45309;
  background: #fff7ed;
}

.history-record__note {
  grid-column: 2 / -1;
  margin: 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.55;
}

.history-empty {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff7ed;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
}

.plan-row__footer {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fit-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.detail-link {
  align-self: flex-start;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(37, 99, 235, 0.15);
  border-radius: 999px;
  background: #fff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
}

.detail-fade-enter-active,
.detail-fade-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.detail-fade-enter-from,
.detail-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.ai-workbench-card {
  scroll-margin-top: 78px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid #dbeafe;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(37, 99, 235, 0.08);
}

.ai-workbench-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-workbench-card__eyebrow {
  display: inline-flex;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.06em;
}

.ai-workbench-card h3 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.ai-workbench-card__refresh {
  flex-shrink: 0;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.ai-workbench-card__refresh:disabled {
  opacity: 0.65;
}

.ai-context-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 8px;
}

.ai-context-chip {
  padding: 9px 10px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  display: grid;
  gap: 3px;
}

.ai-context-chip span {
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
}

.ai-context-chip strong {
  color: #0f172a;
  font-size: 12px;
  line-height: 1.35;
}

.ai-summary-grid {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.ai-summary-card {
  min-height: 96px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.ai-summary-card strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
}

.ai-summary-card p {
  margin: 7px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.ai-deep-details {
  margin-top: 12px;
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
  padding: 10px 12px;
}

.ai-deep-details summary {
  cursor: pointer;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 900;
}

.ai-deep-sections {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.ai-deep-sections article {
  padding: 10px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.ai-deep-sections h4 {
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
}

.ai-deep-sections p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}

.skill-chat-box {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.skill-chat-box__head h4 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 900;
}

.skill-chat-box__head p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.quick-question-row {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-question-row button {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.skill-message-list {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.skill-message {
  max-width: 86%;
  padding: 10px 12px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.65;
}

.skill-message--user {
  justify-self: end;
  background: #0f172a;
  color: #ffffff;
  white-space: pre-wrap;
}

.skill-message--assistant {
  justify-self: start;
  background: #f8fafc;
  color: #334155;
  border: 1px solid #e2e8f0;
}

.result-disclaimer {
  margin-bottom: 14px;
}

.ai-reply-notice {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: #94a3b8;
}

.skill-message__markdown {
  display: grid;
  gap: 10px;
  overflow-wrap: anywhere;
  white-space: normal;
}

.skill-message__markdown :deep(p),
.skill-message__markdown :deep(ul),
.skill-message__markdown :deep(ol),
.skill-message__markdown :deep(blockquote),
.skill-message__markdown :deep(pre),
.skill-message__markdown :deep(hr) {
  margin: 0;
}

.skill-message__markdown :deep(p + p) {
  margin-top: 2px;
}

.skill-message__markdown :deep(h2),
.skill-message__markdown :deep(h3),
.skill-message__markdown :deep(h4) {
  margin: 8px 0 0;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 900;
}

.skill-message__markdown :deep(.markdown-section-title) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  margin-top: 10px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #075985;
  font-size: 12px;
  line-height: 1.4;
  font-weight: 900;
}

.skill-message__markdown :deep(.markdown-section-title::before) {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #0284c7;
}

.skill-message__markdown :deep(ul),
.skill-message__markdown :deep(ol) {
  padding-left: 18px;
}

.skill-message__markdown :deep(li + li) {
  margin-top: 4px;
}

.skill-message__markdown :deep(blockquote) {
  padding-left: 10px;
  border-left: 3px solid #bfdbfe;
  color: #475569;
}

.skill-message__markdown :deep(a) {
  color: #1d4ed8;
  font-weight: 800;
  text-decoration: none;
}

.skill-message__markdown :deep(a:hover) {
  text-decoration: underline;
}

.skill-message__markdown :deep(code) {
  padding: 1px 5px;
  border-radius: 6px;
  background: #e2e8f0;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 0.92em;
}

.skill-message__markdown :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  padding: 10px 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  line-height: 1.55;
}

.skill-message__markdown :deep(pre code) {
  display: block;
  padding: 0;
  background: transparent;
  color: inherit;
  white-space: pre;
}

.skill-message__markdown :deep(hr) {
  height: 1px;
  border: 0;
  background: #cbd5e1;
}

.skill-message__markdown :deep(.markdown-table-wrap) {
  max-width: 100%;
  overflow-x: auto;
  margin: 2px 0 6px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.03);
}

.skill-message__markdown :deep(table) {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  font-size: 12px;
  line-height: 1.5;
}

.skill-message__markdown :deep(th),
.skill-message__markdown :deep(td) {
  padding: 8px 10px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: top;
}

.skill-message__markdown :deep(th) {
  position: sticky;
  top: 0;
  background: #eff6ff;
  color: #1e3a8a;
  font-weight: 900;
  white-space: nowrap;
}

.skill-message__markdown :deep(td) {
  color: #334155;
}

.skill-message__markdown :deep(tr:last-child td) {
  border-bottom: 0;
}

.skill-input-row {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.skill-input-row input {
  min-width: 0;
  min-height: 40px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
}

.skill-input-row button {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  border: none;
  background: #0f172a;
  color: #ffffff;
  font-size: 13px;
  font-weight: 900;
}

.skill-input-row button:disabled {
  opacity: 0.5;
}

.ai-error {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.6;
}

.bottom-actions {
  display: none;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.action-btn {
  min-height: 46px;
  border-radius: 14px;
  border: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 800;
}

.action-btn--primary {
  grid-column: 1 / -1;
  background: #0f172a;
  color: #fff;
}

.action-btn--secondary {
  background: #eff6ff;
  color: #1d4ed8;
}

@media (min-width: 1024px) {
  .hero-panel {
    grid-template-columns: minmax(0, 1.72fr) minmax(300px, 0.88fr);
  }

  .result-workbench {
    max-width: 1240px;
    grid-template-columns: minmax(0, 1fr) 216px;
    gap: 18px;
  }

  .result-tools {
    position: sticky;
    top: 76px;
  }

  .digest-grid {
    grid-template-columns: 1fr 1fr;
  }

  .algorithm-range-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .advisor-card__grid,
  .advisor-card__lists {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .ai-deep-sections {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .card-top {
    flex-direction: row;
    align-items: flex-start;
  }

  .plan-row__header {
    grid-template-columns: 42px minmax(0, 1fr) 140px;
    gap: 14px;
  }

  .plan-row__aside {
    grid-column: auto;
    flex-direction: column;
    align-items: flex-end;
    justify-content: flex-start;
    padding: 0;
    border: none;
    background: transparent;
  }

  .plan-row__quick {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .plan-row__compact-footer {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }

  .bottom-actions {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .action-btn--primary {
    grid-column: auto;
  }
}

@media (min-width: 1280px) {
  .hero-panel {
    max-width: 1240px;
    grid-template-columns: minmax(0, 1.85fr) minmax(340px, 0.9fr);
  }

  .result-workbench {
    max-width: 1240px;
    grid-template-columns: minmax(0, 1fr) 236px;
    gap: 20px;
  }

  .hero-main {
    padding: 28px;
  }

  .hero-title {
    font-size: 34px;
  }

  .digest-card {
    padding: 22px;
  }

  .compare-bar {
    padding-left: 24px;
    padding-right: 24px;
    border: 1px solid rgba(15, 23, 42, 0.06);
    background: rgba(255, 255, 255, 0.92);
  }
}

@media (min-width: 1440px) {
  .plan-row__header {
    grid-template-columns: 44px minmax(0, 1fr) 150px;
    gap: 14px;
  }

  .plan-row {
    padding: 0;
  }

  .plan-row__aside {
    flex-direction: column;
    align-items: flex-end;
    justify-content: flex-start;
  }

  .plan-row__summary {
    padding: 16px 20px;
  }

  .plan-row__insights {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .plan-row__detail {
    padding: 0 20px 18px;
  }

  .history-record {
    grid-template-columns: 58px repeat(6, minmax(0, 1fr));
    align-items: center;
  }
}

.reference-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 14px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #b45309;
  font-size: 12px;
  line-height: 1.6;
}

.draft-status-bar {
  margin: 16px 0;
  padding: 14px 18px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.draft-status-bar__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.draft-status-bar__title {
  font-weight: 700;
  color: #0f172a;
  font-size: 14px;
}

.draft-status-bar__hint {
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.draft-status-bar__metrics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.draft-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.draft-status-pill--keep {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.draft-status-pill--review {
  background: #fff7ed;
  color: #b45309;
  border: 1px solid #fed7aa;
}

.draft-status-pill--drop {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.draft-export-btn {
  min-height: 30px;
  padding: 0 12px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
}

.manual-review-card {
  margin-top: 16px;
  padding: 18px 20px;
  border-radius: 18px;
  background: #fffaf3;
  border: 1px solid #fcd9b6;
}

.manual-review-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.manual-review-card__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: #b45309;
}

.manual-review-card__desc {
  margin-top: 6px;
  color: #92400e;
  font-size: 12px;
  line-height: 1.7;
}

.manual-review-card__count {
  flex-shrink: 0;
  align-self: flex-start;
  font-size: 12px;
  font-weight: 700;
  color: #b45309;
  background: #fff;
  border: 1px solid #fcd9b6;
  border-radius: 999px;
  padding: 4px 10px;
}

.manual-review-card__summary,
.manual-review-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.manual-review-card__count--risk {
  color: #b91c1c;
  border-color: #fecaca;
  background: #fef2f2;
}

.manual-review-preview {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.manual-review-preview li {
  padding: 9px 10px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #fcd9b6;
  display: grid;
  gap: 4px;
}

.manual-review-preview strong {
  color: #78350f;
  font-size: 12px;
  font-weight: 800;
}

.manual-review-preview span {
  color: #92400e;
  font-size: 12px;
  line-height: 1.55;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.manual-review-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.manual-review-list__item {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #fcd9b6;
  padding: 12px 14px;
}

.manual-review-list__row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.manual-review-list__index {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  background: #b45309;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.manual-review-list__title {
  font-weight: 600;
  color: #1f2937;
  font-size: 13px;
}

.manual-review-list__gradient {
  font-size: 12px;
  color: #b45309;
}

.manual-review-list__reasons {
  margin-top: 6px;
  padding-left: 18px;
  color: #b45309;
  font-size: 12px;
  line-height: 1.7;
}

.manual-review-list__links {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.manual-review-list__links a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #fff7ed;
  border: 1px solid #fcd9b6;
  color: #92400e;
  font-size: 11px;
  text-decoration: none;
}

.manual-review-list__links a:hover {
  background: #fed7aa;
}

.manual-review-card__toggle {
  margin-top: 10px;
  padding: 6px 12px;
  border-radius: 999px;
  background: transparent;
  border: 1px solid #fcd9b6;
  color: #b45309;
  font-size: 12px;
  cursor: pointer;
}

.evidence-chain {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.evidence-chain__head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.evidence-chain__links {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.evidence-chain__links a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #2563eb;
  font-size: 11px;
  text-decoration: none;
}

.evidence-chain__links a:hover {
  background: #eff6ff;
}

.evidence-chain__flag {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 999px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 11px;
  font-weight: 600;
}

.plan-row__footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-toggle {
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.status-toggle:hover {
  background: #f8fafc;
}

.status-toggle--keep {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #047857;
}

.status-toggle--review {
  background: #fff7ed;
  border-color: #fed7aa;
  color: #b45309;
}

.status-toggle--drop {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.plan-row--status-keep {
  border-color: #a7f3d0;
  box-shadow: 0 0 0 1px rgba(16, 185, 129, 0.2);
}

.plan-row--status-review {
  border-color: #fed7aa;
}

.plan-row--status-drop {
  opacity: 0.55;
}

@media (max-width: 767px) {
  .result-page {
    padding-bottom: 28px;
  }

  .hero-panel,
  .result-workbench {
    padding-left: 12px;
    padding-right: 12px;
  }

  .hero-main {
    padding: 18px;
    border-radius: 18px;
  }

  .hero-title {
    font-size: 24px;
  }

  .hero-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .result-tools {
    order: -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 10px;
    border-radius: 16px;
  }

  .result-tool {
    justify-content: center;
    min-width: 0;
    padding: 0 8px;
  }

  .result-tool--primary {
    grid-column: 1 / -1;
  }

  .manual-review-card,
  .ai-workbench-card {
    padding: 14px;
    border-radius: 16px;
  }

  .manual-review-card__head,
  .ai-workbench-card__head {
    flex-direction: column;
    align-items: stretch;
  }

  .manual-review-card__summary,
  .manual-review-card__actions {
    justify-content: flex-start;
  }

  .ai-context-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .skill-input-row {
    grid-template-columns: 1fr;
  }

  .skill-message {
    max-width: 100%;
  }

  .history-record {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .history-record__year {
    width: auto;
  }
}
</style>
