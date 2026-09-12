<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { fetchVolunteerPlan } from '@/api/volunteer'
import { useVolunteerStore } from '@/stores/volunteer'
import MetricGlossarySheet from '@/components/MetricGlossarySheet.vue'
import RecommendSection from '@/components/RecommendSection.vue'
import SafeExternalLink from '@/components/SafeExternalLink.vue'
import type { GradientRangeDetail, VolunteerItem } from '@/types'
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
  FolderOpen,
  HelpCircle,
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
const compareIds = ref<string[]>([])
const restoring = ref(false)
const showAllReview = ref(false)
const expandedRows = ref<Set<string>>(new Set())
const showMetricGlossary = ref(false)

/** 把位次差翻译成一句白话依据，直接显示在卡片摘要上。 */
function evidenceLine(item: VolunteerItem): string {
  const year = item.referenceYear
  const gap = item.rankGap
  if (typeof gap === 'number' && year) {
    const ratio = typeof item.rankGapRatio === 'number' && Number.isFinite(item.rankGapRatio)
      ? `（约为你位次的 ${Math.abs(Math.round(item.rankGapRatio))}%）`
      : ''
    if (gap > 0) {
      return `依据：${year} 年该志愿最低录取位次比你的位次宽松约 ${gap.toLocaleString()} 位${ratio}，按「${item.gradient}」档纳入。`
    }
    if (gap < 0) {
      return `依据：${year} 年该志愿最低录取位次比你的位次高约 ${Math.abs(gap).toLocaleString()} 位${ratio}，属于「${item.gradient}」档尝试。`
    }
    return `依据：${year} 年该志愿最低录取位次与你的位次基本持平，归入「${item.gradient}」档。`
  }
  return `依据：该志愿历史位次数据有限（置信度 ${confidenceText(item)}），请展开详情并结合官方数据复核。`
}

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
  冲: { color: '#b34040', bg: '#f7efef' },
  稳: { color: '#17181c', bg: '#f4f4f2' },
  保: { color: '#2f7d5d', bg: '#eef2ee' },
  垫: { color: '#a5793a', bg: '#faf7ef' },
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
const planTitle = computed(() => `${planProvinceName.value}${planUnitLabel.value}方案`)
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

const filteredItems = computed(() => {
  if (activeTab.value === 'all') return modeItems.value
  return modeItems.value.filter(item => item.gradient === activeTab.value)
})

/** 渐进渲染：96 条全量 DOM 一次性渲染太重，先渲染一批，滚动到底部哨兵再补。 */
const RENDER_BATCH = 24
const visibleCount = ref(RENDER_BATCH)
const visibleItems = computed(() => filteredItems.value.slice(0, visibleCount.value))
const hasMoreToRender = computed(() => visibleCount.value < filteredItems.value.length)
const listSentinel = ref<HTMLDivElement | null>(null)
let sentinelObserver: IntersectionObserver | null = null

function ensureSentinelObserver() {
  if (sentinelObserver || typeof IntersectionObserver === 'undefined') return
  sentinelObserver = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting) && hasMoreToRender.value) {
      visibleCount.value = Math.min(filteredItems.value.length, visibleCount.value + RENDER_BATCH)
    }
  }, { rootMargin: '600px 0px' })
}

watch(listSentinel, (el, prev) => {
  ensureSentinelObserver()
  if (prev) sentinelObserver?.unobserve(prev)
  if (el) sentinelObserver?.observe(el)
})

watch([activeTab, activeMode], () => {
  expandedRows.value = new Set()
  visibleCount.value = RENDER_BATCH
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
  if (!volunteerStore.planItems.length) {
    showToast('暂无志愿数据，请重新生成方案')
    router.push('/volunteer')
  }
})

onUnmounted(() => {
  sentinelObserver?.disconnect()
  sentinelObserver = null
})

async function restorePlan() {
  const planId = Number(route.query.planId || volunteerStore.getSavedPlanMeta()?.planId)
  const accessKey = String(route.query.accessKey || volunteerStore.getSavedPlanMeta()?.accessKey || '')
  if (!planId || !accessKey) return
  restoring.value = true
  try {
    const res = await fetchVolunteerPlan(planId, accessKey)
    const plan = res.data.data
    const formData = formDataFromPlan(plan)
    volunteerStore.setPlanFromResponse(plan)
    volunteerStore.setFormData(formData)
    activeMode.value = formData.strategyMode
    if (route.query.accessKey) {
      router.replace({ path: route.path, query: { ...route.query, accessKey: undefined } })
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

async function exportExcel() {
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
      ...items.map(item => [
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
        <button class="header-btn" title="我的志愿空间" @click="router.push('/my-plans')">
          <FolderOpen :size="18" />
        </button>
        <button class="header-btn" @click="exportExcel" title="导出 Excel">
          <FileSpreadsheet :size="18" />
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
      <div class="reference-banner reference-banner--data-limit">
        <Info :size="16" />
        <span>
          新高考模式下可比历史数据年份有限，历史位次的年际波动可能偏大；「参考匹配」「机会指数」均为基于有限样本的估算，
          请结合省考试院一分一段表与高校招生章程复核后再做决策。
        </span>
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
      <div class="hero-side">
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

    <section v-if="rangeSummary" class="algorithm-card">
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

    <section v-if="volunteerStore.advisorAdvice" class="advisor-card">
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

    <section class="digest-grid">
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

    <section class="tabs-bar">
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

    <section class="compare-bar" :class="{ 'compare-bar--active': compareIds.length > 0 }">
      <div class="compare-copy">
        <div class="compare-title">院校对比</div>
        <div class="compare-desc">已选 {{ compareIds.length }} 所学校，可快速横向比较平台、风险和适配度</div>
      </div>
      <button class="compare-btn" :disabled="compareIds.length < 2" @click="openCompare">
        <SplitSquareVertical :size="16" />
        去对比
      </button>
    </section>

    <section class="plan-list">
      <article
        v-for="item in visibleItems"
        :key="statusKey(item)"
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
            <button class="quick-help" type="button" title="这些指标是什么意思？" @click.stop="showMetricGlossary = true">
              <HelpCircle :size="12" />
              指标说明
            </button>
          </div>

          <p class="plan-row__evidence">{{ evidenceLine(item) }}</p>

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
              <div class="reason-box">
                <div class="reason-title"><ShieldCheck :size="14" /> 上榜原因</div>
                <p>{{ item.recommendReason || '建议结合学校平台、专业方向和官方章程综合判断。' }}</p>
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

      <div v-if="hasMoreToRender" ref="listSentinel" class="plan-list__sentinel">
        已显示 {{ visibleItems.length }} / {{ filteredItems.length }} 条，继续下滑加载
      </div>
    </section>

    <RecommendSection />

    <div class="bottom-actions">
      <button class="action-btn action-btn--secondary" @click="goAi">
        AI 深度解读
        <ArrowRight :size="16" />
      </button>
      <button class="action-btn action-btn--secondary" @click="exportDecisionDraft">
        <ClipboardCheck :size="16" />
        导出草稿
      </button>
      <button class="action-btn action-btn--primary" @click="exportExcel">
        <Download :size="16" />
        导出 Excel
      </button>
    </div>

    <MetricGlossarySheet v-model:show="showMetricGlossary" />
  </div>
</template>

<style scoped>
.result-page {
  min-height: 100dvh;
  padding-bottom: 96px;
  background: var(--gz-bg);
}

.result-header {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(23, 24, 28, 0.06);
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
  color: #383a40;
}

.header-title {
  flex: 1;
  font-size: 18px;
  font-weight: 800;
  color: #17181c;
}

.hero-panel,
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

.result-warning {
  padding: 12px 14px;
  border: 1px solid #e6dcbd;
  border-radius: 16px;
  background: #faf7ef;
  color: #7c5f33;
  font-size: 13px;
  line-height: 1.6;
}

.rank-estimate-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid #d9d8d3;
  border-radius: 16px;
  background: #f4f4f2;
  color: #17181c;
  font-size: 12px;
  line-height: 1.6;
}

.rank-estimate-banner--auto {
  border-color: #e8dcc5;
  background: #fff7ed;
  color: #9a3412;
}

.hero-main {
  padding: 22px;
  border-radius: 24px;
  background: linear-gradient(145deg, #ffffff, #eef4ff);
  border: 1px solid rgba(23, 24, 28, 0.08);
  box-shadow: 0 18px 40px rgba(23, 24, 28, 0.08);
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
  color: #383a40;
  border: 1px solid rgba(23, 24, 28, 0.08);
}

.hero-title {
  margin-top: 14px;
  font-family: var(--gz-font-display);
  font-size: 28px;
  line-height: 1.25;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: #17181c;
}

.hero-desc {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #4b4d54;
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
  border: 1px solid rgba(23, 24, 28, 0.06);
}

.metric-label {
  font-size: 12px;
  color: #6a6c72;
}

.metric-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 900;
  color: #17181c;
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
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: #fff;
  color: #4b4d54;
  font-size: 14px;
  font-weight: 700;
}

.mode-btn.active {
  background: #17181c;
  color: #fff;
  border-color: #17181c;
}

.algorithm-card {
  padding-top: 12px;
}

.algorithm-card__details {
  border: 1px solid #e7e6e1;
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
  color: #17181c;
  font-size: 14px;
  font-weight: 800;
}

.algorithm-card__details summary::marker {
  color: #17181c;
}

.algorithm-card__details summary strong {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f4f4f2;
  color: #17181c;
  font-size: 12px;
}

.algorithm-card__details p {
  margin-top: 10px;
  color: #4b4d54;
  font-size: 12px;
  line-height: 1.7;
}

.algorithm-card__note {
  padding: 8px 10px;
  border-radius: 12px;
  background: #fafaf8;
  border: 1px solid #e3e2de;
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
  background: #fafaf8;
  border: 1px solid #e3e2de;
  color: #4b4d54;
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
  border: 1px solid #e3e2de;
  background: #fafaf8;
  display: grid;
  gap: 3px;
}

.algorithm-range-item span {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: #f4f4f2;
  color: #17181c;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 900;
}

.algorithm-range-item strong {
  color: #17181c;
  font-size: 13px;
}

.algorithm-range-item small {
  color: #6a6c72;
  font-size: 11px;
  line-height: 1.5;
}

.advisor-card {
  margin-top: 12px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid #e7e6e1;
  background: linear-gradient(145deg, #f8fbff, #ffffff);
  box-shadow: 0 16px 42px rgba(23, 24, 28, 0.08);
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
  color: #17181c;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.advisor-card__head h3 {
  margin: 0;
  color: #17181c;
  font-size: 18px;
  font-weight: 900;
}

.advisor-card__badge {
  flex-shrink: 0;
  padding: 5px 10px;
  border-radius: 999px;
  background: #17181c;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.advisor-card__source-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #17181c;
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
  border: 1px solid #e3e2de;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
}

.advisor-card__grid strong,
.advisor-card__lists h4 {
  color: #17181c;
  font-size: 13px;
  font-weight: 900;
}

.advisor-card__grid p,
.advisor-card__note {
  margin: 6px 0 0;
  color: #4b4d54;
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
  background: #fafaf8;
  border: 1px solid #e3e2de;
}

.advisor-card__lists h4 {
  margin: 0 0 8px;
}

.advisor-card__lists ul,
.advisor-card__lists ol {
  margin: 0;
  padding-left: 18px;
  color: #4b4d54;
  font-size: 12px;
  line-height: 1.75;
}

.advisor-card__note {
  margin-top: 10px;
  color: #6a6c72;
}

.digest-grid {
  display: grid;
  gap: 14px;
  padding-top: 18px;
}

.digest-card {
  border-radius: 22px;
  background: #fff;
  border: 1px solid rgba(23, 24, 28, 0.06);
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
  color: #17181c;
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
  border: 1px solid rgba(23, 24, 28, 0.06);
  background: #fafaf8;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #383a40;
}

.digest-item strong {
  font-size: 14px;
  color: #17181c;
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
  border: 1px solid rgba(23, 24, 28, 0.08);
  background: #fff;
  color: #4b4d54;
  font-size: 13px;
  font-weight: 700;
}

.tab-btn.active {
  background: #f4f4f2;
  color: #17181c;
  border-color: #b9bbc0;
}

.tab-num {
  margin-left: 6px;
  color: #97999e;
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
  background: #f4f4f2;
}

.compare-copy {
  flex: 1;
}

.compare-title {
  font-size: 15px;
  font-weight: 800;
  color: #17181c;
}

.compare-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #6a6c72;
}

.compare-btn {
  padding: 10px 14px;
  border: none;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #17181c;
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
  box-shadow: inset 0 0 0 2px rgba(23, 24, 28, 0.35);
}

.plan-row--expanded .plan-row__summary {
  border-bottom: 1px solid rgba(23, 24, 28, 0.06);
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
  border: 1px solid rgba(23, 24, 28, 0.05);
}

.match-badge {
  color: #17181c;
  background: #f4f4f2;
}

.source-badge {
  color: #4b4d54;
  background: #eef2ee;
}

.confidence--high {
  color: #2f6650;
  background: #eef2ee;
}

.confidence--mid {
  color: #8a6d3b;
  background: #faf7ef;
}

.confidence--low {
  color: #a03535;
  background: #f7efef;
}

.plan-row__school {
  margin-top: 8px;
  font-family: var(--gz-font-display);
  font-size: 18px;
  line-height: 1.3;
  font-weight: 700;
  color: #17181c;
}

.plan-row__major {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 700;
  color: #17181c;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.plan-row__meta {
  margin-top: 8px;
  font-size: 12px;
  color: #6a6c72;
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
  border: 1px solid rgba(23, 24, 28, 0.06);
  border-radius: 12px;
  background: #fafaf8;
}

.plan-row__aside-prob {
  font-size: 22px;
  font-weight: 900;
  color: #17181c;
}

.plan-row__aside-fit {
  font-size: 18px;
  letter-spacing: 0;
}

.plan-row__aside-label {
  font-size: 12px;
  color: #6a6c72;
}

.meta-tag {
  color: #383a40;
  background: #fafaf8;
}

.compare-toggle {
  align-self: flex-start;
  min-height: 30px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(23, 24, 28, 0.15);
  background: #fff;
  color: #17181c;
  font-size: 12px;
  font-weight: 700;
}

.compare-toggle.active {
  background: #f4f4f2;
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
  background: #fafaf8;
  border: 1px solid rgba(23, 24, 28, 0.06);
  display: flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
}

.plan-row__quick em {
  font-style: normal;
  font-size: 11px;
  color: #6a6c72;
}

.plan-row__quick strong {
  min-width: 0;
  font-size: 12px;
  color: #17181c;
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
  border: 1px solid #d9d8d3;
  background: #f4f4f2;
  color: #17181c;
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
  border: 1px solid rgba(23, 24, 28, 0.06);
}

.reason-box--warn {
  background: #fff7ed;
}

.reason-box--info {
  background: #f8fbff;
  border-color: #d9d8d3;
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
  color: #17181c;
}

.reason-box p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: #4b4d54;
}

.history-card {
  margin-top: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e7e6e1;
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
  color: #17181c;
  font-weight: 900;
}

.history-card__head p {
  margin-top: 4px;
  color: #6a6c72;
  font-size: 12px;
  line-height: 1.6;
}

.history-card__badge {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  background: #eef2ee;
  color: #2f6650;
  font-size: 11px;
  font-weight: 800;
}

.history-card__badge--warn {
  background: #fff7ed;
  color: #8a6d3b;
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
  border: 1px solid #e7e6e1;
  color: #383a40;
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
  border: 1px solid #e3e2de;
  border-radius: 14px;
  background: #fff;
}

.history-record__year {
  width: 44px;
  height: 32px;
  border-radius: 10px;
  background: #17181c;
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
  color: #6a6c72;
  font-size: 11px;
}

.history-record strong {
  color: #17181c;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-record__source {
  color: #4b4d54;
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
  color: #2f6650;
  background: #eef2ee;
}

.history-record__rank-source.is-converted {
  color: #17181c;
  background: #f4f4f2;
}

.history-record__rank-source.is-missing {
  color: #8a6d3b;
  background: #fff7ed;
}

.history-record__note {
  grid-column: 2 / -1;
  margin: 0;
  color: #6a6c72;
  font-size: 11px;
  line-height: 1.55;
}

.history-empty {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff7ed;
  color: #7c5f33;
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
  color: #4b4d54;
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
  border: 1px solid rgba(23, 24, 28, 0.15);
  border-radius: 999px;
  background: #fff;
  color: #17181c;
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
  border-top: 1px solid rgba(23, 24, 28, 0.08);
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
  background: var(--gz-brand-gradient);
  color: #fff;
  box-shadow: 0 8px 20px rgba(23, 24, 28, 0.28);
}

.action-btn--secondary {
  background: #f4f4f2;
  color: #17181c;
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
    border: 1px solid rgba(23, 24, 28, 0.08);
    box-shadow: 0 16px 30px rgba(23, 24, 28, 0.12);
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
    border: 1px solid rgba(23, 24, 28, 0.06);
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
  border: 1px solid #e8dcc5;
  color: #8a6d3b;
  font-size: 12px;
  line-height: 1.6;
}

.reference-banner--data-limit {
  background: #f4f4f2;
  border-color: #d9d8d3;
  color: #17181c;
}

.plan-row__evidence {
  margin: 8px 0 0;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--gz-bg-subtle);
  border: 1px dashed rgba(23, 24, 28, 0.2);
  font-size: 12px;
  line-height: 1.6;
  color: var(--gz-ink-soft);
}

.quick-help {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 30px;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px dashed rgba(23, 24, 28, 0.3);
  background: #fff;
  color: var(--gz-ink-soft);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.quick-help:hover,
.quick-help:focus-visible {
  background: var(--gz-bg-subtle);
}

.plan-list__sentinel {
  padding: 18px 0 6px;
  text-align: center;
  color: var(--gz-text-tertiary);
  font-size: 12px;
}

/* 指标术语表样式已随 MetricGlossarySheet 组件迁移 */

.draft-status-bar {
  margin: 16px 0;
  padding: 14px 18px;
  border-radius: 16px;
  border: 1px solid #e3e2de;
  background: linear-gradient(180deg, #ffffff, #fafaf8);
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
  color: #17181c;
  font-size: 14px;
}

.draft-status-bar__hint {
  color: #6a6c72;
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
  background: #eef2ee;
  color: #2f6650;
  border: 1px solid #c2d5c5;
}

.draft-status-pill--review {
  background: #fff7ed;
  color: #8a6d3b;
  border: 1px solid #e8dcc5;
}

.draft-status-pill--drop {
  background: #f7efef;
  color: #a03535;
  border: 1px solid #e3cbcb;
}

.draft-export-btn {
  min-height: 30px;
  padding: 0 12px;
  color: #17181c;
  font-size: 12px;
  font-weight: 700;
  background: #fff;
  border: 1px solid #cdccc7;
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
  color: #8a6d3b;
}

.manual-review-card__desc {
  margin-top: 6px;
  color: #7c5f33;
  font-size: 12px;
  line-height: 1.7;
}

.manual-review-card__count {
  flex-shrink: 0;
  align-self: flex-start;
  font-size: 12px;
  font-weight: 700;
  color: #8a6d3b;
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
  background: #8a6d3b;
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
  color: #8a6d3b;
}

.manual-review-list__reasons {
  margin-top: 6px;
  padding-left: 18px;
  color: #8a6d3b;
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
  color: #7c5f33;
  font-size: 11px;
  text-decoration: none;
}

.manual-review-list__links a:hover {
  background: #e8dcc5;
}

.manual-review-card__toggle {
  margin-top: 10px;
  padding: 6px 12px;
  border-radius: 999px;
  background: transparent;
  border: 1px solid #fcd9b6;
  color: #8a6d3b;
  font-size: 12px;
  cursor: pointer;
}

.evidence-chain {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fafaf8;
  border: 1px dashed #cdccc7;
}

.evidence-chain__head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #4b4d54;
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
  border: 1px solid #e3e2de;
  color: #17181c;
  font-size: 11px;
  text-decoration: none;
}

.evidence-chain__links a:hover {
  background: #f4f4f2;
}

.evidence-chain__flag {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f7efef;
  border: 1px solid #e3cbcb;
  color: #a03535;
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
  border: 1px solid #cdccc7;
  background: #ffffff;
  color: #4b4d54;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.status-toggle:hover {
  background: #fafaf8;
}

.status-toggle--keep {
  background: #eef2ee;
  border-color: #c2d5c5;
  color: #2f6650;
}

.status-toggle--review {
  background: #fff7ed;
  border-color: #e8dcc5;
  color: #8a6d3b;
}

.status-toggle--drop {
  background: #f7efef;
  border-color: #e3cbcb;
  color: #a03535;
}

.plan-row--status-keep {
  border-color: #c2d5c5;
  box-shadow: 0 0 0 1px rgba(47, 125, 93, 0.2);
}

.plan-row--status-review {
  border-color: #e8dcc5;
}

.plan-row--status-drop {
  opacity: 0.55;
}
</style>
