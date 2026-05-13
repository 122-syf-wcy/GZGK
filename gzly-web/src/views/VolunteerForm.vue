<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVolunteerStore } from '@/stores/volunteer'
import { fetchGzBatchSupport, generateVolunteerPlan, rankCheck } from '@/api/volunteer'
import { getHotMajors, type HotMajor } from '@/api/scoreLine'
import DisclaimerDialog from '@/components/DisclaimerDialog.vue'
import type {
  BatchCategory,
  BatchSupportItem,
  BatchSupportResponse,
  GradientRangeKey,
  GradientRanges,
  RecommendationPhase,
  SupportLevel,
} from '@/types'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  type ProvinceCode,
} from '@/constants/provinces'
import {
  DISCLAIMER_CONFIRM_TEXT,
  DISCLAIMER_VERSION,
} from '@/constants/compliance'
import { cloneGradientRanges, presetGradientRanges } from '@/utils/volunteer-plan'
import { closeToast, showLoadingToast, showToast } from 'vant'
import {
  ArrowLeft,
  ArrowRight,
  Atom,
  BookOpen,
  CheckCircle,
  Dna,
  FlaskConical,
  Globe,
  Scale,
  BriefcaseBusiness,
  GraduationCap,
  Landmark,
  Wallet,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const volunteerStore = useVolunteerStore()

const totalScore = ref<number | undefined>(volunteerStore.formData.totalScore || undefined)
const provinceCode = ref<ProvinceCode>(normalizeProvinceCode(route.query.provinceCode))
const provinceRank = ref<number | undefined>(volunteerStore.formData.provinceRank || undefined)
const firstSubject = ref<'物理' | '历史'>(volunteerStore.formData.firstSubject || '物理')
const resubjects = ref<string[]>([...volunteerStore.formData.resubjects])
const preferredMajors = ref<string[]>([...volunteerStore.formData.preferredMajors])
const preferredRegions = ref<string[]>([...volunteerStore.formData.preferredRegions])
const strategyMode = ref<'保守型' | '均衡型' | '冲刺型'>(volunteerStore.formData.strategyMode || '均衡型')
const decisionPriority = ref<'学校优先' | '专业优先'>(volunteerStore.formData.decisionPriority || '专业优先')
const careerGoal = ref<'就业优先' | '升学优先' | '城市机会优先'>(volunteerStore.formData.careerGoal || '就业优先')
const tuitionBudget = ref<'低预算' | '均衡预算' | '不限制'>(volunteerStore.formData.tuitionBudget || '均衡预算')
const acceptPrivate = ref(volunteerStore.formData.acceptPrivate)
const acceptSinoForeign = ref(volunteerStore.formData.acceptSinoForeign)
const gradientRangeMode = ref<'preset' | 'custom'>(volunteerStore.formData.gradientRanges ? 'custom' : 'preset')
const gradientRanges = ref<GradientRanges>(
  volunteerStore.formData.gradientRanges
    ? cloneGradientRanges(volunteerStore.formData.gradientRanges)
    : presetGradientRanges(strategyMode.value),
)
const customMajor = ref('')
const agreedDisclaimer = ref(volunteerStore.formData.agreedDisclaimer)
const disclaimerVersion = ref(volunteerStore.formData.disclaimerVersion || '')
const disclaimerRef = ref<InstanceType<typeof DisclaimerDialog> | null>(null)

const hotMajors = ref<HotMajor[]>([])
const hotMajorsLoading = ref(false)
const rankHint = ref('')
const rankConflict = ref('')
const rankEstLoading = ref(false)
const rankEstimateLow = ref<number | null>(null)
const rankEstimateHigh = ref<number | null>(null)
const rankEstimateYear = ref<number | null>(null)
const generating = ref(false)
const pendingGenerateAfterDisclaimer = ref(false)
let estimateTimer: ReturnType<typeof setTimeout> | null = null

const firstSubjectOptions: Array<{ label: '物理' | '历史'; icon: typeof Atom; iconClass: string }> = [
  { label: '物理', icon: Atom, iconClass: 'subject-icon--physics' },
  { label: '历史', icon: BookOpen as typeof Atom, iconClass: 'subject-icon--history' },
]

const resubjectOptions = [
  { label: '化学', icon: FlaskConical, iconClass: 'subject-icon--chemistry' },
  { label: '生物', icon: Dna, iconClass: 'subject-icon--biology' },
  { label: '政治', icon: Scale, iconClass: 'subject-icon--politics' },
  { label: '地理', icon: Globe, iconClass: 'subject-icon--geography' },
]

const strategyOptions = [
  { key: '保守型', title: '保守型', desc: '更重视风险复核，优先降低高波动志愿占比' },
  { key: '均衡型', title: '均衡型', desc: '冲稳保兼顾，适合大多数考生作为主方案' },
  { key: '冲刺型', title: '冲刺型', desc: '接受更高波动，优先争取平台或热门方向' },
] as const

// 入口省份锁定：贵州专区只通过 /volunteer?provinceCode=GZ 进入；不再展示省份卡片选择器，
// 也不允许 localStorage 旧 provinceCode 覆盖 URL 来源。
const provinceFromUrl = computed<ProvinceCode>(() => normalizeProvinceCode(route.query.provinceCode))
const provinceLabel = computed(() => `${currentProvince.value.name} (${currentProvince.value.code})`)

// === batch-support 接入：直接消费后端 /api/volunteer/gz/batch-support 返回的 18 个真实批次 ===
const batchItems = ref<BatchSupportItem[]>([])
const batchSupportLoading = ref(false)
const batchSupportError = ref('')
const batchSupportPhase = ref<RecommendationPhase | ''>('')
const batchSupportEstimateMode = ref(false)
const batchSupportDataSourceYears = ref<number[]>([])
const batchSupportTargetYear = ref<number | null>(null)
const batchSupportOfficialReady = ref(false)

// 默认普通本科批；用户可以在分组列表里切换到普通专科批，其他批次只读说明、按钮禁用。
const selectedBatchCode = ref<string>(
  volunteerStore.formData.batchCode && volunteerStore.formData.batchCode.length > 0
    ? volunteerStore.formData.batchCode
    : 'NORMAL_UNDERGRADUATE',
)

// 按 candidate category 分组，渲染时与产品规范的【普通类 / 艺术类 / 体育类】一致。
const BATCH_CATEGORY_GROUPS: Array<{ key: BatchCategory[]; title: string; tone: 'ordinary' | 'art' | 'sports' | 'special' }> = [
  { key: ['ORDINARY'], title: '普通类', tone: 'ordinary' },
  { key: ['ART'], title: '艺术类', tone: 'art' },
  { key: ['SPORTS'], title: '体育类', tone: 'sports' },
  { key: ['EARLY', 'SPECIAL_PROGRAM', 'OTHER'], title: '提前批 / 资格类', tone: 'special' },
]
const batchGroups = computed(() => {
  return BATCH_CATEGORY_GROUPS.map(group => ({
    title: group.title,
    tone: group.tone,
    items: batchItems.value.filter(it => group.key.includes((it.category as BatchCategory))),
  })).filter(g => g.items.length > 0)
})
const selectedBatchItem = computed<BatchSupportItem | undefined>(() => (
  batchItems.value.find(it => it.batchCode === selectedBatchCode.value)
))
const selectedSupportLevel = computed<SupportLevel | ''>(() => (
  selectedBatchItem.value?.supportLevel ?? ''
))
const canGenerateCurrentBatch = computed(() => (
  selectedSupportLevel.value === 'ESTIMATE_RECOMMEND'
  || selectedSupportLevel.value === 'TRIAL_RECOMMEND'
  || selectedSupportLevel.value === 'FULL_RECOMMEND'
))
const currentBatchHint = computed(() => {
  const item = selectedBatchItem.value
  if (!item) return ''
  if (item.supportLevel === 'QUERY_ONLY') {
    return item.supportReason || item.supportNote || '当前批次仅支持规则和数据缺口说明，暂不生成志愿方案'
  }
  return item.supportReason || ''
})

async function loadBatchSupport() {
  if (provinceCode.value !== 'GZ') return
  batchSupportLoading.value = true
  batchSupportError.value = ''
  try {
    const res = await fetchGzBatchSupport()
    if (res.data.code !== 0 || !res.data.data) {
      batchSupportError.value = res.data.message || '批次支持数据加载失败，请稍后重试'
      return
    }
    const payload: BatchSupportResponse = res.data.data
    batchItems.value = payload.items || []
    batchSupportPhase.value = payload.recommendationPhase || ''
    batchSupportEstimateMode.value = payload.estimateMode === true
    batchSupportDataSourceYears.value = payload.dataSourceYears || []
    batchSupportTargetYear.value = payload.targetYear ?? null
    batchSupportOfficialReady.value = payload.officialDataReady === true
    const stillExists = batchItems.value.some(it => it.batchCode === selectedBatchCode.value)
    if (!stillExists && batchItems.value.length > 0) {
      const normal = batchItems.value.find(it => it.batchCode === 'NORMAL_UNDERGRADUATE')
      selectedBatchCode.value = (normal || batchItems.value[0]).batchCode
    }
  } catch (err: any) {
    batchSupportError.value = err?.message || '批次支持数据加载失败，请稍后重试'
  } finally {
    batchSupportLoading.value = false
  }
}

function selectBatch(code: string): void {
  if (selectedBatchCode.value !== code) {
    selectedBatchCode.value = code
    volunteerStore.setFormData({ batchCode: code })
  }
}

function supportLevelLabel(level: SupportLevel | string | undefined): string {
  switch (level) {
    case 'ESTIMATE_RECOMMEND': return '可生成预估参考'
    case 'TRIAL_RECOMMEND': return '试推荐'
    case 'FULL_RECOMMEND': return '完整推荐'
    case 'QUERY_ONLY': return '规则/数据缺口说明'
    default: return '暂不支持'
  }
}
function supportLevelClass(level: SupportLevel | string | undefined): string {
  switch (level) {
    case 'ESTIMATE_RECOMMEND': return 'batch-level batch-level--estimate'
    case 'TRIAL_RECOMMEND': return 'batch-level batch-level--trial'
    case 'FULL_RECOMMEND': return 'batch-level batch-level--full'
    case 'QUERY_ONLY': return 'batch-level batch-level--query'
    default: return 'batch-level'
  }
}

const gradientRangeRows: Array<{ key: GradientRangeKey; label: '冲' | '稳' | '保' | '垫'; desc: string }> = [
  { key: 'chong', label: '冲', desc: '尝试更高层次院校或热门方向' },
  { key: 'wen', label: '稳', desc: '围绕当前位次的核心选择' },
  { key: 'bao', label: '保', desc: '位次更宽松的安全垫' },
  { key: 'dian', label: '垫', desc: '进一步兜底的备选，防止整表过于激进' },
]

const decisionOptions = [
  { key: '专业优先', title: '专业优先', icon: BriefcaseBusiness, desc: '优先保证读到更接近目标专业的方向' },
  { key: '学校优先', title: '学校优先', icon: Landmark, desc: '更看重学校平台、城市资源和升学环境' },
] as const

const careerOptions = [
  { key: '就业优先', title: '就业优先', icon: BriefcaseBusiness, desc: '更关注行业落地、技能实用性与就业出口' },
  { key: '升学优先', title: '升学优先', icon: GraduationCap, desc: '更看重学校平台、科研环境和继续深造' },
  { key: '城市机会优先', title: '城市机会优先', icon: Landmark, desc: '优先争取城市实习、见识和生活选择' },
] as const

const budgetOptions = [
  { key: '低预算', title: '低预算', icon: Wallet, desc: '自动规避民办和中外合作，减少高学费风险' },
  { key: '均衡预算', title: '均衡预算', icon: Wallet, desc: '可接受常规收费，但默认规避高成本合作项目' },
  { key: '不限制', title: '不限制', icon: Wallet, desc: '不因学费做强过滤，更注重机会本身' },
] as const

const regionOptions = [
  '北京', '上海', '广东', '浙江', '江苏', '四川',
  '重庆', '湖北', '湖南', '贵州', '云南', '陕西',
]

const subjectTypeLabel = computed(() => (firstSubject.value === '物理' ? '物理类' : '历史类'))
const currentProvince = computed(() => getProvinceConfig(provinceCode.value))
const provinceName = computed(() => currentProvince.value.name)
const targetVolunteerCount = computed(() => currentProvince.value.targetCount)
const volunteerUnitLabel = computed(() => (
  currentProvince.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45' ? '院校专业组' : '志愿'
))
const targetBatchLabel = computed(() => currentProvince.value.targetBatch)
const isGenerateLocked = computed(() => currentProvince.value.status !== 'open')
const hasRankEstimate = computed(() => Boolean(rankEstimateHigh.value && rankEstimateHigh.value > 0))
const effectiveRankForPreview = computed(() => (
  provinceRank.value && provinceRank.value > 0
    ? provinceRank.value
    : rankEstimateHigh.value || 0
))

const hasCurrentDisclaimer = computed(() => (
  agreedDisclaimer.value && disclaimerVersion.value === DISCLAIMER_VERSION
))

const summaryBullets = computed(() => {
  const items = [
    `${strategyMode.value}方案`,
    `${provinceName.value}${targetBatchLabel.value}`,
    `${decisionPriority.value}`,
    `${careerGoal.value}`,
    `${tuitionBudget.value}`,
    acceptPrivate.value ? '可接受民办' : '只看公办',
    acceptSinoForeign.value ? '可接受合作办学' : '默认排除合作办学',
  ]
  if (preferredMajors.value.length) items.push(`专业偏好：${preferredMajors.value.join('、')}`)
  if (preferredRegions.value.length) items.push(`地区偏好：${preferredRegions.value.join('、')}`)
  return items
})

const formReady = computed(() => {
  return (
    totalScore.value !== undefined &&
    totalScore.value > 0 &&
    totalScore.value <= 750 &&
    (provinceRank.value !== undefined && provinceRank.value > 0 || hasRankEstimate.value) &&
    resubjects.value.length === 2 &&
    !rangeValidationMessage.value
  )
})
const canSubmit = computed(() => (
  formReady.value && !isGenerateLocked.value && canGenerateCurrentBatch.value
))

const missingItems = computed(() => {
  const items: string[] = []
  if (totalScore.value === undefined || totalScore.value <= 0) items.push('高考总分')
  if ((provinceRank.value === undefined || provinceRank.value <= 0) && !hasRankEstimate.value) {
    items.push('全省位次或官方估算位次')
  }
  if (resubjects.value.length !== 2) items.push('两门再选科目')
  if (rangeValidationMessage.value) items.push('梯度区间')
  return items
})

const readinessText = computed(() => (
  isGenerateLocked.value
    ? '生成能力暂未开放'
    : missingItems.value.length ? `还差 ${missingItems.value.length} 项必填信息` : '已满足生成条件'
))

const readinessDetail = computed(() => (
  isGenerateLocked.value
    ? currentProvince.value.volunteerLockDescription
    : missingItems.value.length
    ? `请继续补全：${missingItems.value.join('、')}`
    : hasCurrentDisclaimer.value
      ? `可以直接生成 ${targetVolunteerCount.value} 个${volunteerUnitLabel.value}草稿，随后继续做 AI 解读、Excel 导出和人工排序。`
      : `点击生成按钮阅读并确认生成前风险告知后，即可生成 ${targetVolunteerCount.value} 个${volunteerUnitLabel.value}草稿。`
))

const progressRatio = computed(() => {
  let filled = 0
  if (totalScore.value && totalScore.value > 0) filled += 1
  if ((provinceRank.value && provinceRank.value > 0) || hasRankEstimate.value) filled += 1
  if (resubjects.value.length === 2) filled += 1
  if (hasCurrentDisclaimer.value) filled += 1
  return Math.round((filled / 4) * 100)
})

const summaryMetrics = computed(() => [
  {
    label: '高考总分',
    value: totalScore.value ? `${totalScore.value}分` : '待填写',
    note: '750 分满分口径',
  },
  {
    label: '全省位次',
    value: provinceRank.value
      ? provinceRank.value.toLocaleString()
      : hasRankEstimate.value
        ? `估算 ${rankEstimateHigh.value!.toLocaleString()}`
        : '待填写',
    note: provinceRank.value ? subjectTypeLabel.value : hasRankEstimate.value ? `${rankEstimateYear.value || ''}官方保守估位` : subjectTypeLabel.value,
  },
  {
    label: '选科组合',
    value: `${firstSubject.value} + ${resubjects.value.length}/2`,
    note: resubjects.value.length ? resubjects.value.join(' / ') : '还需补足两门再选',
  },
  {
    label: '偏好设置',
    value: `${preferredMajors.value.length + preferredRegions.value.length}`,
    note: `专业 ${preferredMajors.value.length} · 地区 ${preferredRegions.value.length}`,
  },
])

const decisionSummary = computed(() => [
  { label: '方案风格', value: strategyMode.value },
  { label: '优先级', value: decisionPriority.value },
  { label: '长期目标', value: careerGoal.value },
  { label: '预算偏好', value: tuitionBudget.value },
])

const rangePreview = computed(() =>
  gradientRangeRows.map(row => {
    const range = gradientRanges.value[row.key]
    const baseRank = effectiveRankForPreview.value
    const low = baseRank > 0
      ? Math.max(1, baseRank + range.rankOffsetMin)
      : 0
    const high = baseRank > 0
      ? Math.max(low, baseRank + range.rankOffsetMax)
      : 0
    return {
      ...row,
      range,
      text: low && high ? `第${low.toLocaleString()} ~ ${high.toLocaleString()}位` : `${formatOffset(range.rankOffsetMin)} ~ ${formatOffset(range.rankOffsetMax)}`,
    }
  }),
)

const rangeValidationMessage = computed(() => {
  const ranges = gradientRanges.value
  const pairs = gradientRangeRows.map(row => ({ label: row.label, value: ranges[row.key] }))
  for (const pair of pairs) {
    if (!Number.isFinite(pair.value.rankOffsetMin) || !Number.isFinite(pair.value.rankOffsetMax)) {
      return `${pair.label}档区间需要填写数字`
    }
    if (pair.value.rankOffsetMin > pair.value.rankOffsetMax) {
      return `${pair.label}档区间起点不能大于终点`
    }
  }
  if (ranges.chong.rankOffsetMax >= 0) return '冲档必须位于当前位次之前'
  if (ranges.wen.rankOffsetMin > 0 || ranges.wen.rankOffsetMax < 0) return '稳档需要覆盖当前位次附近'
  if (ranges.bao.rankOffsetMin < 0 || ranges.dian.rankOffsetMin < 0) return '保档和垫档不能跨到当前位次之前'
  if (
    ranges.chong.rankOffsetMax > ranges.wen.rankOffsetMin ||
    ranges.wen.rankOffsetMax > ranges.bao.rankOffsetMin ||
    ranges.bao.rankOffsetMax > ranges.dian.rankOffsetMin
  ) {
    return '梯度区间顺序必须保持冲、稳、保、垫'
  }
  return ''
})

const preferenceSummary = computed(() => [
  acceptPrivate.value ? '可接受民办院校' : '默认排除民办院校',
  acceptSinoForeign.value ? '可接受中外合作办学' : '默认排除合作办学',
])

async function loadHotMajors() {
  if (currentProvince.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45') {
    hotMajors.value = []
    return
  }
  hotMajorsLoading.value = true
  try {
    const subjectType = firstSubject.value === '物理' ? '物理类' : '历史类'
    const res = await getHotMajors(subjectType, 10)
    if (res.data.code === 0) {
      hotMajors.value = res.data.data
    }
  } catch {
    hotMajors.value = []
  } finally {
    hotMajorsLoading.value = false
  }
}

watch([firstSubject, provinceCode], loadHotMajors)
onMounted(() => {
  loadHotMajors()
  loadBatchSupport()
})

// URL 是 provinceCode 的唯一权威来源；store 持久化的 formData.provinceCode 不会反向覆盖。
watch(() => route.query.provinceCode, (value) => {
  const next = normalizeProvinceCode(value)
  if (provinceCode.value !== next) {
    provinceCode.value = next
  }
})

watch(provinceCode, (code) => {
  volunteerStore.setFormData({ provinceCode: code })
  // 切换省份时重新拉批次列表（当前贵州独占；其他省份占位）。
  loadBatchSupport()
})

watch(selectedBatchCode, (code) => {
  volunteerStore.setFormData({ batchCode: code })
})

watch(strategyMode, (mode) => {
  if (gradientRangeMode.value === 'preset') {
    gradientRanges.value = presetGradientRanges(mode)
  }
})

watch([totalScore, provinceRank, firstSubject, provinceCode], ([score, rank, subject, province]) => {
  rankHint.value = ''
  rankConflict.value = ''
  rankEstimateLow.value = null
  rankEstimateHigh.value = null
  rankEstimateYear.value = null
  if (estimateTimer) clearTimeout(estimateTimer)
  if (!score || score < 200 || score > 750) return
  estimateTimer = setTimeout(async () => {
    rankEstLoading.value = true
    try {
      const res = await rankCheck({
        provinceCode: province,
        totalScore: score as number,
        provinceRank: rank && rank > 0 ? (rank as number) : undefined,
        firstSubject: subject as '物理' | '历史',
      })
      const data = res.data?.data
      if (!data) return
      if (data.officialDataReady && data.rankLow && data.rankHigh) {
        rankEstimateLow.value = data.rankLow
        rankEstimateHigh.value = data.rankHigh
        rankEstimateYear.value = data.referenceYear || null
        rankHint.value = `${data.provinceName || provinceName.value}${data.referenceYear || ''}官方一分一段：${data.subjectType}该分位次区间约 ${data.rankLow.toLocaleString()} ~ ${data.rankHigh.toLocaleString()}。未手填位次时，系统会采用保守位次 ${data.rankHigh.toLocaleString()} 生成，并在结果页标记为估算。`
        if (data.submittedRank && data.matched === false) {
          rankConflict.value = `你填写的位次 ${data.submittedRank.toLocaleString()} 与官方区间不一致，请优先核对考试院公布的成绩单。`
        }
      } else if (data.note) {
        rankHint.value = data.note
      }
    } catch {
      rankHint.value = ''
    } finally {
      rankEstLoading.value = false
    }
  }, 600)
})

function toggleResubject(subject: string) {
  const idx = resubjects.value.indexOf(subject)
  if (idx > -1) {
    resubjects.value.splice(idx, 1)
  } else if (resubjects.value.length < 2) {
    resubjects.value.push(subject)
  } else {
    showToast('再选科目最多选择2门')
  }
}

function toggleMajor(major: string) {
  const idx = preferredMajors.value.indexOf(major)
  if (idx > -1) {
    preferredMajors.value.splice(idx, 1)
  } else if (preferredMajors.value.length < 5) {
    preferredMajors.value.push(major)
  } else {
    showToast('最多选择5个意向专业方向')
  }
}

function addCustomMajor() {
  const value = customMajor.value.trim()
  if (!value) return
  if (preferredMajors.value.includes(value)) {
    customMajor.value = ''
    return
  }
  if (preferredMajors.value.length >= 5) {
    showToast('最多选择5个意向专业方向')
    return
  }
  preferredMajors.value.push(value)
  customMajor.value = ''
}

function toggleRegion(region: string) {
  const idx = preferredRegions.value.indexOf(region)
  if (idx > -1) {
    preferredRegions.value.splice(idx, 1)
  } else if (preferredRegions.value.length < 5) {
    preferredRegions.value.push(region)
  } else {
    showToast('最多选择5个意向地区')
  }
}

function formatOffset(value: number) {
  if (value > 0) return `+${value.toLocaleString()}`
  return value.toLocaleString()
}

function setRangeMode(mode: 'preset' | 'custom') {
  gradientRangeMode.value = mode
  if (mode === 'preset') {
    gradientRanges.value = presetGradientRanges(strategyMode.value)
  }
}

// 入口省份锁定后不再需要 selectProvince；保留兼容备用（routing 路径如 /region/:code 仍可调用）。
function selectProvince(code: ProvinceCode): void {
  if (provinceCode.value !== code) {
    provinceCode.value = code
  }
  router.replace({
    query: {
      ...route.query,
      provinceCode: code,
    },
  })
}

function updateRange(key: GradientRangeKey, field: keyof GradientRanges[GradientRangeKey], rawValue: string | number) {
  const value = typeof rawValue === 'number' ? rawValue : Number(rawValue)
  gradientRanges.value = {
    ...gradientRanges.value,
    [key]: {
      ...gradientRanges.value[key],
      [field]: Number.isFinite(value) ? value : 0,
    },
  }
  gradientRangeMode.value = 'custom'
}

function openDisclaimer(continueAfterConfirm = false) {
  pendingGenerateAfterDisclaimer.value = continueAfterConfirm
  disclaimerRef.value?.open()
}

function useEstimatedRank() {
  if (!rankEstimateHigh.value) return
  provinceRank.value = rankEstimateHigh.value
}

function handleDisclaimerConfirm(version: string) {
  agreedDisclaimer.value = true
  disclaimerVersion.value = version
  volunteerStore.setFormData({
    agreedDisclaimer: true,
    disclaimerVersion: version,
  })
  if (pendingGenerateAfterDisclaimer.value) {
    pendingGenerateAfterDisclaimer.value = false
    void submitPlan()
  }
}

async function onSubmit() {
  if (isGenerateLocked.value) {
    showToast(currentProvince.value.volunteerLockTitle)
    return
  }
  if (!canSubmit.value || generating.value) return
  if (!hasCurrentDisclaimer.value) {
    openDisclaimer(true)
    return
  }

  await submitPlan()
}

async function submitPlan() {
  if (isGenerateLocked.value) {
    showToast(currentProvince.value.volunteerLockTitle)
    return
  }
  if (!canSubmit.value || !hasCurrentDisclaimer.value || generating.value) return

  generating.value = true
  showLoadingToast({ message: '方案生成中...', forbidClick: true, duration: 0 })
  try {
    const payload = {
      provinceCode: provinceCode.value,
      totalScore: totalScore.value!,
      provinceRank: provinceRank.value && provinceRank.value > 0 ? provinceRank.value : 0,
      firstSubject: firstSubject.value,
      resubjects: [...resubjects.value],
      batchCode: selectedBatchCode.value,
      candidateType: selectedBatchItem.value?.candidateType || '普通类',
      preferredMajors: [...preferredMajors.value],
      preferredRegions: [...preferredRegions.value],
      strategyMode: strategyMode.value,
      decisionPriority: decisionPriority.value,
      careerGoal: careerGoal.value,
      tuitionBudget: tuitionBudget.value,
      acceptPrivate: acceptPrivate.value,
      acceptSinoForeign: acceptSinoForeign.value,
      agreedDisclaimer: true as const,
      disclaimerVersion: disclaimerVersion.value,
      gradientRanges: cloneGradientRanges(gradientRanges.value),
    }
    const genRes = await generateVolunteerPlan(payload)
    if (genRes.data.code !== 0) {
      closeToast()
      showToast(genRes.data.message || '生成失败')
      return
    }

    const plan = genRes.data.data
    volunteerStore.setFormData({
      ...payload,
      provinceRank: plan.provinceRank || payload.provinceRank,
    })
    volunteerStore.setPlanFromResponse(plan)
    closeToast()
    router.push({
      path: '/volunteer/result',
      query: { planId: String(plan.id) },
    })
  } catch (err: any) {
    closeToast()
    showToast(err?.message || '生成失败，请重试')
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <div class="gz-shell-page volunteer-form-page">
    <header class="gz-shell-header">
      <div class="gz-shell-header-inner">
        <button type="button" class="gz-shell-back" aria-label="返回上一页" @click="router.back()">
          <ArrowLeft :size="20" />
        </button>
        <div class="gz-shell-heading">
          <div class="gz-shell-title">{{ provinceName }} AI 志愿预估参考</div>
          <div class="gz-shell-subtitle">
            {{ isGenerateLocked
              ? '查看地区口径、表单字段和数据准备状态'
              : `当前面向 ${batchSupportTargetYear ?? 2026} 届${provinceName}高考，基于 ${batchSupportDataSourceYears.join('/') || '2024/2025'} 历史数据进行预估参考。` }}
          </div>
        </div>
        <div class="gz-shell-header-extra">{{ progressRatio }}%</div>
      </div>
    </header>

    <div class="gz-shell-main volunteer-main-shell">
      <section class="gz-shell-hero volunteer-hero">
        <div class="volunteer-hero__copy">
          <span class="gz-shell-kicker">volunteer plan</span>
          <h1 class="gz-shell-hero-title">主流程只保留真正影响结果的输入项</h1>
          <p class="gz-shell-hero-desc">
            {{ isGenerateLocked ? currentProvince.volunteerLockDescription : `系统会综合分数、位次、选科、偏好、预算与风险取向生成 ${targetVolunteerCount} 个${volunteerUnitLabel}。` }}
          </p>
          <div class="gz-shell-chip-row volunteer-hero__chips">
            <span class="gz-shell-chip is-soft-active">{{ subjectTypeLabel }}</span>
            <span class="gz-shell-chip is-soft-active">{{ provinceName }}</span>
            <span class="gz-shell-chip is-soft-active">{{ strategyMode }}</span>
            <span class="gz-shell-chip is-soft-active">{{ decisionPriority }}</span>
            <span class="gz-shell-chip is-soft-active">{{ careerGoal }}</span>
          </div>
        </div>

        <div class="gz-shell-metrics">
          <div v-for="metric in summaryMetrics" :key="metric.label" class="gz-shell-metric">
            <span class="gz-shell-metric-label">{{ metric.label }}</span>
            <span class="gz-shell-metric-value">{{ metric.value }}</span>
            <span class="gz-shell-metric-note">{{ metric.note }}</span>
          </div>
        </div>
      </section>

      <section v-if="isGenerateLocked" class="gz-shell-panel volunteer-lock-panel">
        <div class="volunteer-lock-panel__badge">数据准备中</div>
        <div>
          <h2>{{ currentProvince.volunteerLockTitle }}</h2>
          <p>{{ currentProvince.volunteerLockDescription }}</p>
          <small>可先查看政策、分数线入口和院校库；后端数据门禁继续保留，前端不会主动发起生成。</small>
        </div>
      </section>

      <div class="volunteer-layout">
        <div class="volunteer-main">
          <section class="gz-shell-panel volunteer-section">
            <div class="volunteer-section__head">
              <div>
                <h2 class="volunteer-section__title">成绩与选科</h2>
                <p class="volunteer-section__desc">先确定省份和 3+1+2 口径，系统后续的位次校验、筛选范围和志愿单位都以这里为准。</p>
              </div>
              <span class="volunteer-section__index">01</span>
            </div>

            <!--
              入口省份锁定：从 /volunteer?provinceCode=GZ 进入即固定贵州专区，
              不再展示 4 张省份卡片选择器，避免误回旧的省份选择流程。
            -->
            <div class="option-group">
              <div class="volunteer-block__label">填报省份</div>
              <div class="province-badge" role="status" aria-live="polite">
                <span class="province-badge__title">{{ provinceLabel }}</span>
                <span v-if="batchSupportEstimateMode" class="province-badge__phase">AI 志愿预估参考 · {{ batchSupportDataSourceYears.join('/') || '2024/2025' }} 历史数据</span>
                <span v-else class="province-badge__phase">{{ currentProvince.statusLabel }}</span>
              </div>
              <div v-if="currentProvince.volunteerUnitType === 'PROFESSIONAL_GROUP_45'" class="volunteer-note">
                {{ currentProvince.shortName }}当前按{{ currentProvince.targetBatch }}院校专业组建模；公开可核验数据不足 45 个时，系统会提示补数据，不会伪装完整方案。
              </div>
            </div>

            <!--
              批次选择：直接消费后端 /api/volunteer/gz/batch-support 返回的 18 个真实批次，
              按【普通类 / 艺术类 / 体育类 / 提前批 资格类】分组。普通本/专科可生成预估参考，其他批次只读说明。
            -->
            <div class="option-group">
              <div class="volunteer-block__label">报考批次</div>
              <div v-if="batchSupportLoading" class="volunteer-note">正在加载批次支持矩阵…</div>
              <div v-else-if="batchSupportError" class="volunteer-note volunteer-note--warn">{{ batchSupportError }}</div>
              <div v-else class="batch-groups">
                <div v-for="group in batchGroups" :key="group.title" class="batch-group" :class="`batch-group--${group.tone}`">
                  <div class="batch-group__title">{{ group.title }}</div>
                  <div class="batch-grid">
                    <button
                      v-for="b in group.items"
                      :key="b.batchCode"
                      type="button"
                      class="batch-card"
                      :class="{ active: selectedBatchCode === b.batchCode, 'batch-card--query': b.supportLevel === 'QUERY_ONLY' }"
                      @click="selectBatch(b.batchCode)"
                    >
                      <span class="batch-card__title">{{ b.batchName }}</span>
                      <span :class="supportLevelClass(b.supportLevel)">{{ supportLevelLabel(b.supportLevel) }}</span>
                      <span class="batch-card__mode">{{ b.recommendMode }}</span>
                    </button>
                  </div>
                </div>
                <p v-if="currentBatchHint" class="batch-hint">{{ currentBatchHint }}</p>
              </div>
            </div>

            <div class="volunteer-block">
              <div class="volunteer-block__label">首选科目（选 1 门）</div>
              <div class="subject-options">
                <button
                  v-for="opt in firstSubjectOptions"
                  :key="opt.label"
                  type="button"
                  class="subject-chip subject-chip--primary"
                  :class="{ active: firstSubject === opt.label }"
                  @click="firstSubject = opt.label"
                >
                  <span class="subject-chip__icon" :class="opt.iconClass" aria-hidden="true">
                    <component :is="opt.icon" class="subject-chip__icon-svg" :size="22" :stroke-width="2.1" />
                  </span>
                  <span>{{ opt.label }}</span>
                </button>
              </div>
            </div>

            <div class="volunteer-block">
              <div class="volunteer-block__label">再选科目（选 2 门）</div>
              <div class="subject-options">
                <button
                  v-for="opt in resubjectOptions"
                  :key="opt.label"
                  type="button"
                  class="subject-chip"
                  :class="{ active: resubjects.includes(opt.label) }"
                  @click="toggleResubject(opt.label)"
                >
                  <span class="subject-chip__icon" :class="opt.iconClass" aria-hidden="true">
                    <component :is="opt.icon" class="subject-chip__icon-svg" :size="20" :stroke-width="2.1" />
                  </span>
                  <span>{{ opt.label }}</span>
                </button>
              </div>
            </div>

            <div class="volunteer-form-grid">
              <div class="field-card">
                <label class="field-card__label">高考总分</label>
                <div class="field-card__input-wrap">
                  <van-field v-model.number="totalScore" type="digit" placeholder="0 - 750" class="custom-field" />
                  <span class="field-card__unit">分</span>
                </div>
              </div>

              <div class="field-card">
                <label class="field-card__label">全省位次（{{ subjectTypeLabel }}）</label>
                <div class="field-card__input-wrap">
                  <van-field v-model.number="provinceRank" type="digit" placeholder="可手填；未填则用官方一分一段估算" class="custom-field" />
                  <span class="field-card__unit">位</span>
                </div>
                <div v-if="rankEstLoading" class="rank-hint rank-hint--loading">{{ subjectTypeLabel }}位次预估中…</div>
                <div v-else-if="rankHint" class="rank-hint">{{ rankHint }}</div>
                <button
                  v-if="hasRankEstimate && (!provinceRank || provinceRank <= 0)"
                  type="button"
                  class="rank-use-btn"
                  @click="useEstimatedRank"
                >
                  使用保守估算位次 {{ rankEstimateHigh?.toLocaleString() }}
                </button>
                <div v-if="rankConflict" class="rank-hint rank-hint--warning">{{ rankConflict }}</div>
              </div>
            </div>
          </section>

          <section class="gz-shell-panel volunteer-section">
            <div class="volunteer-section__head">
              <div>
                <h2 class="volunteer-section__title">填报策略</h2>
                <p class="volunteer-section__desc">这部分决定系统更偏向冲学校、稳专业，还是优先考虑就业、升学和城市机会。</p>
              </div>
              <span class="volunteer-section__index">02</span>
            </div>

            <div class="option-group">
              <div class="volunteer-block__label">方案风格</div>
              <div class="option-grid option-grid--triple">
                <button
                  v-for="opt in strategyOptions"
                  :key="opt.key"
                  type="button"
                  class="option-card"
                  :class="{ active: strategyMode === opt.key }"
                  @click="strategyMode = opt.key"
                >
                  <span class="option-card__title">{{ opt.title }}</span>
                  <span class="option-card__desc">{{ opt.desc }}</span>
                </button>
              </div>
            </div>

            <div class="option-group gradient-range-panel">
              <div class="gradient-range-panel__head">
                <div>
                  <div class="volunteer-block__label">冲稳保垫位次区间</div>
                  <p class="gradient-range-panel__desc">以你手填的官方全省位次为基准，系统只在这些区间内取候选。</p>
                </div>
                <div class="range-mode-switch">
                  <button type="button" :class="{ active: gradientRangeMode === 'preset' }" @click="setRangeMode('preset')">跟随风格</button>
                  <button type="button" :class="{ active: gradientRangeMode === 'custom' }" @click="setRangeMode('custom')">自定义</button>
                </div>
              </div>

              <div class="range-preview-grid">
                <div v-for="row in rangePreview" :key="row.key" class="range-preview-card">
                  <span class="range-preview-card__label">{{ row.label }}</span>
                  <strong>{{ row.text }}</strong>
                  <small>{{ row.desc }}</small>
                </div>
              </div>

              <div v-if="gradientRangeMode === 'custom'" class="range-edit-grid">
                <div v-for="row in gradientRangeRows" :key="`edit-${row.key}`" class="range-edit-row">
                  <div class="range-edit-row__label">
                    <strong>{{ row.label }}档</strong>
                    <span>{{ row.desc }}</span>
                  </div>
                  <div class="range-edit-row__fields">
                    <van-field
                      :model-value="gradientRanges[row.key].rankOffsetMin"
                      type="number"
                      inputmode="numeric"
                      class="custom-field custom-field--compact range-field"
                      @update:model-value="value => updateRange(row.key, 'rankOffsetMin', value)"
                    />
                    <span>至</span>
                    <van-field
                      :model-value="gradientRanges[row.key].rankOffsetMax"
                      type="number"
                      inputmode="numeric"
                      class="custom-field custom-field--compact range-field"
                      @update:model-value="value => updateRange(row.key, 'rankOffsetMax', value)"
                    />
                  </div>
                </div>
              </div>

              <div v-if="rangeValidationMessage" class="range-error">{{ rangeValidationMessage }}</div>
            </div>

            <div class="option-group">
              <div class="volunteer-block__label">你希望系统优先照顾什么</div>
              <div class="option-grid option-grid--double">
                <button
                  v-for="opt in decisionOptions"
                  :key="opt.key"
                  type="button"
                  class="option-card option-card--icon"
                  :class="{ active: decisionPriority === opt.key }"
                  @click="decisionPriority = opt.key"
                >
                  <component :is="opt.icon" :size="18" />
                  <span class="option-card__title">{{ opt.title }}</span>
                  <span class="option-card__desc">{{ opt.desc }}</span>
                </button>
              </div>
            </div>

            <div class="option-group">
              <div class="volunteer-block__label">长期目标</div>
              <div class="option-grid option-grid--triple">
                <button
                  v-for="opt in careerOptions"
                  :key="opt.key"
                  type="button"
                  class="option-card option-card--icon"
                  :class="{ active: careerGoal === opt.key }"
                  @click="careerGoal = opt.key"
                >
                  <component :is="opt.icon" :size="18" />
                  <span class="option-card__title">{{ opt.title }}</span>
                  <span class="option-card__desc">{{ opt.desc }}</span>
                </button>
              </div>
            </div>
          </section>

          <section class="gz-shell-panel volunteer-section">
            <div class="volunteer-section__head">
              <div>
                <h2 class="volunteer-section__title">约束条件</h2>
                <p class="volunteer-section__desc">预算与录取接受度会参与筛选，不再只是展示文案。</p>
              </div>
              <span class="volunteer-section__index">03</span>
            </div>

            <div class="option-group">
              <div class="volunteer-block__label">预算偏好</div>
              <div class="option-grid option-grid--triple">
                <button
                  v-for="opt in budgetOptions"
                  :key="opt.key"
                  type="button"
                  class="option-card option-card--icon"
                  :class="{ active: tuitionBudget === opt.key }"
                  @click="tuitionBudget = opt.key"
                >
                  <component :is="opt.icon" :size="18" />
                  <span class="option-card__title">{{ opt.title }}</span>
                  <span class="option-card__desc">{{ opt.desc }}</span>
                </button>
              </div>
            </div>

            <div class="toggle-list">
              <div class="toggle-item">
                <div>
                  <div class="toggle-item__title">接受民办院校</div>
                  <div class="toggle-item__desc">关闭后只推荐公办院校</div>
                </div>
                <van-switch v-model="acceptPrivate" size="22px" />
              </div>
              <div class="toggle-item">
                <div>
                  <div class="toggle-item__title">接受中外合作 / 港澳台合作</div>
                  <div class="toggle-item__desc">关闭后尽量规避高成本合作办学项目</div>
                </div>
                <van-switch v-model="acceptSinoForeign" size="22px" />
              </div>
            </div>

            <div class="volunteer-note">
              系统会把这些约束用于候选学校过滤、排序和提醒提示，所以这里比“备注”更重要。
            </div>
          </section>

          <section class="gz-shell-panel volunteer-section">
            <div class="volunteer-section__head">
              <div>
                <h2 class="volunteer-section__title">意向方向</h2>
                <p class="volunteer-section__desc">把专业与地区偏好一次设置好，避免生成结果再回头大幅筛掉。</p>
              </div>
              <span class="volunteer-section__index">04</span>
            </div>

            <div class="interest-layout">
              <div class="interest-block">
                <div class="interest-block__head">
                  <div class="interest-block__title">意向专业（最多 5 个）</div>
                <div class="interest-block__desc">
                  {{ currentProvince.volunteerUnitType === 'PROFESSIONAL_GROUP_45' ? `${currentProvince.shortName}专业组数据导入后会展示本省口径热门方向。` : `热门专业基于${subjectTypeLabel} 2024 年录取数据统计。` }}
                </div>
                </div>

                <div v-if="hotMajorsLoading" class="inline-loading">热门专业加载中…</div>
                <div v-else-if="currentProvince.volunteerUnitType === 'PROFESSIONAL_GROUP_45'" class="inline-loading">{{ currentProvince.shortName }}按院校专业组生成，可先手动输入专业关键词。</div>
                <div v-else-if="hotMajors.length" class="hot-major-grid">
                  <button
                    v-for="(major, idx) in hotMajors"
                    :key="major.name"
                    type="button"
                    class="hot-major-card"
                    :class="{ active: preferredMajors.includes(major.name) }"
                    @click="toggleMajor(major.name)"
                  >
                    <div class="hot-major-card__rank" :class="{ 'is-top3': idx < 3 }">{{ idx + 1 }}</div>
                    <div class="hot-major-card__body">
                      <div class="hot-major-card__name">{{ major.name }}</div>
                      <div class="hot-major-card__meta">{{ major.schoolCount }} 所院校 · 均分 {{ major.avgScore }}</div>
                    </div>
                    <div class="hot-major-card__heat">
                      <div class="hot-major-card__heat-bar" :style="{ width: `${major.heat}%` }"></div>
                      <span class="hot-major-card__heat-label">热度 {{ major.heat }}%</span>
                    </div>
                  </button>
                </div>

                <div class="custom-input-row">
                  <van-field
                    v-model="customMajor"
                    placeholder="输入其他专业关键词"
                    class="custom-field custom-field--compact"
                    @keyup.enter="addCustomMajor"
                  />
                  <button type="button" class="add-btn" :disabled="!customMajor.trim()" @click="addCustomMajor">添加</button>
                </div>

                <div v-if="preferredMajors.length" class="selected-tags">
                  <button
                    v-for="major in preferredMajors"
                    :key="major"
                    type="button"
                    class="tag-item tag-item--major"
                    @click="toggleMajor(major)"
                  >
                    {{ major }} ×
                  </button>
                </div>
              </div>

              <div class="interest-block">
                <div class="interest-block__head">
                  <div class="interest-block__title">意向地区（最多 5 个）</div>
                  <div class="interest-block__desc">如果你更看重城市资源、实习机会或离家距离，可以先把地区偏好定清楚。</div>
                </div>

                <div class="region-grid">
                  <button
                    v-for="region in regionOptions"
                    :key="region"
                    type="button"
                    class="region-chip"
                    :class="{ active: preferredRegions.includes(region) }"
                    @click="toggleRegion(region)"
                  >
                    {{ region }}
                  </button>
                </div>

                <div v-if="preferredRegions.length" class="selected-tags">
                  <button
                    v-for="region in preferredRegions"
                    :key="region"
                    type="button"
                    class="tag-item tag-item--region"
                    @click="toggleRegion(region)"
                  >
                    {{ region }} ×
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <aside class="volunteer-sidebar">
          <section class="gz-shell-panel volunteer-summary-card">
            <div class="volunteer-state" :class="{ 'is-ready': !missingItems.length && !isGenerateLocked }">
              <div class="volunteer-state__label">当前状态</div>
              <div class="volunteer-state__value">{{ readinessText }}</div>
              <p class="volunteer-state__desc">{{ readinessDetail }}</p>
            </div>

            <div class="sidebar-summary-list">
              <div v-for="item in decisionSummary" :key="item.label" class="sidebar-summary-item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section class="gz-shell-panel volunteer-summary-card volunteer-summary-card--muted">
            <div class="gz-shell-panel-head">
              <div class="gz-shell-panel-title">系统将按这些偏好执行</div>
              <div class="gz-shell-panel-desc">这些标签会同时参与候选学校过滤、排序和结果解释。</div>
            </div>

            <div class="summary-tags">
              <span v-for="item in summaryBullets" :key="item" class="summary-tag">{{ item }}</span>
            </div>

            <div class="preference-list">
              <span v-for="item in preferenceSummary" :key="item" class="preference-badge">{{ item }}</span>
            </div>
          </section>
        </aside>
      </div>

      <section class="gz-shell-panel volunteer-submit-card">
        <div class="gz-shell-panel-head">
          <div class="gz-shell-panel-title">提交前确认</div>
          <div class="gz-shell-panel-desc">确认风险提示后生成志愿方案，生成完成后可继续进行 AI 解读、结果复核和 Excel 导出。</div>
        </div>

        <button type="button" class="agreement-box" :class="{ confirmed: hasCurrentDisclaimer }" @click="openDisclaimer()">
          <span class="agreement-check" :class="{ confirmed: hasCurrentDisclaimer }">
            <CheckCircle v-if="hasCurrentDisclaimer" :size="15" />
          </span>
          <span class="disclaimer-text">
            <strong>{{ hasCurrentDisclaimer ? '已确认生成前风险告知' : '生成前需阅读风险告知' }}</strong>
            <small>{{ hasCurrentDisclaimer ? DISCLAIMER_CONFIRM_TEXT : `点击阅读并确认版本 ${DISCLAIMER_VERSION}` }}</small>
          </span>
        </button>

        <button class="submit-btn" :class="{ disabled: !canSubmit || generating }" :disabled="!canSubmit || generating" @click="onSubmit">
          <span>
            {{
              isGenerateLocked
                ? '生成能力暂未开放'
                : !canGenerateCurrentBatch
                  ? '当前批次仅支持规则和数据缺口说明，暂不生成志愿方案'
                  : generating
                    ? '生成中…'
                    : hasCurrentDisclaimer
                      ? '生成 AI 志愿预估参考'
                      : '阅读风险告知并生成 AI 志愿预估参考'
            }}
          </span>
          <ArrowRight :size="18" />
        </button>
        <p class="submit-hint">
          {{ isGenerateLocked ? currentProvince.volunteerLockDescription : '系统会综合专业数据、院校门槛、位次波动和你的偏好给出可解释结果。' }}
        </p>
      </section>
    </div>

    <DisclaimerDialog ref="disclaimerRef" v-model="agreedDisclaimer" @confirm="handleDisclaimerConfirm" />
  </div>
</template>

<style scoped>
.volunteer-form-page :deep(.van-switch) {
  --van-switch-on-background: #0f172a;
}

.volunteer-form-page :deep(.van-checkbox) {
  --van-checkbox-checked-icon-color: #0f172a;
}

.volunteer-main-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.volunteer-lock-panel {
  display: grid;
  gap: 12px;
  padding: 20px;
  border-color: rgba(180, 83, 9, 0.2);
  background: linear-gradient(180deg, rgba(255, 253, 250, 0.98) 0%, rgba(255, 251, 235, 0.94) 100%);
}

.volunteer-lock-panel__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  font-weight: 800;
}

.volunteer-lock-panel h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
  color: #0f172a;
}

.volunteer-lock-panel p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.volunteer-lock-panel small {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #92400e;
}

.volunteer-layout {
  display: grid;
  gap: 18px;
}

.volunteer-main,
.volunteer-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.volunteer-section,
.volunteer-summary-card,
.volunteer-submit-card {
  padding: 22px;
}

.volunteer-summary-card--muted {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.96) 100%);
}

.volunteer-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.volunteer-section__title {
  font-size: 20px;
  line-height: 1.2;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.volunteer-section__desc {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.75;
  color: #64748b;
}

.volunteer-section__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  height: 40px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.volunteer-block + .volunteer-block,
.option-group + .option-group {
  margin-top: 18px;
}

.volunteer-block__label {
  font-size: 14px;
  line-height: 1.5;
  font-weight: 700;
  color: #334155;
}

.subject-options,
.region-grid,
.selected-tags,
.summary-tags,
.preference-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.subject-options,
.region-grid,
.selected-tags,
.summary-tags,
.preference-list,
.volunteer-form-grid,
.hot-major-grid,
.custom-input-row,
.toggle-list,
.sidebar-summary-list {
  margin-top: 12px;
}

.subject-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.subject-chip:hover,
.option-card:hover,
.hot-major-card:hover,
.region-chip:hover,
.tag-item:hover {
  transform: translateY(-1px);
}

.subject-chip.active {
  border-color: rgba(37, 99, 235, 0.22);
  background: #eff6ff;
  color: #1d4ed8;
}

.subject-chip--primary {
  padding-right: 22px;
}

.subject-chip__icon {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.subject-chip__icon-svg {
  color: #1d4ed8;
}

.subject-icon--physics {
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
}

.subject-icon--history {
  background: linear-gradient(135deg, #fef3c7, #fff7ed);
}

.subject-icon--history .subject-chip__icon-svg {
  color: #b45309;
}

.subject-icon--chemistry {
  background: linear-gradient(135deg, #dcfce7, #f0fdf4);
}

.subject-icon--chemistry .subject-chip__icon-svg,
.subject-icon--biology .subject-chip__icon-svg {
  color: #0f766e;
}

.subject-icon--biology {
  background: linear-gradient(135deg, #ccfbf1, #f0fdfa);
}

.subject-icon--politics {
  background: linear-gradient(135deg, #fee2e2, #fff1f2);
}

.subject-icon--politics .subject-chip__icon-svg {
  color: #be123c;
}

.subject-icon--geography {
  background: linear-gradient(135deg, #dbeafe, #ecfeff);
}

.subject-icon--geography .subject-chip__icon-svg {
  color: #0369a1;
}

.volunteer-form-grid {
  display: grid;
  gap: 12px;
}

.field-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.field-card__label {
  display: block;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 700;
  color: #475569;
}

.field-card__input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.field-card__unit {
  flex-shrink: 0;
  font-size: 14px;
  color: #64748b;
}

.custom-field {
  flex: 1;
}

.custom-field :deep(.van-cell) {
  padding: 0;
  background: transparent;
}

.custom-field :deep(.van-cell::after) {
  display: none;
}

.custom-field :deep(.van-field__body) {
  min-height: 48px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.custom-field--compact :deep(.van-field__body) {
  min-height: 46px;
}

.rank-hint {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.6;
}

.rank-hint--warning {
  margin-top: 8px;
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  font-weight: 600;
}

.rank-hint--loading {
  border-color: #e5e7eb;
  background: #f8fafc;
  color: #94a3b8;
}

.rank-use-btn {
  margin-top: 8px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid #bfdbfe;
  background: #ffffff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.option-grid {
  display: grid;
  gap: 12px;
}

.option-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
  padding: 16px;
  text-align: left;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #fff;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.option-card--icon {
  padding-left: 48px;
}

.option-card--icon :deep(svg) {
  position: absolute;
  top: 16px;
  left: 16px;
  color: #2563eb;
}

.option-card.active {
  border-color: rgba(37, 99, 235, 0.24);
  background: #eff6ff;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
}

.option-card__title {
  font-size: 14px;
  line-height: 1.4;
  font-weight: 700;
  color: #0f172a;
}

.option-card__desc {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.gradient-range-panel {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.gradient-range-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.gradient-range-panel__desc {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.range-mode-switch {
  flex-shrink: 0;
  padding: 3px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #fff;
  display: inline-flex;
  gap: 4px;
}

.range-mode-switch button {
  min-height: 30px;
  padding: 0 10px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.range-mode-switch button.active {
  background: #0f172a;
  color: #fff;
}

.range-preview-grid {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.range-preview-card {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
  display: grid;
  gap: 3px;
}

.range-preview-card__label {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 800;
}

.range-preview-card strong {
  font-size: 13px;
  color: #0f172a;
}

.range-preview-card small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.5;
}

.range-edit-grid {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.range-edit-row {
  padding: 10px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  display: grid;
  gap: 8px;
}

.range-edit-row__label {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.range-edit-row__label strong {
  font-size: 13px;
  color: #0f172a;
}

.range-edit-row__label span {
  font-size: 11px;
  line-height: 1.5;
  color: #64748b;
}

.range-edit-row__fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.range-field {
  min-width: 0;
}

.range-error {
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.6;
  font-weight: 700;
}

.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toggle-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.toggle-item__title {
  font-size: 14px;
  line-height: 1.5;
  font-weight: 700;
  color: #0f172a;
}

.toggle-item__desc,
.volunteer-note,
.interest-block__desc,
.submit-hint,
.disclaimer-text {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.province-badge {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  margin-top: 4px;
}
.province-badge__title {
  font-size: 14px;
  font-weight: 700;
  color: #92400e;
}
.province-badge__phase {
  font-size: 12px;
  color: #b45309;
}

.batch-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 6px;
}
.batch-group {
  padding: 12px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
}
.batch-group--ordinary { border-color: #bfdbfe; background: #eff6ff; }
.batch-group--art      { border-color: #fbcfe8; background: #fdf2f8; }
.batch-group--sports   { border-color: #bbf7d0; background: #f0fdf4; }
.batch-group--special  { border-color: #fde68a; background: #fffbeb; }
.batch-group__title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
}
.batch-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}
.batch-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.batch-card:hover { border-color: #2563eb; }
.batch-card.active {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}
.batch-card--query {
  background: #f9fafb;
  color: #6b7280;
  cursor: pointer;
}
.batch-card--query.active {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.18);
}
.batch-card__title { font-size: 13px; font-weight: 600; line-height: 1.3; color: inherit; }
.batch-card__mode { font-size: 11px; color: #94a3b8; }
.batch-level {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.batch-level--estimate { background: #f59e0b; color: #fff; }
.batch-level--trial    { background: #2563eb; color: #fff; }
.batch-level--full     { background: #16a34a; color: #fff; }
.batch-level--query    { background: #e2e8f0; color: #475569; }
.batch-hint {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.55;
}
.volunteer-note--warn {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.volunteer-note {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
}

.interest-layout {
  display: grid;
  gap: 18px;
}

.interest-block {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.interest-block__title {
  font-size: 16px;
  line-height: 1.3;
  font-weight: 700;
  color: #0f172a;
}

.hot-major-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hot-major-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 112px;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.hot-major-card.active {
  border-color: rgba(37, 99, 235, 0.24);
  background: #eff6ff;
}

.hot-major-card__rank {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 16px;
  font-weight: 800;
}

.hot-major-card__rank.is-top3 {
  background: #0f172a;
  color: #fff;
}

.hot-major-card__body {
  flex: 1;
  min-width: 0;
}

.hot-major-card__name {
  font-size: 15px;
  line-height: 1.4;
  font-weight: 700;
  color: #0f172a;
  word-break: keep-all;
}

.hot-major-card__meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  word-break: keep-all;
}

.hot-major-card__heat {
  position: relative;
  width: 112px;
  height: 32px;
  justify-self: end;
  overflow: hidden;
  border-radius: 12px;
  background: #e2e8f0;
}

.hot-major-card__heat-bar {
  position: absolute;
  inset: 0 auto 0 0;
  background: linear-gradient(90deg, #c7d2fe, #93c5fd);
}

.hot-major-card__heat-label {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 11px;
  font-weight: 700;
  color: #1e293b;
}

.inline-loading {
  margin-top: 12px;
  font-size: 13px;
  color: #64748b;
}

.custom-input-row {
  display: flex;
  gap: 10px;
}

.add-btn,
.region-chip,
.tag-item,
.summary-tag,
.preference-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.add-btn {
  flex-shrink: 0;
  min-width: 72px;
  color: #0f172a;
}

.add-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.region-chip.active {
  border-color: rgba(37, 99, 235, 0.24);
  background: #eff6ff;
  color: #1d4ed8;
}

.tag-item--major {
  background: #eff6ff;
  border-color: rgba(37, 99, 235, 0.16);
  color: #1d4ed8;
}

.tag-item--region {
  background: #ecfdf5;
  border-color: rgba(5, 150, 105, 0.16);
  color: #047857;
}

.volunteer-state {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(251, 191, 36, 0.2);
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.96) 0%, rgba(255, 247, 237, 0.92) 100%);
}

.volunteer-state.is-ready {
  border-color: rgba(16, 185, 129, 0.16);
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.96) 0%, rgba(240, 253, 250, 0.92) 100%);
}

.volunteer-state__label {
  font-size: 12px;
  line-height: 1.4;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #92400e;
}

.volunteer-state.is-ready .volunteer-state__label {
  color: #047857;
}

.volunteer-state__value {
  margin-top: 10px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.volunteer-state__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

.sidebar-summary-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.sidebar-summary-item strong {
  color: #0f172a;
  font-size: 14px;
}

.summary-tags,
.preference-list,
.sidebar-summary-list {
  margin-top: 14px;
}

.summary-tag,
.preference-badge {
  min-height: 38px;
  border-radius: 999px;
  background: #fff;
}

.preference-badge {
  background: #f8fafc;
}

.agreement-box {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.agreement-box.confirmed {
  border-color: rgba(22, 101, 52, 0.25);
  background: #f0fdf4;
}

.agreement-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 7px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #166534;
}

.agreement-check.confirmed {
  border-color: #86efac;
  background: #dcfce7;
}

.disclaimer-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.disclaimer-text strong {
  font-size: 14px;
  line-height: 1.35;
  color: #0f172a;
}

.disclaimer-text small {
  font-size: 12px;
  line-height: 1.45;
  color: #64748b;
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  margin-top: 16px;
  min-height: 52px;
  padding: 0 18px;
  border: none;
  border-radius: 18px;
  background: #0f172a;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  transition: transform 0.18s ease, opacity 0.18s ease, background 0.18s ease;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  background: #111827;
}

.submit-btn.disabled {
  opacity: 0.45;
}

.submit-hint {
  margin-top: 10px;
  text-align: center;
}

@media (min-width: 768px) {
  .volunteer-form-grid,
  .option-grid--double {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .interest-layout {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1024px) {
  .volunteer-layout {
    grid-template-columns: minmax(0, 1.55fr) minmax(320px, 380px);
    align-items: start;
  }

  .volunteer-hero {
    grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.65fr);
    align-items: end;
  }

  .interest-layout {
    grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
    align-items: start;
  }

  .volunteer-sidebar {
    position: sticky;
    top: 88px;
  }

  .option-grid--triple {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .range-preview-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .range-edit-row {
    grid-template-columns: minmax(160px, 0.8fr) minmax(0, 1.2fr);
    align-items: center;
  }
}

@media (max-width: 767px) {
  .volunteer-section__head {
    flex-direction: column;
  }

  .gradient-range-panel__head {
    flex-direction: column;
  }

  .volunteer-section__index {
    align-self: flex-start;
  }
}

@media (max-width: 540px) {
  .hot-major-card {
    grid-template-columns: 42px minmax(0, 1fr);
    align-items: start;
  }

  .hot-major-card__heat {
    grid-column: 2;
    justify-self: start;
    width: 128px;
    margin-top: 2px;
  }
}

/* Desktop cleanup: use the available width instead of stretching a mobile form. */
.volunteer-main-shell {
  max-width: 1320px;
  gap: 14px;
}

.volunteer-hero {
  padding: 18px;
  border-radius: 16px;
}

.volunteer-hero :deep(.gz-shell-hero-title) {
  margin-top: 8px;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.12;
}

.volunteer-hero :deep(.gz-shell-hero-desc) {
  margin-top: 8px;
  line-height: 1.55;
}

.volunteer-section,
.volunteer-summary-card,
.volunteer-submit-card {
  padding: 16px;
  border-radius: 16px;
}

.volunteer-main,
.volunteer-sidebar {
  gap: 14px;
}

.volunteer-section__head {
  gap: 12px;
  margin-bottom: 14px;
}

.volunteer-section__title {
  font-size: 18px;
}

.volunteer-section__desc {
  display: none;
}

.volunteer-section__index {
  min-width: 34px;
  height: 34px;
  font-size: 12px;
}

.volunteer-block + .volunteer-block,
.option-group + .option-group {
  margin-top: 14px;
}

.subject-options,
.region-grid,
.selected-tags,
.summary-tags,
.preference-list {
  gap: 8px;
}

.subject-options,
.region-grid,
.selected-tags,
.summary-tags,
.preference-list,
.volunteer-form-grid,
.hot-major-grid,
.custom-input-row,
.toggle-list,
.sidebar-summary-list {
  margin-top: 10px;
}

.subject-chip {
  min-height: 42px;
  padding: 0 12px;
  border-radius: 12px;
}

.subject-chip__icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
}

.field-card {
  padding: 12px;
  border-radius: 14px;
}

.field-card__input-wrap {
  margin-top: 8px;
}

.custom-field :deep(.van-field__body) {
  min-height: 42px;
  border-radius: 12px;
}

.option-grid {
  gap: 10px;
}

.option-card {
  min-height: 88px;
  padding: 12px;
  border-radius: 14px;
  gap: 6px;
}

.option-card--icon {
  padding-left: 42px;
}

.option-card--icon :deep(svg) {
  top: 13px;
  left: 13px;
}

.option-card__desc {
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.toggle-item,
.volunteer-note,
.interest-block,
.hot-major-card,
.agreement-box,
.sidebar-summary-item {
  border-radius: 14px;
}

.toggle-item {
  padding: 12px;
}

.volunteer-note {
  margin-top: 10px;
  padding: 10px 12px;
}

.interest-layout {
  gap: 14px;
}

.interest-block {
  padding: 14px;
}

.hot-major-grid {
  gap: 8px;
}

.hot-major-card {
  grid-template-columns: 34px minmax(0, 1fr) 96px;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
}

.hot-major-card__rank {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  font-size: 14px;
}

.hot-major-card__name {
  font-size: 14px;
}

.hot-major-card__heat {
  width: 96px;
  height: 28px;
}

.add-btn,
.region-chip,
.tag-item,
.summary-tag,
.preference-badge {
  min-height: 36px;
  padding: 0 12px;
  border-radius: 12px;
}

.volunteer-state {
  padding: 14px;
  border-radius: 14px;
}

.volunteer-state__value {
  margin-top: 6px;
  font-size: 18px;
}

.submit-btn {
  min-height: 46px;
  margin-top: 12px;
  border-radius: 14px;
}

@media (min-width: 1024px) {
  .volunteer-layout {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .volunteer-sidebar {
    order: -1;
    position: static;
    display: grid;
    grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.25fr);
    align-items: stretch;
    gap: 12px;
  }

  .volunteer-main {
    display: grid;
    grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.1fr);
    align-items: start;
    gap: 14px;
  }

  .volunteer-main > .volunteer-section:nth-child(4) {
    grid-column: 1 / -1;
  }

  .interest-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
  }

  .volunteer-summary-card {
    min-height: 100%;
  }

  .volunteer-submit-card {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(300px, 0.78fr) 220px;
    align-items: center;
    gap: 12px;
  }

  .volunteer-submit-card .gz-shell-panel-head,
  .volunteer-submit-card .agreement-box,
  .volunteer-submit-card .submit-btn {
    margin-top: 0;
  }

  .volunteer-submit-card .submit-hint {
    grid-column: 2 / 4;
    margin-top: -4px;
    text-align: left;
  }
}

@media (min-width: 1280px) {
  .volunteer-main-shell {
    max-width: 1440px;
  }

  .volunteer-main {
    grid-template-columns: 430px minmax(0, 1fr);
  }
}

@media (min-width: 900px) {
  .volunteer-main-shell {
    width: min(1480px, calc(100vw - 48px));
    max-width: none;
  }

  .volunteer-layout {
    display: flex;
    flex-direction: column;
  }

  .volunteer-main {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .volunteer-section {
    display: grid;
    grid-template-columns: 190px minmax(0, 1fr);
    align-items: start;
    column-gap: 18px;
    row-gap: 12px;
  }

  .volunteer-section__head {
    grid-column: 1;
    grid-row: 1 / span 20;
    flex-direction: column;
    justify-content: flex-start;
    gap: 10px;
    margin-bottom: 0;
    padding-right: 16px;
    border-right: 1px solid #e2e8f0;
  }

  .volunteer-section__desc {
    display: block;
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.55;
  }

  .volunteer-section__index {
    order: -1;
  }

  .volunteer-section > .volunteer-block,
  .volunteer-section > .volunteer-form-grid,
  .volunteer-section > .option-group,
  .volunteer-section > .toggle-list,
  .volunteer-section > .volunteer-note,
  .volunteer-section > .interest-layout {
    grid-column: 2;
  }

  .volunteer-form-grid,
  .option-grid--double {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .option-grid--triple {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .interest-layout {
    grid-template-columns: minmax(0, 1fr) 320px;
  }
}

@media (min-width: 900px) {
  .volunteer-form-page {
    background: #f5f6f8;
  }

  .volunteer-main-shell {
    width: min(1280px, calc(100vw - 64px));
    padding-top: 20px;
  }

  .volunteer-hero {
    display: none;
  }

  .volunteer-layout {
    gap: 16px;
  }

  .volunteer-sidebar {
    display: grid;
    grid-template-columns: minmax(260px, 0.85fr) minmax(0, 1.3fr);
    gap: 12px;
  }

  .volunteer-main {
    display: grid;
    grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
    gap: 16px;
  }

  .volunteer-main > .volunteer-section:nth-child(4) {
    grid-column: 1 / -1;
  }

  .volunteer-section,
  .volunteer-summary-card,
  .volunteer-submit-card,
  .field-card,
  .interest-block,
  .toggle-item,
  .agreement-box,
  .sidebar-summary-item,
  .volunteer-state {
    border-radius: 8px;
    background: #fff;
    box-shadow: none;
  }

  .volunteer-section,
  .volunteer-summary-card,
  .volunteer-submit-card {
    padding: 18px;
    border: 1px solid #dfe3ea;
  }

  .volunteer-section {
    display: block;
  }

  .volunteer-section__head {
    display: flex;
    flex-direction: row;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 16px;
    padding-right: 0;
    border-right: 0;
  }

  .volunteer-section__title {
    font-size: 18px;
    letter-spacing: 0;
  }

  .volunteer-section__desc {
    display: block;
    max-width: 56ch;
    margin-top: 4px;
    color: #6b7280;
  }

  .volunteer-section__index {
    min-width: auto;
    height: auto;
    padding: 2px 8px;
    border-radius: 4px;
    background: #f3f4f6;
    color: #4b5563;
    font-size: 12px;
  }

  .subject-chip,
  .option-card,
  .hot-major-card,
  .region-chip,
  .tag-item,
  .summary-tag,
  .preference-badge,
  .add-btn {
    border-radius: 6px;
    box-shadow: none;
    transform: none !important;
  }

  .subject-chip {
    min-height: 38px;
    gap: 8px;
    padding: 0 10px;
    background: #fff;
  }

  .subject-chip__icon {
    width: 24px;
    height: 24px;
    border-radius: 5px;
    background: #f3f4f6 !important;
  }

  .subject-chip__icon-svg,
  .subject-icon--history .subject-chip__icon-svg,
  .subject-icon--chemistry .subject-chip__icon-svg,
  .subject-icon--biology .subject-chip__icon-svg,
  .subject-icon--politics .subject-chip__icon-svg,
  .subject-icon--geography .subject-chip__icon-svg {
    color: #374151;
  }

  .subject-chip.active,
  .option-card.active,
  .hot-major-card.active,
  .region-chip.active,
  .tag-item--major {
    border-color: #2563eb;
    background: #f8fbff;
    color: #1d4ed8;
    box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.08);
  }

  .field-card,
  .interest-block,
  .toggle-item,
  .volunteer-note,
  .agreement-box,
  .sidebar-summary-item {
    border: 1px solid #e5e7eb;
    background: #fafafa;
  }

  .field-card {
    padding: 12px;
  }

  .custom-field :deep(.van-field__body) {
    min-height: 38px;
    border-radius: 6px;
    background: #fff;
  }

  .option-card {
    min-height: 82px;
    padding: 12px;
  }

  .option-card--icon {
    padding-left: 40px;
  }

  .option-card--icon :deep(svg) {
    color: #4b5563;
  }

  .hot-major-card {
    grid-template-columns: 30px minmax(0, 1fr) 92px;
    padding: 9px 10px;
  }

  .hot-major-card__rank {
    width: 30px;
    height: 30px;
    border-radius: 6px;
    font-size: 13px;
  }

  .hot-major-card__rank.is-top3 {
    background: #111827;
  }

  .hot-major-card__heat {
    height: 24px;
    border-radius: 6px;
  }

  .hot-major-card__heat-bar {
    background: #bfdbfe;
  }

  .volunteer-state {
    border-color: #fde68a;
    background: #fffbeb;
  }

  .volunteer-state.is-ready {
    background: #f0fdf4;
  }

  .submit-btn {
    border-radius: 6px;
  }
}
</style>
