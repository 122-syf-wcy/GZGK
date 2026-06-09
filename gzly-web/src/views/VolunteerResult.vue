<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { fetchVolunteerPlan } from '@/api/volunteer'
import { useVolunteerStore } from '@/stores/volunteer'
import RecommendSection from '@/components/RecommendSection.vue'
import SafeExternalLink from '@/components/SafeExternalLink.vue'
import UserFeedbackDialog from '@/components/UserFeedbackDialog.vue'
import type { GradientRangeDetail, HistoryRecord, VolunteerItem } from '@/types'
import { buildPlanModeItems, formDataFromPlan, summarizePlan, type PlanMode } from '@/utils/volunteer-plan'
import { sanitizeHttpUrl } from '@/utils/markdown'
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

const activeTab = ref<'all' | '冲' | '稳' | '保' | '垫'>('all')
const activeMode = ref<PlanMode>(volunteerStore.formData.strategyMode || '均衡型')
const schoolSearch = ref('')
const probabilitySort = ref<'default' | 'desc' | 'asc'>('default')
const compareIds = ref<string[]>([])
const restoring = ref(false)
const showAllReview = ref(false)
const expandedRows = ref<Set<string>>(new Set())

/** 志愿草稿状态：保留 / 待查 / 淘汰 / 默认。本地持久化。 */
type DraftStatus = 'keep' | 'review' | 'drop' | 'default'
const STATUS_STORAGE_KEY = computed(() => `gz_volunteer_status_${volunteerStore.planId ?? 0}`)
const itemStatuses = ref<Record<string, DraftStatus>>(loadStatuses())

function statusKey(item: { schoolId?: string; groupCode?: string; majorName: string; index: number }) {
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

function cycleStatus(item: { schoolId?: string; majorName: string; index: number }) {
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

function statusOf(item: { schoolId?: string; majorName: string; index: number }): DraftStatus {
  return itemStatuses.value[statusKey(item)] || 'default'
}

function isExpanded(item: { schoolId?: string; majorName: string; index: number }) {
  return expandedRows.value.has(statusKey(item))
}

function toggleDetail(item: { schoolId?: string; majorName: string; index: number }) {
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
  Object.values(itemStatuses.value).forEach((s) => {
    if (s in counts) counts[s as keyof typeof counts]++
  })
  return counts
})

const visibleManualReviews = computed(() => {
  const list = volunteerStore.manualReviewItems
  if (!list || !list.length) return []
  return showAllReview.value ? list : list.slice(0, 6)
})

const gradientConfig: Record<string, { color: string; bg: string }> = {
  冲: { color: '#dc2626', bg: '#fef2f2' },
  稳: { color: '#2563eb', bg: '#eff6ff' },
  保: { color: '#059669', bg: '#ecfdf5' },
  垫: { color: '#d97706', bg: '#fffbeb' },
}

const modeItems = computed(() => buildPlanModeItems(volunteerStore.planItems, activeMode.value))

const decisionDraftItems = computed(() =>
  modeItems.value.filter(item => {
    const status = statusOf(item)
    return status === 'keep' || status === 'review'
  }),
)

const summary = computed(() => summarizePlan(modeItems.value))
const rangeSummary = computed(() => volunteerStore.gradientRangeSummary)
const rankEstimateSummary = computed(() => volunteerStore.rankEstimate)
const rangeSummaryRows = computed(() => {
  const ranges = rangeSummary.value?.ranges
  if (!ranges) return [] as GradientRangeDetail[]
  return (['冲', '稳', '保', '垫'] as const)
    .flatMap(key => ranges[key] ? [ranges[key] as GradientRangeDetail] : [])
})
const planProvinceCode = computed(() => normalizeProvinceCode(volunteerStore.formData.provinceCode || volunteerStore.planItems[0]?.provinceCode || 'GZ'))
const planProvinceConfig = computed(() => getProvinceConfig(planProvinceCode.value))
const isProfessionalGroupPlan = computed(() => planProvinceConfig.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45')
const planProvinceName = computed(() => planProvinceConfig.value.shortName)
const planUnitLabel = computed(() => (isProfessionalGroupPlan.value ? '院校专业组' : '志愿'))
const isStrategyAdvicePlan = computed(() => (
  volunteerStore.policy?.supportLevel === 'QUERY_ONLY' ||
  volunteerStore.policy?.recommendMode === 'QUERY_ONLY' ||
  volunteerStore.modelInfo?.visibleMetric === 'query_only' ||
  !volunteerStore.planItems.length
))
const planTitle = computed(() => `${planProvinceName.value}${isStrategyAdvicePlan.value ? '策略建议方案' : `${planUnitLabel.value}方案`}`)
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
    { key: '冲', label: '冲', count: items.filter(i => i.gradient === '冲').length },
    { key: '稳', label: '稳', count: items.filter(i => i.gradient === '稳').length },
    { key: '保', label: '保', count: items.filter(i => i.gradient === '保').length },
    { key: '垫', label: '垫', count: items.filter(i => i.gradient === '垫').length },
  ] as const
})

function publicFacingText(text?: string) {
  return String(text || '')
    .replace(/PRE_OFFICIAL_DATA/g, '官方数据待发布')
    .replace(/TRIAL_RECOMMEND/g, '历史估算')
    .replace(/ESTIMATE_RECOMMEND/g, '历史估算')
    .replace(/QUERY_ONLY/g, '策略建议')
    .replace(/FULL_RECOMMEND/g, '历史估算')
    .replace(/QERY_ONLY/g, '策略建议')
}

function missingDataText(key: string) {
  const labels: Record<string, string> = {
    official_2026_admission_plan: '2026 官方招生计划未发布/未导入',
    official_2026_score_or_rank: '2026 官方分数位次未发布/未导入',
    formal_recommend_candidate_pool: '正式推荐候选池未达到开放条件',
    hi_score_rank_3plus3_official_source: '海南 3+3 一分一段官方源仍需补齐',
    yn_2025_new_gaokao_ocr_reviewed_source: '云南 2025 新高考结构化源仍在 OCR/人工复核',
    ha_official_core_gaokao_source: '河南普通高考核心官方源仍待补齐',
  }
  return labels[key] || key
}

const strategyMissingItems = computed(() => {
  const missing = volunteerStore.warnings || []
  return missing.length ? missing.map(item => publicFacingText(String(item))) : []
})

const strategyReason = computed(() => publicFacingText(
  volunteerStore.policy?.supportReason ||
  volunteerStore.policy?.supportNote ||
  volunteerStore.dataQualityWarning ||
  '当前批次仅开放策略建议和数据缺口说明。',
))

const strategyCards = computed(() => [
  {
    title: '当前批次规则',
    text: `${planProvinceName.value}当前仍处于官方数据待发布阶段，系统保留批次入口并按省份规则解释，不跨省回退。`,
  },
  {
    title: '数据缺口',
    text: strategyMissingItems.value.length ? strategyMissingItems.value.join('；') : strategyReason.value,
  },
  {
    title: '当前可做',
    text: '可先查看院校、分数线、特长生专区和政策状态；待官方源补齐后再升级为更完整的历史估算或正式数据查询。',
  },
])

function scoreRankText(record?: Pick<HistoryRecord, 'minScore' | 'minRank'>) {
  if (!record || (!record.minScore && !record.minRank)) return '--'
  const score = record.minScore ? `${record.minScore}分` : '--'
  const rank = record.minRank ? `${record.minRank.toLocaleString()}位` : '--'
  return `${score} / ${rank}`
}

function historyByYear(item: VolunteerItem, year: number) {
  return (item.historyRecords || []).find(record => record.year === year)
}

function displayValue(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '--'
  if (typeof value === 'number') return value > 0 ? value.toLocaleString() : '--'
  return value
}

function probabilityText(item: VolunteerItem) {
  if (typeof item.chanceScore === 'number' && item.chanceScore > 0) return `${Math.round(item.chanceScore)}%`
  if (item.referenceFitLevel) return item.referenceFitLevel
  return '参考不足'
}

function sourceStatusText(item: VolunteerItem) {
  return item.sourceStatus || item.confidenceLabel || item.dataSourceType || '待复核'
}

function majorCodeText(item: VolunteerItem) {
  if (item.majorCode) return item.majorCode
  if (item.sourceMajorId) return `源ID ${item.sourceMajorId}`
  return '--'
}

function rowMissingReason(item: VolunteerItem) {
  const reasons = new Set<string>()
  if (item.missingReason) item.missingReason.split(/[；;]/).forEach(reason => reason && reasons.add(reason.trim()))
  if (!item.majorCode && !item.sourceMajorId && !isProfessionalGroupPlan.value) reasons.add('缺官方结构化专业代码')
  if (!item.duration) reasons.add('缺学制')
  if (!item.tuition) reasons.add('缺学费')
  if (!item.latestPlanCount && !item.planCount) reasons.add('缺招生人数')
  return Array.from(reasons).filter(Boolean).join('；') || '字段来源已结构化'
}

function groupMajorSummary(item: VolunteerItem) {
  const majors = item.professionalMajors || []
  if (majors.length) return majors.slice(0, 3).map(major => major.majorName).filter(Boolean).join('、')
  return (item.groupMajors || []).slice(0, 3).join('、') || item.majorName
}

function professionalDetailTitle() {
  return isProfessionalGroupPlan.value ? '院校专业组明细' : '专业明细与来源'
}

const filteredItems = computed(() => {
  const keyword = schoolSearch.value.trim().toLowerCase()
  let items = activeTab.value === 'all'
    ? modeItems.value
    : modeItems.value.filter(item => item.gradient === activeTab.value)
  if (keyword) {
    items = items.filter(item => [
      item.universityName,
      item.schoolCode,
      item.schoolId,
      item.groupCode,
      item.majorCode,
      item.sourceMajorId,
      item.majorName,
      ...(item.groupMajors || []),
      ...((item.professionalMajors || []).map(major => `${major.majorCode || ''} ${major.majorName || ''}`)),
    ].join(' ').toLowerCase().includes(keyword))
  }
  if (probabilitySort.value !== 'default') {
    items = [...items].sort((a, b) => {
      const diff = (a.chanceScore || 0) - (b.chanceScore || 0)
      return probabilitySort.value === 'desc' ? -diff : diff
    })
  }
  return items
})

const professionalGapSummary = computed(() => {
  const reasons = new Map<string, number>()
  modeItems.value.forEach(item => {
    rowMissingReason(item).split(/[；;]/).forEach(reason => {
      const text = reason.trim()
      if (!text || text === '字段来源已结构化') return
      reasons.set(text, (reasons.get(text) || 0) + 1)
    })
  })
  return Array.from(reasons.entries()).map(([label, count]) => ({ label, count })).slice(0, 5)
})

watch([activeTab, activeMode], () => {
  expandedRows.value = new Set()
})

const topKeeps = computed(() =>
  [...modeItems.value]
    .sort((a, b) => ((b.recommendationScore || 0) * 1.5 + (fitRank(b) * 1.2) + (b.dataConfidenceScore || 0) + (b.matchScore || 0)) - ((a.recommendationScore || 0) * 1.5 + (fitRank(a) * 1.2) + (a.dataConfidenceScore || 0) + (a.matchScore || 0)))
    .slice(0, 5),
)

const topRisks = computed(() =>
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

onMounted(async () => {
  if (!volunteerStore.planItems.length) {
    await restorePlan()
  }
  if (!volunteerStore.planItems.length && !isStrategyAdvicePlan.value) {
    showToast('暂无志愿数据，请重新生成方案')
    router.push('/volunteer')
  }
})

async function restorePlan() {
  const planId = Number(route.query.planId || volunteerStore.getSavedPlanMeta()?.planId)
  const savedMeta = volunteerStore.getSavedPlanMeta()
  const accessKey = String(route.query.safetyCode || route.query.accessKey || savedMeta?.safetyCode || savedMeta?.accessKey || '')
  if (!planId || !accessKey) return
  restoring.value = true
  try {
    const res = await fetchVolunteerPlan(planId, accessKey)
    const plan = res.data.data
    const formData = formDataFromPlan(plan)
    volunteerStore.setPlanFromResponse(plan)
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
  router.push({
    path: '/volunteer/ai',
    query: {
      planId: volunteerStore.planId ? String(volunteerStore.planId) : undefined,
    },
  })
}

function safeUrl(url?: string | null) {
  return sanitizeHttpUrl(url)
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
    query: { schoolId: item.schoolId, provinceCode: planProvinceCode.value },
  })
}

async function loadXlsx() {
  return await import('xlsx')
}

type ProfessionalExportRow = Record<string, string | number | boolean>

const PROFESSIONAL_EXPORT_HEADERS = [
  'riskTier',
  'admissionProbability',
  'volunteerIndex',
  'schoolCode',
  'schoolName',
  'majorGroupCode',
  'majorCode',
  'majorName',
  'majorDescription',
  'minScore2025',
  'minRank2025',
  'minScore2024',
  'minRank2024',
  'minScore2023',
  'minRank2023',
  'duration',
  'tuition',
  'planCount',
  'locked',
  'sourceYear',
  'sourceStatus',
  'missingReason',
  'manualReviewRequired',
]

function professionalFieldValue(value?: string | number | boolean | null) {
  if (value === undefined || value === null || value === '') return '--'
  if (typeof value === 'number') return value > 0 ? value : '--'
  return value
}

function firstProfessionalMajor(item: VolunteerItem) {
  return item.professionalMajors?.find(major => major.majorCode || major.majorName) || null
}

function professionalMajorName(item: VolunteerItem) {
  if (isProfessionalGroupPlan.value) return groupMajorSummary(item) || item.majorName
  return item.majorName
}

function professionalMajorCode(item: VolunteerItem) {
  if (item.majorCode) return item.majorCode
  const major = firstProfessionalMajor(item)
  if (major?.majorCode) return major.majorCode
  if (item.sourceMajorId) return `source:${item.sourceMajorId}`
  return '--'
}

function professionalMajorDescription(item: VolunteerItem) {
  const major = firstProfessionalMajor(item)
  return item.majorDescription || item.resubjectRequirement || major?.majorDescription || major?.resubjectRequirement || rowMissingReason(item)
}

function professionalDuration(item: VolunteerItem) {
  return item.duration || firstProfessionalMajor(item)?.duration || '--'
}

function professionalTuition(item: VolunteerItem) {
  return item.tuition || firstProfessionalMajor(item)?.tuition || '--'
}

function professionalPlanCount(item: VolunteerItem) {
  return item.latestPlanCount || item.planCount || firstProfessionalMajor(item)?.planCount || '--'
}

function professionalHistoryValue(item: VolunteerItem, year: number, key: 'minScore' | 'minRank') {
  const record = historyByYear(item, year)
  return professionalFieldValue(record?.[key])
}

function professionalExportRows(items = modeItems.value): ProfessionalExportRow[] {
  return items.map(item => ({
    riskTier: item.gradient,
    admissionProbability: probabilityText(item),
    volunteerIndex: item.index,
    schoolCode: professionalFieldValue(item.schoolCode || item.schoolId),
    schoolName: item.universityName,
    majorGroupCode: professionalFieldValue(item.groupCode),
    majorCode: professionalMajorCode(item),
    majorName: professionalMajorName(item),
    majorDescription: professionalMajorDescription(item),
    minScore2025: professionalHistoryValue(item, 2025, 'minScore'),
    minRank2025: professionalHistoryValue(item, 2025, 'minRank'),
    minScore2024: professionalHistoryValue(item, 2024, 'minScore'),
    minRank2024: professionalHistoryValue(item, 2024, 'minRank'),
    minScore2023: professionalHistoryValue(item, 2023, 'minScore'),
    minRank2023: professionalHistoryValue(item, 2023, 'minRank'),
    duration: professionalDuration(item),
    tuition: professionalTuition(item),
    planCount: professionalPlanCount(item),
    locked: item.locked ? '是' : '否',
    sourceYear: professionalFieldValue(item.sourceYear || item.referenceYear),
    sourceStatus: sourceStatusText(item),
    missingReason: rowMissingReason(item),
    manualReviewRequired: item.needsManualReview ? '是' : '否',
  }))
}

function professionalExportFileStem() {
  const form = volunteerStore.formData
  return `${planProvinceName.value}高考专业志愿表_${activeMode.value}_${form.totalScore}分_${form.provinceRank}位`
}

function csvEscape(value: string | number | boolean) {
  const text = String(value ?? '')
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

async function exportExcel() {
  try {
    const XLSX = await loadXlsx()
    const items = modeItems.value
    if (!items.length) {
      showToast('暂无志愿数据可导出')
      return
    }
    const rows = [PROFESSIONAL_EXPORT_HEADERS, ...professionalExportRows(items).map(row => PROFESSIONAL_EXPORT_HEADERS.map(key => row[key]))]

    const ws = XLSX.utils.aoa_to_sheet(rows)
    ws['!cols'] = PROFESSIONAL_EXPORT_HEADERS.map(key => ({ wch: ['majorDescription', 'missingReason'].includes(key) ? 42 : 16 }))

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'professional_table')
    const fileName = `${professionalExportFileStem()}.xlsx`
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

function exportCsv() {
  const items = modeItems.value
  if (!items.length) {
    showToast('暂无志愿数据可导出')
    return
  }
  const lines = [
    PROFESSIONAL_EXPORT_HEADERS.map(csvEscape).join(','),
    ...professionalExportRows(items).map(row => PROFESSIONAL_EXPORT_HEADERS.map(key => csvEscape(row[key])).join(',')),
  ]
  const file = new File([`\ufeff${lines.join('\n')}`], `${professionalExportFileStem()}.csv`, { type: 'text/csv;charset=utf-8' })
  fallbackDownload(file, file.name)
}

async function copyPlanSummary() {
  const form = volunteerStore.formData
  const metrics = volunteerStore.planMetrics
  const sourceYears = volunteerStore.trainingYears.length ? volunteerStore.trainingYears.join('/') : '2024/2025 历史数据'
  const batchName = volunteerStore.policy?.batchName || volunteerStore.policy?.batchCode || '当前批次'
  const lines = [
    `${planProvinceName.value}高考${isStrategyAdvicePlan.value ? '策略建议' : '专业志愿表'}方案`,
    `省份：${planProvinceName.value}`,
    `分数：${form.totalScore || '--'} 分`,
    `位次：${form.provinceRank ? form.provinceRank.toLocaleString() : '--'}`,
    `批次：${batchName}`,
    `生成类型：${isStrategyAdvicePlan.value ? '策略建议' : '历史估算'}`,
    `数据来源年份：${sourceYears}`,
    `志愿数量：${modeItems.value.length}`,
    `冲稳保数量：冲 ${metrics?.chongCount ?? tabs.value.find(tab => tab.key === '冲')?.count ?? 0} / 稳 ${metrics?.wenCount ?? tabs.value.find(tab => tab.key === '稳')?.count ?? 0} / 保 ${metrics?.baoCount ?? tabs.value.find(tab => tab.key === '保')?.count ?? 0} / 垫 ${metrics?.dianCount ?? tabs.value.find(tab => tab.key === '垫')?.count ?? 0}`,
    '提示：当前为官方数据待发布阶段，本方案基于历史数据估算，仅供辅助参考。',
  ]
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    showSuccessToast('方案摘要已复制')
  } catch {
    showToast('复制失败，请手动复制页面摘要')
  }
}

function printAsPdf() {
  window.print()
}

async function exportDecisionDraft() {
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
      ...items.map((item, idx) => [
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
        <button v-if="!isStrategyAdvicePlan" class="header-btn" @click="exportExcel" title="导出 Excel">
          <FileSpreadsheet :size="18" />
        </button>
        <button v-if="!isStrategyAdvicePlan" class="header-btn" @click="copyPlanSummary" title="复制方案摘要">
          <ClipboardCheck :size="18" />
        </button>
      </div>
    </header>

    <section class="hero-panel">
      <div v-if="restoring" class="result-warning">
        正在恢复已生成的志愿方案...
      </div>
      <div v-if="volunteerStore.dataQualityWarning" class="result-warning">
        {{ volunteerStore.dataQualityWarning }}
      </div>
      <div v-for="warning in volunteerStore.warnings" :key="warning" class="result-warning">
        {{ warning }}
      </div>
      <div v-if="rankEstimateSummary" class="rank-estimate-banner" :class="{ 'rank-estimate-banner--auto': rankEstimateSummary.rankEstimated }">
        <ShieldAlert :size="16" />
        <span>{{ rankEstimateSummary.reminder || rankEstimateSummary.note }}</span>
      </div>
      <div class="reference-banner">
        <ShieldAlert :size="16" />
        <span>{{ volunteerStore.referenceProbabilityNotice }}</span>
      </div>
      <div class="hero-main">
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
      <div v-if="!isStrategyAdvicePlan" class="hero-side">
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

    <section class="next-step-card">
      <div class="next-step-card__copy">
        <span class="next-step-card__kicker">next step</span>
        <h2>下一步你可以这样看</h2>
        <ol>
          <li>先看冲稳保分布，确认方案是否均衡。</li>
          <li>再看院校和专业是否符合兴趣。</li>
          <li>导出 Excel，和家长一起筛选。</li>
          <li>等 2026 官方数据发布后，再做最终复核。</li>
        </ol>
      </div>
      <div class="next-step-card__actions">
        <button v-if="!isStrategyAdvicePlan" type="button" @click="exportExcel">
          <FileSpreadsheet :size="15" /> 导出 Excel
        </button>
        <button type="button" @click="copyPlanSummary">
          <ClipboardCheck :size="15" /> 复制方案摘要
        </button>
        <button type="button" @click="router.push({ path: '/volunteer', query: { provinceCode: planProvinceCode } })">
          <ArrowLeft :size="15" /> 返回重新填写
        </button>
        <UserFeedbackDialog :province-code="planProvinceCode" :result-id="volunteerStore.planId" compact />
      </div>
    </section>

    <section v-if="!isStrategyAdvicePlan" class="mode-switch">
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

    <section v-if="isStrategyAdvicePlan" class="strategy-advice-card">
      <div class="strategy-advice-card__head">
        <span class="strategy-advice-card__badge">策略建议</span>
        <div>
          <h2>当前不生成院校志愿清单</h2>
          <p>{{ strategyReason }}</p>
        </div>
      </div>
      <div class="strategy-advice-grid">
        <article v-for="card in strategyCards" :key="card.title">
          <strong>{{ card.title }}</strong>
          <p>{{ card.text }}</p>
        </article>
      </div>
      <div class="strategy-action-row">
        <button type="button" class="strategy-action" @click="router.push({ path: '/score-line', query: { provinceCode: planProvinceCode } })">查看分数线</button>
        <button type="button" class="strategy-action" @click="router.push({ path: '/universities', query: { provinceCode: planProvinceCode } })">院校查询</button>
        <button type="button" class="strategy-action" @click="router.push({ path: '/special-admissions', query: { provinceCode: planProvinceCode } })">特长生专区</button>
      </div>
      <details class="strategy-field-spec">
        <summary>未来专业志愿表字段说明</summary>
        <div class="strategy-field-grid">
          <span>院校代码 / 专业组代码</span>
          <span>专业代码 / 专业名称</span>
          <span>2025/2024/2023 分数位次</span>
          <span>学制 / 学费 / 招生人数</span>
          <span>选科、语种、体检和单科限制</span>
          <span>官方来源和人工复核状态</span>
        </div>
        <p>GX/HI/YN/HA 当前不生成院校清单；官方结构化计划、专业代码、组内专业和历年位次补齐后，才会升级为专业志愿表。</p>
      </details>
    </section>

    <section v-if="rangeSummary && !isStrategyAdvicePlan" class="algorithm-card">
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

    <section v-if="volunteerStore.advisorAdvice && !isStrategyAdvicePlan" class="advisor-card">
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

    <section class="draft-status-bar" v-if="modeItems.length">
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
      v-if="volunteerStore.manualReviewItems && volunteerStore.manualReviewItems.length"
      class="manual-review-card"
    >
      <header class="manual-review-card__head">
        <div>
          <h3 class="manual-review-card__title">
            <ClipboardCheck :size="16" /> 强制人工复核清单
          </h3>
          <p class="manual-review-card__desc">
            以下条目自动识别出潜在风险（缺官方选科要求、院校级回退、合作办学、医学/军警/艺术等），
            最终结果以学校招生章程与考试院公告为准。
          </p>
        </div>
        <span class="manual-review-card__count">{{ volunteerStore.manualReviewItems.length }} 条</span>
      </header>
      <ul class="manual-review-list">
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
              <ExternalLink :size="12" /> 官方资料 {{ li + 1 }}
            </SafeExternalLink>
          </div>
        </li>
      </ul>
      <button
        v-if="volunteerStore.manualReviewItems.length > 6"
        class="manual-review-card__toggle"
        type="button"
        @click="showAllReview = !showAllReview"
      >
        {{ showAllReview ? '收起' : `展开剩余 ${volunteerStore.manualReviewItems.length - 6} 条` }}
      </button>
    </section>

    <section v-if="!isStrategyAdvicePlan" class="digest-grid">
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

    <section v-if="!isStrategyAdvicePlan" class="professional-table-panel">
      <div class="professional-table-head">
        <div>
          <span class="professional-table-kicker">professional volunteer table</span>
          <h2>志愿专业组方案明细</h2>
          <p>默认展示全部 {{ filteredItems.length }} 条，字段缺失以“--”标记并在缺口列说明；当前仍为历史估算，不代表 2026 正式数据。</p>
        </div>
        <div class="professional-table-actions">
          <button type="button" class="professional-export-btn" @click="exportExcel">
            <FileSpreadsheet :size="15" /> 导出 Excel
          </button>
          <button type="button" class="professional-export-btn" @click="exportCsv">
            <Download :size="15" /> 导出 CSV
          </button>
          <button type="button" class="professional-export-btn" @click="copyPlanSummary">
            <ClipboardCheck :size="15" /> 复制摘要
          </button>
          <button type="button" class="professional-export-btn" @click="printAsPdf">
            <Download :size="15" /> 打印 / 保存为 PDF
          </button>
          <button type="button" class="professional-export-btn" @click="exportDecisionDraft">
            <ClipboardCheck :size="15" /> 导出核验草稿
          </button>
        </div>
      </div>

      <div class="professional-gap-strip" v-if="professionalGapSummary.length">
        <span class="professional-gap-title">字段缺口</span>
        <span v-for="gap in professionalGapSummary" :key="gap.label" class="professional-gap-chip">{{ gap.label }} {{ gap.count }}</span>
      </div>

      <div class="professional-toolbar">
        <div class="professional-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="professional-tab"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }} <span>{{ tab.count }}</span>
          </button>
        </div>
        <div class="professional-filters">
          <input v-model="schoolSearch" type="search" placeholder="搜索院校 / 专业 / 代码" class="professional-search" />
          <select v-model="probabilitySort" class="professional-select" aria-label="按录取参考值排序">
            <option value="default">默认顺序</option>
            <option value="desc">参考值从高到低</option>
            <option value="asc">参考值从低到高</option>
          </select>
        </div>
      </div>

      <div class="professional-table-scroll">
        <table class="professional-table">
          <thead>
            <tr>
              <th>冲稳保</th>
              <th>录取参考值</th>
              <th>志愿序号</th>
              <th>院校代码</th>
              <th class="col-school">院校名称</th>
              <th>专业组代码</th>
              <th>专业代码</th>
              <th class="col-major">专业名称</th>
              <th class="col-desc">专业简介 / 限制说明</th>
              <th>2025最低分/排名</th>
              <th>2024最低分/排名</th>
              <th>2023最低分/排名</th>
              <th>学制</th>
              <th>学费</th>
              <th>招生人数</th>
              <th>是否锁定</th>
              <th>来源状态</th>
              <th>人工复核</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in filteredItems" :key="`${activeMode}-${item.index}-${item.schoolId}-${item.groupCode || item.majorName}`">
              <tr :class="['professional-row', `professional-row--${item.gradient}`, { 'is-expanded': isExpanded(item) }]">
                <td><span class="risk-tier" :style="{ color: gradientConfig[item.gradient].color, background: gradientConfig[item.gradient].bg }">{{ item.gradient }}</span></td>
                <td><strong>{{ probabilityText(item) }}</strong><small>{{ item.chanceLevel || '历史估算' }}</small></td>
                <td>{{ item.index }}</td>
                <td>{{ displayValue(item.schoolCode || item.schoolId) }}</td>
                <td class="col-school"><button type="button" class="table-school-link" @click="openUniversity(item)">{{ item.universityName }}</button><small>{{ [item.province, item.city].filter(Boolean).join(' · ') || '--' }}</small></td>
                <td>{{ displayValue(item.groupCode) }}</td>
                <td>{{ majorCodeText(item) }}</td>
                <td class="col-major"><span>{{ isProfessionalGroupPlan ? groupMajorSummary(item) : item.majorName }}</span></td>
                <td class="col-desc"><span :title="item.majorDescription || item.resubjectRequirement || rowMissingReason(item)">{{ item.majorDescription || item.resubjectRequirement || rowMissingReason(item) }}</span></td>
                <td>{{ scoreRankText(historyByYear(item, 2025)) }}</td>
                <td>{{ scoreRankText(historyByYear(item, 2024)) }}</td>
                <td>{{ scoreRankText(historyByYear(item, 2023)) }}</td>
                <td>{{ displayValue(item.duration) }}</td>
                <td>{{ displayValue(item.tuition) }}</td>
                <td>{{ displayValue(item.latestPlanCount || item.planCount) }}</td>
                <td>{{ item.locked ? '是' : '否' }}</td>
                <td><span class="source-status">{{ sourceStatusText(item) }}</span></td>
                <td><span :class="['review-status', item.needsManualReview ? 'is-needed' : '']">{{ item.needsManualReview ? '需复核' : '常规复核' }}</span></td>
                <td>
                  <button type="button" class="table-detail-btn" @click="toggleDetail(item)">
                    {{ isExpanded(item) ? '收起' : '展开' }}
                  </button>
                </td>
              </tr>
              <tr v-if="isExpanded(item)" class="professional-detail-row">
                <td colspan="19">
                  <div class="professional-detail-grid">
                    <section>
                      <h3>{{ professionalDetailTitle() }}</h3>
                      <div v-if="isProfessionalGroupPlan && item.professionalMajors?.length" class="major-detail-table-wrap">
                        <table class="major-detail-table">
                          <thead>
                            <tr>
                              <th>专业代码</th>
                              <th>专业名称</th>
                              <th>学制</th>
                              <th>学费</th>
                              <th>计划数</th>
                              <th>限制说明</th>
                              <th>来源</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="major in item.professionalMajors" :key="`${item.index}-${major.majorCode || major.majorName}`">
                              <td>{{ displayValue(major.majorCode) }}</td>
                              <td>{{ displayValue(major.majorName) }}</td>
                              <td>{{ displayValue(major.duration) }}</td>
                              <td>{{ displayValue(major.tuition) }}</td>
                              <td>{{ displayValue(major.planCount) }}</td>
                              <td>{{ major.majorDescription || major.resubjectRequirement || major.missingReason || '--' }}</td>
                              <td>{{ major.sourceStatus || major.sourceName || '--' }}</td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                      <div v-else class="professional-detail-empty">{{ rowMissingReason(item) }}</div>
                    </section>
                    <section>
                      <h3>近三年记录</h3>
                      <div class="history-mini-grid">
                        <span>2025 <strong>{{ scoreRankText(historyByYear(item, 2025)) }}</strong></span>
                        <span>2024 <strong>{{ scoreRankText(historyByYear(item, 2024)) }}</strong></span>
                        <span>2023 <strong>{{ scoreRankText(historyByYear(item, 2023)) }}</strong></span>
                      </div>
                      <p>{{ item.rangeNote || '按本次梯度区间和历史投档数据筛选，请结合官方信息复核。' }}</p>
                    </section>
                    <section>
                      <h3>风险与操作</h3>
                      <p>{{ item.riskReason || item.recommendReason || '请核对招生章程、专业目录、学费、选科、体检、语种和单科限制。' }}</p>
                      <div class="professional-detail-actions">
                        <button
                          class="status-toggle"
                          :class="`status-toggle--${statusOf(item)}`"
                          type="button"
                          @click="cycleStatus(item)"
                        >
                          {{ STATUS_LABEL[statusOf(item)] }}
                        </button>
                        <button class="detail-link" type="button" @click="openUniversity(item)">查看院校详情</button>
                      </div>
                    </section>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </section>

    <RecommendSection />

    <div v-if="!isStrategyAdvicePlan" class="bottom-actions">
      <button class="action-btn action-btn--secondary" @click="goAi">
        AI 深度解读
        <ArrowRight :size="16" />
      </button>
      <button class="action-btn action-btn--secondary" @click="exportDecisionDraft">
        <ClipboardCheck :size="16" />
        导出草稿
      </button>
      <button class="action-btn action-btn--secondary" @click="exportCsv">
        <Download :size="16" />
        导出 CSV
      </button>
      <button class="action-btn action-btn--secondary" @click="copyPlanSummary">
        <ClipboardCheck :size="16" />
        复制摘要
      </button>
      <button class="action-btn action-btn--primary" @click="exportExcel">
        <Download :size="16" />
        导出 Excel
      </button>
    </div>
  </div>
</template>

<style scoped>
.result-page {
  min-height: 100dvh;
  padding-bottom: 96px;
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
.next-step-card,
.mode-switch,
.algorithm-card,
.digest-grid,
.tabs-bar,
.compare-bar,
.plan-list {
  max-width: 1200px;
  margin: 0 auto;
  padding-left: 16px;
  padding-right: 16px;
}

.hero-panel {
  display: grid;
  gap: 16px;
  padding-top: 20px;
}

.next-step-card {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.next-step-card__copy {
  padding: 16px 18px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #ffffff;
}

.next-step-card__kicker {
  display: inline-flex;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0;
}

.next-step-card h2 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.next-step-card ol {
  display: grid;
  gap: 7px;
  margin: 10px 0 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.55;
}

.next-step-card__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 2px;
}

.next-step-card__actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
  font-size: 12px;
  font-weight: 800;
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

.hero-main {
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
}

.metric-card {
  padding: 16px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.metric-label {
  font-size: 12px;
  color: #64748b;
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

.strategy-advice-card {
  max-width: 1200px;
  margin: 14px auto 0;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.05);
}

.strategy-advice-card__head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.strategy-advice-card__badge {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 12px;
  font-weight: 800;
}

.strategy-advice-card h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.25;
  color: #0f172a;
}

.strategy-advice-card p {
  margin-top: 8px;
  color: #475569;
  font-size: 14px;
  line-height: 1.8;
}

.strategy-advice-grid {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.strategy-advice-grid article {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}

.strategy-advice-grid strong {
  color: #0f172a;
  font-size: 14px;
}

.strategy-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.strategy-action {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  background: #fff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.strategy-field-spec {
  margin-top: 14px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
  padding: 12px 14px;
}

.strategy-field-spec summary {
  cursor: pointer;
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
}

.strategy-field-grid {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.strategy-field-grid span {
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.professional-table-panel {
  max-width: 1360px;
  margin: 18px auto 0;
  padding: 0 16px;
}

.professional-table-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 14px 14px 0 0;
  background: #fff;
}

.professional-table-kicker {
  display: inline-flex;
  margin-bottom: 5px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0;
}

.professional-table-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 900;
}

.professional-table-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.professional-table-actions,
.professional-filters,
.professional-tabs,
.professional-detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.professional-export-btn,
.professional-tab,
.table-detail-btn {
  min-height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
  font-size: 12px;
  font-weight: 800;
}

.professional-export-btn {
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.professional-gap-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border-left: 1px solid #e2e8f0;
  border-right: 1px solid #e2e8f0;
  background: #fff7ed;
}

.professional-gap-title,
.professional-gap-chip,
.source-status,
.review-status {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.professional-gap-title {
  background: #fed7aa;
  color: #9a3412;
}

.professional-gap-chip {
  background: #fff;
  border: 1px solid #fed7aa;
  color: #9a3412;
}

.professional-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-left: 1px solid #e2e8f0;
  border-right: 1px solid #e2e8f0;
  background: #f8fafc;
}

.professional-tab {
  padding: 0 11px;
}

.professional-tab.active {
  background: #0f172a;
  border-color: #0f172a;
  color: #fff;
}

.professional-tab span {
  margin-left: 4px;
  opacity: 0.72;
}

.professional-search,
.professional-select {
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
  font-size: 12px;
}

.professional-search {
  width: 220px;
  padding: 0 10px;
}

.professional-select {
  padding: 0 8px;
}

.professional-table-scroll {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 0 0 14px 14px;
  background: #fff;
}

.professional-table {
  width: 100%;
  min-width: 1720px;
  border-collapse: separate;
  border-spacing: 0;
  color: #0f172a;
  font-size: 12px;
}

.professional-table th,
.professional-table td {
  border-bottom: 1px solid #e2e8f0;
  padding: 9px 10px;
  text-align: left;
  vertical-align: top;
}

.professional-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #f8fafc;
  color: #475569;
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.professional-row td {
  background: #fff;
}

.professional-row:hover td {
  background: #f8fbff;
}

.professional-row strong,
.professional-row small,
.table-school-link,
.col-school small {
  display: block;
}

.professional-row small,
.col-school small {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.risk-tier {
  min-width: 30px;
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-weight: 900;
}

.col-school { width: 170px; }
.col-major { width: 220px; }
.col-desc { width: 240px; }

.col-major span,
.col-desc span {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.45;
}

.table-school-link {
  padding: 0;
  border: none;
  background: transparent;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 900;
  text-align: left;
}

.source-status {
  background: #ecfdf5;
  color: #047857;
}

.review-status {
  background: #eff6ff;
  color: #1d4ed8;
}

.review-status.is-needed {
  background: #fff7ed;
  color: #b45309;
}

.table-detail-btn {
  padding: 0 10px;
  color: #2563eb;
  border-color: #bfdbfe;
  background: #eff6ff;
}

.professional-detail-row td {
  padding: 0;
  background: #f8fafc;
}

.professional-detail-grid {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.professional-detail-grid section {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

.professional-detail-grid h3 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 900;
  color: #0f172a;
}

.professional-detail-grid p,
.professional-detail-empty {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
}

.major-detail-table-wrap {
  overflow-x: auto;
}

.major-detail-table {
  width: 100%;
  min-width: 820px;
  border-collapse: collapse;
  font-size: 12px;
}

.major-detail-table th,
.major-detail-table td {
  border: 1px solid #e2e8f0;
  padding: 7px 8px;
  text-align: left;
}

.major-detail-table th {
  background: #f8fafc;
  color: #475569;
}

.history-mini-grid {
  display: grid;
  gap: 6px;
  margin-bottom: 8px;
}

.history-mini-grid span {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 10px;
  background: #f8fafc;
  color: #64748b;
}

.history-mini-grid strong {
  color: #0f172a;
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

.algorithm-metrics {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.algorithm-metrics span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 4px 9px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
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

.plan-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 8px;
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

.bottom-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 12px);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(14px);
  border-top: 1px solid rgba(15, 23, 42, 0.08);
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
    grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
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
    left: auto;
    right: 24px;
    bottom: 24px;
    width: 540px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    border-radius: 18px;
    border: 1px solid rgba(15, 23, 42, 0.08);
    box-shadow: 0 16px 30px rgba(15, 23, 42, 0.12);
  }

  .action-btn--primary {
    grid-column: auto;
  }
}

@media (min-width: 1280px) {
  .hero-panel,
  .mode-switch,
  .algorithm-card,
  .advisor-card,
  .digest-grid,
  .tabs-bar,
  .compare-bar,
  .plan-list {
    max-width: 1360px;
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
</style>
