<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCurrentSafetyCode, setCurrentSafetyCode, useVolunteerStore } from '@/stores/volunteer'
import { generateVolunteerPlan, getGzBatchSupport, rankCheck } from '@/api/volunteer'
import { getHotMajors, type HotMajor } from '@/api/scoreLine'
import DisclaimerDialog from '@/components/DisclaimerDialog.vue'
import type { BatchSupportItem, CandidateType, GradientRangeKey, GradientRanges } from '@/types'
import {
  getProvinceConfig,
  normalizeProvinceCode,
  PROVINCE_LIST,
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

function provinceCodeFromRouteQuery(value: unknown): ProvinceCode | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (raw == null || String(raw).trim() === '') return null
  return normalizeProvinceCode(raw)
}

const totalScore = ref<number | undefined>(volunteerStore.formData.totalScore || undefined)
const lockedProvinceCode = ref<ProvinceCode | null>(provinceCodeFromRouteQuery(route.query.provinceCode))
const provinceCode = ref<ProvinceCode>(lockedProvinceCode.value || normalizeProvinceCode(volunteerStore.formData.provinceCode))
const candidateType = ref<CandidateType | ''>(volunteerStore.formData.candidateType || '')
const batchCode = ref(volunteerStore.formData.batchCode || '')
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
const qualificationTags = ref<string[]>([...(volunteerStore.formData.qualificationTags || [])])
const artProfessionalScore = ref<number | undefined>(volunteerStore.formData.artProfessionalScore)
const sportsProfessionalScore = ref<number | undefined>(volunteerStore.formData.sportsProfessionalScore)
const comprehensiveScore = ref<number | undefined>(volunteerStore.formData.comprehensiveScore)
const currentSafetyCode = ref(getCurrentSafetyCode())

const hotMajors = ref<HotMajor[]>([])
const batchSupportItems = ref<BatchSupportItem[]>([])
const activeAdmissionYear = ref<number>()
const latestOfficialDataYear = ref<number>()
const trainingYears = ref<number[]>([])
const recommendationPhase = ref('PRE_OFFICIAL_DATA')
const estimateMode = ref(true)
const historyYears = ref<number[]>([])
const batchSupportLoading = ref(false)
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

const candidateTypeOptions: Array<{ key: CandidateType; title: string; desc: string }> = [
  { key: '普通类', title: '普通类', desc: '普通本科、专科、提前批和专项计划' },
  { key: '艺术类', title: '艺术类', desc: '按艺术类批次展示，不套普通位次模型' },
  { key: '体育类', title: '体育类', desc: '按体育类批次展示，不套普通位次模型' },
]

const qualificationOptions = [
  '国家专项计划', '地方专项计划', '高校专项计划', '民族班', '预科班', '定向招生', '免费医学定向', '优师专项',
]

const strategyOptions = [
  { key: '保守型', title: '保守型', desc: '更重视风险复核，优先降低高波动志愿占比' },
  { key: '均衡型', title: '均衡型', desc: '冲稳保兼顾，适合大多数考生作为主方案' },
  { key: '冲刺型', title: '冲刺型', desc: '接受更高波动，优先争取平台或热门方向' },
] as const

const provinceOptions = PROVINCE_LIST.map(item => ({
  key: item.code,
  title: item.shortName,
  desc: `${item.targetBatch}，${item.volunteerUnit} · ${item.statusLabel}`,
}))

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
const isProvinceEntryLocked = computed(() => Boolean(lockedProvinceCode.value))
const admissionYearText = computed(() => (activeAdmissionYear.value ? `${activeAdmissionYear.value}届` : '最新高考年份'))
const historyYearText = computed(() => historyYears.value.length ? historyYears.value.join('、') : '历史')
const trainingYearText = computed(() => trainingYears.value.length ? trainingYears.value.join('、') : historyYearText.value)
const isPreOfficialData = computed(() => recommendationPhase.value === 'PRE_OFFICIAL_DATA' || estimateMode.value)
const isOfficialDataPartial = computed(() => recommendationPhase.value === 'OFFICIAL_DATA_PARTIAL')
const isOfficialDataImported = computed(() => recommendationPhase.value === 'OFFICIAL_DATA_IMPORTED')
const isModelRetrained = computed(() => recommendationPhase.value === 'MODEL_RETRAINED')
const shouldShowPhaseNotice = computed(() => isPreOfficialData.value || isOfficialDataPartial.value || isOfficialDataImported.value || isModelRetrained.value)
const phaseBadgeText = computed(() => (
  isPreOfficialData.value
    ? `${activeAdmissionYear.value || 2026}官方数据未发布`
    : isOfficialDataPartial.value
      ? `${activeAdmissionYear.value || 2026}官方数据导入中`
      : isOfficialDataImported.value
        ? `${activeAdmissionYear.value || 2026}官方数据已导入`
        : isModelRetrained.value
        ? '模型已重训'
        : '数据状态待核验'
))
const phaseNoticeText = computed(() => (
  isPreOfficialData.value
    ? `${activeAdmissionYear.value || 2026} 年招生计划和一分一段表尚未发布；当前只基于 ${trainingYearText.value} 年历史数据展示趋势、缺口和预估参考，不作为正式推荐。`
    : isOfficialDataPartial.value
      ? `${activeAdmissionYear.value || 2026} 年官方数据正在分批导入和质检；页面可查看数据准备进度，普通本科/专科仍不会开放完整推荐。`
      : isOfficialDataImported.value
        ? `${activeAdmissionYear.value || 2026} 官方数据已导入；普通本科/专科进入试推荐阶段，模型重训完成前不展示完整推荐。`
        : isModelRetrained.value
          ? `${activeAdmissionYear.value || 2026} 官方数据已导入并完成模型重训；仍请以考试院和高校章程为准。`
          : `当前已按 ${latestOfficialDataYear.value || activeAdmissionYear.value || '最新'} 年官方数据口径展示批次支持状态。`
))
const pageTitle = computed(() => (isProvinceEntryLocked.value ? `${provinceName.value}志愿填报` : '智能志愿填报'))
const pageSubtitle = computed(() => (
  isProvinceEntryLocked.value
    ? `${provinceName.value}专区已锁定，省份选择和接口请求固定使用${provinceName.value}口径`
    : isProvinceGenerateLocked.value
      ? '查看地区口径、表单字段和数据准备状态'
      : '先选择考生类别和批次，再填写影响推荐结果的条件'
))
const heroTitle = computed(() => (
  isProvinceEntryLocked.value ? `${provinceName.value}专区志愿填报` : '主流程只保留真正影响结果的输入项'
))
const heroDescription = computed(() => (
  isProvinceEntryLocked.value
    ? `当前从${provinceName.value}入口进入，页面、批次矩阵和生成请求均固定为${provinceName.value}，公共生成年份锁定为${admissionYearText.value}。`
    : isProvinceGenerateLocked.value
      ? currentProvince.value.volunteerLockDescription
      : `系统会按${admissionYearText.value}批次综合分数、位次、选科、偏好、预算与风险取向给出对应结果。`
))
const gzDataScopeText = computed(() => {
  if (provinceCode.value !== 'GZ') return ''
  const years = trainingYears.value.length ? trainingYears.value.join('、') : '2024、2025'
  return `贵州历史库已按 ${years} 年口径补入普通类历史/物理专业分与院校级投档线；2024 艺术、体育批次保留查询与复核入口，2025 艺体专业分等待考试院或高校后续公开源补充。`
})
watch(pageTitle, (title) => {
  document.title = title
}, { immediate: true })
const targetVolunteerCount = computed(() => (
  selectedBatchSupport.value
    ? selectedBatchSupport.value.maxVolunteerCount || selectedBatchSupport.value.targetCount || 0
    : currentProvince.value.targetCount
))
const volunteerUnitLabel = computed(() => (
  selectedBatchSupport.value?.volunteerMode || (currentProvince.value.volunteerUnitType === 'PROFESSIONAL_GROUP_45' ? '院校专业组' : '志愿')
))
function fallbackDataStatus(status = 'UNKNOWN') {
  return {
    status,
    policyCount: 0,
    scoreLineCount: 0,
    majorScoreCount: 0,
    historyCount: 0,
    planCount: 0,
    requirementCount: 0,
    ready: false,
    detail: '待接口返回批次支持矩阵',
  }
}
const fallbackBatchSupportItems: BatchSupportItem[] = [
  ['NORMAL_UNDERGRADUATE', '普通本科批', '普通类', 'ORDINARY', 'QUERY_ONLY', 'QUERY_ONLY', 96, '专业（类）+ 院校', 'QueryOnlyRecommendEngine', '批次支持矩阵未返回，先按查询说明处理，不展示完整推荐'],
  ['NORMAL_SPECIALTY', '普通类高职专科批', '普通类', 'ORDINARY', 'TRIAL_RECOMMEND', 'PARALLEL_MAJOR', 96, '专业（类）+ 院校', 'OrdinaryParallelMajorEngine', '普通类高职专科批按96个专业（类）+院校推荐，专科梯度窗口采用更宽口径并保留保档自动扩展'],
  ['EARLY_A_B', '普通类本科提前批A/B段', '普通类', 'EARLY', 'QUERY_ONLY', 'SEQUENTIAL_COLLEGE', 1, '院校顺序志愿', 'SequentialCollegeEngine', '提前A/B段为1个院校顺序志愿，当前仅展示顺序志愿规则、资格条件和数据缺口'],
  ['EARLY_C', '普通类本科提前批C段', '普通类', 'EARLY', 'QUERY_ONLY', 'PARALLEL_MAJOR_60', 60, '专业（类）平行志愿', 'EarlyCParallelMajorEngine', '提前C段为60个专业（类）平行志愿，需单独处理公费师范、优师、免费医学、定向和履约风险'],
  ['SPECIALTY_EARLY', '普通类高职专科提前批', '普通类', 'EARLY', 'QUERY_ONLY', 'SEQUENTIAL_COLLEGE', 1, '院校顺序志愿', 'SequentialCollegeEngine', '高职专科提前批为1个院校顺序志愿，需单独计划与资格规则，当前仅返回规则与数据缺口'],
  ['NATIONAL_SPECIAL', '国家专项计划', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '专项计划', 'SpecialPlanEligibilityEngine', '国家专项计划需资格校验和单独计划数据'],
  ['LOCAL_SPECIAL', '地方专项计划', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '专项计划', 'SpecialPlanEligibilityEngine', '地方专项需资格校验和单独计划数据'],
  ['UNIVERSITY_SPECIAL', '高校专项计划', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '专项计划', 'SpecialPlanEligibilityEngine', '高校专项需报名审核结果与学校名单'],
  ['ETHNIC_CLASS', '民族班', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '特殊计划', 'SpecialPlanEligibilityEngine', '民族班需民族与资格条件校验'],
  ['PREPARATORY', '预科班', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '特殊计划', 'SpecialPlanEligibilityEngine', '预科班需资格与计划数据校验'],
  ['ORIENTED', '定向招生', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '特殊计划', 'SpecialPlanEligibilityEngine', '定向招生需协议与地区资格校验'],
  ['FREE_MEDICAL', '免费医学定向', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '特殊计划', 'SpecialPlanEligibilityEngine', '免费医学定向需资格与履约条件校验'],
  ['TEACHER_EXCELLENCE', '优师专项', '普通类', 'SPECIAL_PROGRAM', 'QUERY_ONLY', 'ELIGIBILITY_QUERY', 0, '特殊计划', 'SpecialPlanEligibilityEngine', '优师专项需资格、履约和单独计划数据'],
  ['ART_UNDERGRADUATE_A', '艺术类本科A段', '艺术类', 'ART', 'QUERY_ONLY', 'SEQUENTIAL_COLLEGE', 1, '院校顺序志愿', 'ArtCompositeRecommendEngine', '艺术本科A段为1个院校顺序志愿，需艺术专业成绩、文化成绩、综合分规则和院校章程共同复核'],
  ['ART_UNDERGRADUATE_B', '艺术类本科B段', '艺术类', 'ART', 'QUERY_ONLY', 'ART_COMPOSITE', 60, '专业（类）平行志愿', 'ArtCompositeRecommendEngine', '艺术本科B段为60个专业（类）平行志愿，必须按艺术综合成绩口径，不套用普通位次推荐模型'],
  ['ART_SPECIALTY', '艺术类高职专科批', '艺术类', 'ART', 'QUERY_ONLY', 'ART_COMPOSITE', 60, '专业（类）平行志愿', 'ArtCompositeRecommendEngine', '艺术高职专科批为60个专业（类）平行志愿，必须按艺术综合成绩口径，不套用普通位次推荐模型'],
  ['SPORTS_UNDERGRADUATE', '体育类本科批', '体育类', 'SPORTS', 'QUERY_ONLY', 'SPORTS_COMPOSITE', 60, '专业（类）平行志愿', 'SportsCompositeRecommendEngine', '体育本科批为60个专业（类）平行志愿，必须按体育综合成绩口径，不套用普通位次推荐模型'],
  ['SPORTS_SPECIALTY', '体育类高职专科批', '体育类', 'SPORTS', 'QUERY_ONLY', 'SPORTS_COMPOSITE', 60, '专业（类）平行志愿', 'SportsCompositeRecommendEngine', '体育高职专科批为60个专业（类）平行志愿，必须按体育综合成绩口径，不套用普通位次推荐模型'],
].map(([batchCode, batchName, type, category, supportLevel, recommendMode, targetCount, volunteerMode, engine, supportNote]) => ({
  batchCode: batchCode as string,
  batchName: batchName as string,
  candidateType: type as CandidateType,
  category: category as string,
  supportLevel: supportLevel as BatchSupportItem['supportLevel'],
  recommendMode: recommendMode as string,
  engine: engine as string,
  engineName: engine as string,
  targetCount: targetCount as number,
  maxVolunteerCount: targetCount as number,
  majorPerSchoolCount: 0,
  hasAdjustment: false,
  volunteerMode: volunteerMode as string,
  policyConfigured: false,
  policyStatus: 'registry_only',
  scoreLineCount: 0,
  majorScoreCount: 0,
  planCount: 0,
  requirementCount: 0,
  dataStatus: fallbackDataStatus(),
  missingData: ['batch-support接口未返回'],
  supportNote: supportNote as string,
  supportReason: supportNote as string,
  warnings: [supportNote as string],
}))
const batchOptions = computed(() => {
  if (!candidateType.value) return []
  const items = provinceCode.value === 'GZ' && batchSupportItems.value.length ? batchSupportItems.value : fallbackBatchSupportItems
  const filtered = items.filter((item: BatchSupportItem) => item.candidateType === candidateType.value)
  return filtered.length ? filtered : fallbackBatchSupportItems.filter(item => item.candidateType === candidateType.value)
})
const selectedBatchSupport = computed(() => batchOptions.value.find((item: BatchSupportItem) => item.batchCode === batchCode.value))
const targetBatchLabel = computed(() => selectedBatchSupport.value?.batchName || '待选择批次')
const canGenerateForSelectedBatch = computed(() => (
  provinceCode.value !== 'GZ'
  || isPreOfficialData.value
  || selectedBatchSupport.value?.supportLevel === 'FULL_RECOMMEND'
  || selectedBatchSupport.value?.supportLevel === 'TRIAL_RECOMMEND'
))
const submitButtonText = computed(() => {
  if (isGenerateLocked.value) return batchSupportGateText.value || '生成能力暂未开放'
  if (generating.value) return isPreOfficialData.value ? '说明生成中…' : '生成中…'
  if (!hasCurrentDisclaimer.value) return isPreOfficialData.value ? '阅读风险告知并查看说明' : '阅读风险告知并生成'
  return isPreOfficialData.value
    ? '查看预估/数据缺口说明'
    : `生成 ${targetVolunteerCount.value} 个${volunteerUnitLabel.value}`
})
const batchSupportGateText = computed(() => {
  if (provinceCode.value !== 'GZ') return ''
  if (!candidateType.value) return '请选择考生类别'
  if (!batchCode.value || !selectedBatchSupport.value) return '请选择目标批次'
  if (canGenerateForSelectedBatch.value) return ''
  if (selectedBatchSupport.value.supportLevel === 'QUERY_ONLY') {
    return `${selectedBatchSupport.value.batchName}当前仅支持政策和数据缺口说明，暂不生成完整推荐。`
  }
  return `${selectedBatchSupport.value.batchName}当前暂不支持生成完整推荐。`
})
const isProvinceGenerateLocked = computed(() => currentProvince.value.status !== 'open')
const isGenerateLocked = computed(() => isProvinceGenerateLocked.value || Boolean(batchSupportGateText.value))
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
const canSubmit = computed(() => formReady.value && !isGenerateLocked.value)

const missingItems = computed(() => {
  const items: string[] = []
  if (provinceCode.value === 'GZ' && !candidateType.value) items.push('考生类别')
  if (provinceCode.value === 'GZ' && (!batchCode.value || !selectedBatchSupport.value)) items.push('目标批次')
  if (totalScore.value === undefined || totalScore.value <= 0) items.push('高考总分')
  if ((provinceRank.value === undefined || provinceRank.value <= 0) && !hasRankEstimate.value) {
    items.push('全省位次或官方估算位次')
  }
  if (resubjects.value.length !== 2) items.push('两门再选科目')
  if (rangeValidationMessage.value) items.push('梯度区间')
  return items
})

const readinessText = computed(() => (
  isProvinceGenerateLocked.value
    ? '生成能力暂未开放'
    : batchSupportGateText.value ? '当前批次仅展示政策说明'
    : missingItems.value.length ? `还差 ${missingItems.value.length} 项必填信息` : '已满足生成条件'
))

const readinessDetail = computed(() => (
  isProvinceGenerateLocked.value
    ? currentProvince.value.volunteerLockDescription
    : batchSupportGateText.value ? batchSupportGateText.value
    : missingItems.value.length
    ? `请继续补全：${missingItems.value.join('、')}`
    : hasCurrentDisclaimer.value
      ? isPreOfficialData.value
        ? `可以查看 ${activeAdmissionYear.value} 年预估/数据缺口说明；${trainingYearText.value} 年仅用于历史趋势和模型校准，不会伪装为 ${activeAdmissionYear.value} 官方数据。`
        : `可以直接生成 ${activeAdmissionYear.value} 年 ${targetVolunteerCount.value} 个${volunteerUnitLabel.value}草稿；${historyYearText.value} 年历史数据仅用于后台回测和模型校准。`
      : isPreOfficialData.value
        ? `点击按钮阅读并确认风险告知后，可查看 ${activeAdmissionYear.value} 年预估/数据缺口说明。`
        : `点击生成按钮阅读并确认生成前风险告知后，即可生成 ${activeAdmissionYear.value} 年 ${targetVolunteerCount.value} 个${volunteerUnitLabel.value}草稿。`
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
    label: `${admissionYearText.value}高考总分`,
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

async function loadBatchSupport() {
  if (provinceCode.value !== 'GZ') {
    batchSupportItems.value = []
    return
  }
  batchSupportLoading.value = true
  try {
    const res = await getGzBatchSupport()
    if (res.data.code === 0) {
      activeAdmissionYear.value = res.data.data.activeAdmissionYear || res.data.data.year
      latestOfficialDataYear.value = res.data.data.latestOfficialDataYear
      historyYears.value = res.data.data.historyYears || []
      trainingYears.value = res.data.data.trainingYears || res.data.data.historyYears || []
      recommendationPhase.value = res.data.data.recommendationPhase || ''
      estimateMode.value = Boolean(res.data.data.estimateMode)
      batchSupportItems.value = res.data.data.items || []
      syncSelectedBatch()
    }
  } catch {
    batchSupportItems.value = []
  } finally {
    batchSupportLoading.value = false
  }
}

function syncSelectedBatch() {
  if (!batchOptions.value.some((item: BatchSupportItem) => item.batchCode === batchCode.value)) {
    batchCode.value = ''
  }
}

function selectCandidateType(type: CandidateType) {
  candidateType.value = type
  syncSelectedBatch()
}

function selectBatch(code: string) {
  batchCode.value = code
}

function toggleQualification(tag: string) {
  const idx = qualificationTags.value.indexOf(tag)
  if (idx > -1) {
    qualificationTags.value.splice(idx, 1)
  } else {
    qualificationTags.value.push(tag)
  }
}

watch([firstSubject, provinceCode], loadHotMajors)
watch([candidateType, batchSupportItems], syncSelectedBatch)
onMounted(() => {
  loadHotMajors()
  loadBatchSupport()
})

watch(() => route.query.provinceCode, (value: unknown) => {
  const next = provinceCodeFromRouteQuery(value)
  lockedProvinceCode.value = next
  if (next && provinceCode.value !== next) {
    provinceCode.value = next
  }
}, { immediate: true })

watch(provinceCode, (code: ProvinceCode) => {
  void loadBatchSupport()
  volunteerStore.setFormData({ provinceCode: code })
})

watch([candidateType, batchCode], ([type, batch]: [CandidateType | '', string]) => {
  if (type) {
    volunteerStore.setFormData({ candidateType: type, batchCode: batch })
  } else {
    volunteerStore.setFormData({ batchCode: batch })
  }
})

watch(currentSafetyCode, (value: string) => {
  setCurrentSafetyCode(normalizeSafetyCodeInput(value))
})

watch(strategyMode, (mode: '保守型' | '均衡型' | '冲刺型') => {
  if (gradientRangeMode.value === 'preset') {
    gradientRanges.value = presetGradientRanges(mode)
  }
})

watch([totalScore, provinceRank, firstSubject, provinceCode], (values: [number | undefined, number | undefined, '物理' | '历史', ProvinceCode]) => {
  const [score, rank, subject, province] = values
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

function supportLevelText(level: string) {
  if (isPreOfficialData.value && (level === 'FULL_RECOMMEND' || level === 'TRIAL_RECOMMEND' || level === 'QUERY_ONLY')) {
    return '预估/缺口说明'
  }
  if (isOfficialDataPartial.value && level === 'QUERY_ONLY') {
    return '准备中/缺口说明'
  }
  return ({
    FULL_RECOMMEND: '完整推荐',
    TRIAL_RECOMMEND: '试推荐',
    QUERY_ONLY: '仅规则/缺口展示',
    UNSUPPORTED: '暂不支持',
  } as Record<string, string>)[level] || level
}

function dataStatusText(status: BatchSupportItem['dataStatus'] | string | undefined) {
  const code = typeof status === 'string' ? status : status?.status || 'UNKNOWN'
  return ({
    READY: '数据就绪',
    POLICY_MISSING: '政策缺失',
    HISTORY_MISSING: '历史线缺失',
    PLAN_MISSING: '计划缺失',
    PRE_OFFICIAL_DATA: '官方未发布',
    OFFICIAL_DATA_PARTIAL: '准备中',
    UNKNOWN: '待核验',
  } as Record<string, string>)[code] || code
}

function setRangeMode(mode: 'preset' | 'custom') {
  gradientRangeMode.value = mode
  if (mode === 'preset') {
    gradientRanges.value = presetGradientRanges(strategyMode.value)
  }
}

function selectProvince(code: ProvinceCode): void {
  if (lockedProvinceCode.value) return
  if (provinceCode.value !== code) {
    provinceCode.value = code
  }
}

function normalizeSafetyCodeInput(value: string) {
  return value.trim().replace(/[-\s]/g, '').toUpperCase()
}

function syncSafetyCodeInput() {
  const normalized = normalizeSafetyCodeInput(currentSafetyCode.value)
  currentSafetyCode.value = normalized
  setCurrentSafetyCode(normalized)
  return normalized
}

function createLocalSafetyCode() {
  const charset = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  const bytes = new Uint8Array(10)
  window.crypto.getRandomValues(bytes)
  currentSafetyCode.value = Array.from(bytes, byte => charset[byte % charset.length]).join('')
  setCurrentSafetyCode(currentSafetyCode.value)
}

function updateRange(key: GradientRangeKey, field: keyof GradientRanges[GradientRangeKey], rawValue: unknown) {
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

function updateRangeMin(key: GradientRangeKey, rawValue: unknown) {
  updateRange(key, 'rankOffsetMin', rawValue)
}

function updateRangeMax(key: GradientRangeKey, rawValue: unknown) {
  updateRange(key, 'rankOffsetMax', rawValue)
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
    showToast(batchSupportGateText.value || currentProvince.value.volunteerLockTitle)
    return
  }
  if (!canSubmit.value || generating.value) return
  if (!syncSafetyCodeInput()) {
    showToast('请先输入安全码后再生成志愿方案。')
    return
  }
  if (!hasCurrentDisclaimer.value) {
    openDisclaimer(true)
    return
  }

  await submitPlan()
}

async function submitPlan() {
  if (isGenerateLocked.value) {
    showToast(batchSupportGateText.value || currentProvince.value.volunteerLockTitle)
    return
  }
  if (!canSubmit.value || !hasCurrentDisclaimer.value || generating.value) return
  const safetyCode = syncSafetyCodeInput()
  if (!safetyCode) {
    showToast('请先输入安全码后再生成志愿方案。')
    return
  }

  generating.value = true
  showLoadingToast({ message: '方案生成中...', forbidClick: true, duration: 0 })
  try {
    const payload = {
      provinceCode: provinceCode.value,
      province: provinceName.value,
      year: activeAdmissionYear.value,
      candidateType: (candidateType.value || '普通类') as CandidateType,
      batchCode: batchCode.value,
      totalScore: totalScore.value!,
      provinceRank: provinceRank.value && provinceRank.value > 0 ? provinceRank.value : 0,
      firstSubject: firstSubject.value,
      resubjects: [...resubjects.value],
      preferredMajors: [...preferredMajors.value],
      preferredRegions: [...preferredRegions.value],
      strategyMode: strategyMode.value,
      decisionPriority: decisionPriority.value,
      careerGoal: careerGoal.value,
      tuitionBudget: tuitionBudget.value,
      acceptPrivate: acceptPrivate.value,
      acceptSinoForeign: acceptSinoForeign.value,
      safetyCode,
      agreedDisclaimer: true as const,
      disclaimerVersion: disclaimerVersion.value,
      gradientRanges: cloneGradientRanges(gradientRanges.value),
      qualificationTags: [...qualificationTags.value],
      artProfessionalScore: artProfessionalScore.value,
      sportsProfessionalScore: sportsProfessionalScore.value,
      comprehensiveScore: comprehensiveScore.value,
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
          <div class="gz-shell-title">{{ pageTitle }}</div>
          <div class="gz-shell-subtitle">
            {{ pageSubtitle }}
          </div>
        </div>
        <div class="gz-shell-header-extra">{{ progressRatio }}%</div>
      </div>
    </header>

    <div class="gz-shell-main volunteer-main-shell">
      <section class="gz-shell-hero volunteer-hero">
        <div class="volunteer-hero__copy">
          <span class="gz-shell-kicker">volunteer plan</span>
          <h1 class="gz-shell-hero-title">{{ heroTitle }}</h1>
          <p class="gz-shell-hero-desc">
            {{ heroDescription }}
          </p>
          <div class="gz-shell-chip-row volunteer-hero__chips">
            <span class="gz-shell-chip is-soft-active">{{ subjectTypeLabel }}</span>
            <span class="gz-shell-chip is-soft-active">{{ provinceName }}</span>
            <span class="gz-shell-chip is-soft-active">{{ strategyMode }}</span>
            <span class="gz-shell-chip is-soft-active">{{ decisionPriority }}</span>
            <span class="gz-shell-chip is-soft-active">{{ careerGoal }}</span>
            <span class="gz-shell-chip is-soft-active">{{ phaseBadgeText }}</span>
          </div>
          <div v-if="provinceCode === 'GZ'" class="volunteer-note volunteer-note--warning">
            {{ phaseNoticeText }}
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

      <section v-if="isProvinceGenerateLocked" class="gz-shell-panel volunteer-lock-panel">
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

            <div class="option-group">
              <div class="volunteer-block__label">填报省份</div>
              <div v-if="isProvinceEntryLocked" class="volunteer-province-lock-card">
                <div class="volunteer-province-lock-card__badge">已锁定{{ provinceName }}入口</div>
                <div class="volunteer-province-lock-card__body">
                  <h3>{{ provinceName }}专区</h3>
                  <p>当前访问地址携带省份入口参数，填报省份固定为{{ provinceName }}，批次矩阵、位次校验和生成请求均使用 {{ provinceCode }} 口径。</p>
                  <span>{{ currentProvince.targetBatch }} · {{ currentProvince.volunteerUnit }} · {{ currentProvince.statusLabel }}</span>
                </div>
              </div>
              <div v-else class="option-grid option-grid--double">
                <button
                  v-for="opt in provinceOptions"
                  :key="opt.key"
                  type="button"
                  class="option-card"
                  :class="{ active: provinceCode === opt.key }"
                  @click="selectProvince(opt.key)"
                >
                  <span class="option-card__title">{{ opt.title }}</span>
                  <span class="option-card__desc">{{ opt.desc }}</span>
                </button>
              </div>
              <div v-if="currentProvince.volunteerUnitType === 'PROFESSIONAL_GROUP_45'" class="volunteer-note">
                {{ currentProvince.shortName }}当前按{{ currentProvince.targetBatch }}院校专业组建模；公开可核验数据不足 45 个时，系统会提示补数据，不会伪装完整方案。
              </div>
            </div>

            <div v-if="provinceCode === 'GZ'" class="option-group">
              <div class="volunteer-block__label">考生类别</div>
              <div class="option-grid option-grid--triple">
                <button
                  v-for="opt in candidateTypeOptions"
                  :key="opt.key"
                  type="button"
                  class="option-card"
                  :class="{ active: candidateType === opt.key }"
                  @click="selectCandidateType(opt.key)"
                >
                  <span class="option-card__title">{{ opt.title }}</span>
                  <span class="option-card__desc">{{ opt.desc }}</span>
                </button>
              </div>
            </div>

            <div v-if="provinceCode === 'GZ'" class="option-group">
              <div class="volunteer-block__label">目标批次</div>
              <div class="option-grid option-grid--double">
                <button
                  v-for="item in batchOptions"
                  :key="item.batchCode"
                  type="button"
                  class="option-card"
                  :class="{ active: batchCode === item.batchCode }"
                  @click="selectBatch(item.batchCode)"
                >
                  <span class="option-card__title">{{ item.batchName }}</span>
                  <span class="option-card__desc">{{ supportLevelText(item.supportLevel) }} · {{ dataStatusText(item.dataStatus) }}</span>
                  <span class="option-card__desc">{{ item.recommendMode }} · 最多 {{ item.maxVolunteerCount || item.targetCount || 0 }} 个</span>
                </button>
              </div>
              <div class="volunteer-note">
                {{ batchSupportLoading ? '批次支持矩阵读取中…' : selectedBatchSupport?.supportReason || selectedBatchSupport?.supportNote || '请选择目标批次，系统不会默认套用普通本科批。' }}
              </div>
              <div v-if="shouldShowPhaseNotice" class="volunteer-note volunteer-note--warning">
                <strong>{{ phaseBadgeText }}</strong>
                <span>{{ phaseNoticeText }}</span>
              </div>
              <div v-if="gzDataScopeText" class="volunteer-note volunteer-note--data">
                <strong>贵州数据口径</strong>
                <span>{{ gzDataScopeText }}</span>
              </div>
              <div v-if="selectedBatchSupport && !canGenerateForSelectedBatch" class="volunteer-note volunteer-note--warning">
                <strong>{{ supportLevelText(selectedBatchSupport.supportLevel) }}</strong>
                <span>{{ selectedBatchSupport.supportReason || selectedBatchSupport.supportNote }}</span>
                <span v-if="selectedBatchSupport.warnings?.length">缺口：{{ selectedBatchSupport.warnings.join('；') }}</span>
              </div>
            </div>

            <div v-if="selectedBatchSupport?.category === 'SPECIAL_PROGRAM'" class="option-group">
              <div class="volunteer-block__label">资格标签</div>
              <div class="preference-tags">
                <button
                  v-for="tag in qualificationOptions"
                  :key="tag"
                  type="button"
                  class="preference-tag"
                  :class="{ active: qualificationTags.includes(tag) }"
                  @click="toggleQualification(tag)"
                >
                  {{ tag }}
                </button>
              </div>
            </div>

            <div v-if="candidateType !== '普通类'" class="volunteer-form-grid">
              <div v-if="candidateType === '艺术类'" class="field-card">
                <label class="field-card__label">艺术专业成绩</label>
                <div class="field-card__input-wrap">
                  <van-field v-model.number="artProfessionalScore" type="digit" placeholder="可选填" class="custom-field" />
                  <span class="field-card__unit">分</span>
                </div>
              </div>
              <div v-if="candidateType === '体育类'" class="field-card">
                <label class="field-card__label">体育专业成绩</label>
                <div class="field-card__input-wrap">
                  <van-field v-model.number="sportsProfessionalScore" type="digit" placeholder="可选填" class="custom-field" />
                  <span class="field-card__unit">分</span>
                </div>
              </div>
              <div class="field-card">
                <label class="field-card__label">综合分</label>
                <div class="field-card__input-wrap">
                  <van-field v-model.number="comprehensiveScore" type="number" placeholder="可选填" class="custom-field" />
                  <span class="field-card__unit">分</span>
                </div>
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
                      @update:model-value="updateRangeMin(row.key, $event)"
                    />
                    <span>至</span>
                    <van-field
                      :model-value="gradientRanges[row.key].rankOffsetMax"
                      type="number"
                      inputmode="numeric"
                      class="custom-field custom-field--compact range-field"
                      @update:model-value="updateRangeMax(row.key, $event)"
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
                    <div class="hot-major-card__rank" :class="{ 'is-top3': major.rank <= 3 }">{{ major.rank }}</div>
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

        <div class="safety-code-box">
          <label class="safety-code-label" for="volunteer-safety-code">安全码</label>
          <div class="safety-code-row">
            <input
              id="volunteer-safety-code"
              v-model.trim="currentSafetyCode"
              class="safety-code-input"
              type="text"
              inputmode="text"
              autocomplete="off"
              placeholder="请输入或生成安全码"
              @blur="syncSafetyCodeInput"
            />
            <button type="button" class="safety-code-create" @click="createLocalSafetyCode">生成</button>
          </div>
          <p class="safety-code-hint">安全码只随本次生成请求提交，不会写入地址栏；请妥善保存，后续查看方案需要它。</p>
        </div>

        <button class="submit-btn" :class="{ disabled: !canSubmit || generating }" :disabled="!canSubmit || generating" @click="onSubmit">
          <span>{{ submitButtonText }}</span>
          <ArrowRight :size="18" />
        </button>
        <p class="submit-hint">
          {{ isGenerateLocked ? batchSupportGateText || currentProvince.volunteerLockDescription : isPreOfficialData ? phaseNoticeText : `${admissionYearText}公共填报入口已锁定，历史年份仅用于后台回测和模型校准。` }}
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

.volunteer-province-lock-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(37, 99, 235, 0.22);
  background: linear-gradient(135deg, #eff6ff 0%, #ffffff 100%);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.06);
}

.volunteer-province-lock-card__badge {
  width: fit-content;
  padding: 5px 10px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  line-height: 1.4;
  font-weight: 800;
}

.volunteer-province-lock-card__body h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
}

.volunteer-province-lock-card__body p {
  margin: 8px 0 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.volunteer-province-lock-card__body span {
  display: inline-flex;
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  font-weight: 700;
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

.volunteer-note {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
}

.volunteer-note--data {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.volunteer-note--data strong {
  display: block;
  margin-bottom: 4px;
  color: #14532d;
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

.preference-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.preference-tag {
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

.preference-tag.active {
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

.safety-code-box {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.safety-code-label {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.safety-code-row {
  display: flex;
  gap: 10px;
}

.safety-code-input {
  flex: 1;
  min-width: 0;
  min-height: 46px;
  padding: 0 14px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  outline: none;
}

.safety-code-input:focus {
  border-color: rgba(37, 99, 235, 0.45);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
}

.safety-code-create {
  min-height: 46px;
  padding: 0 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.safety-code-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
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
