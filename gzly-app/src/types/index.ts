/**
 * 域模型。
 *
 * 后端 `gzly-web` 的 TS 类型里 464 个字段有 275 个可选（59%），其中 VolunteerItem
 * 75 个字段 64 个可选（85%）。直接照抄会让 App 每处 UI 都要写兜底。
 *
 * 这里采用两段式：
 *   Raw*  —— 贴着线上 JSON 的形态，全部可选（后端配了 non_null，null 字段会直接消失）
 *   非 Raw —— 归一化之后的形态，UI 只消费这一层，字段全部必填
 *
 * 归一化函数见 `utils/normalize.ts`。新增字段先进 Raw，确认稳定后再进归一层。
 */

export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  records: T[]
}

export type ProvinceCode = 'GZ' | 'SC' | 'HB' | 'AH' | 'GX' | 'HI' | 'YN' | 'HA'

export type Gradient = '冲' | '稳' | '保' | '垫'
export type RiskColor = 'green' | 'yellow' | 'red'
export type StrategyMode = '保守型' | '均衡型' | '冲刺型'
export type FirstSubject = '物理' | '历史'

/** 志愿单位：贵州是「专业（类）+ 院校」，其余省份是「院校专业组」 */
export type VolunteerUnitType = 'MAJOR_96' | 'PROFESSIONAL_GROUP_45'

/** 省级数据就绪度。当前后端 FULL 恒不返回，最好状态是 ESTIMATE。 */
export type ReadinessLevel = 'LOCKED' | 'QUERY_ONLY' | 'ESTIMATE' | 'FULL'
export type SupportLevel = 'FULL_RECOMMEND' | 'ESTIMATE_RECOMMEND' | 'QUERY_ONLY'

// ---------------------------------------------------------------------------
// 批次支持（省份入口置灰的唯一依据）
// ---------------------------------------------------------------------------

export interface RawBatchSupportItem {
  batchCode?: string
  batchName?: string
  candidateType?: string
  supportLevel?: SupportLevel
  targetCount?: number
  maxVolunteerCount?: number
  volunteerMode?: string
  policyStatus?: string
  officialSourceTitle?: string
  officialSourceUrl?: string
  generatorReady?: boolean
  supportReason?: string
  missingData?: string[]
}

export interface RawBatchSupportResponse {
  provinceCode?: string
  provinceName?: string
  year?: number
  targetYear?: number
  latestOfficialDataYear?: number
  dataSourceYears?: number[]
  recommendationPhase?: string
  officialDataReady?: boolean
  readinessLevel?: ReadinessLevel
  items?: RawBatchSupportItem[]
  summary?: Record<string, number>
  warnings?: string[]
}

export interface BatchSupportItem {
  batchCode: string
  batchName: string
  candidateType: string
  supportLevel: SupportLevel
  targetCount: number
  volunteerMode: string
  policyStatus: string
  officialSourceTitle: string
  officialSourceUrl: string
  generatorReady: boolean
  supportReason: string
  missingData: string[]
}

export interface BatchSupport {
  provinceCode: string
  provinceName: string
  year: number
  latestOfficialDataYear: number
  dataSourceYears: number[]
  readinessLevel: ReadinessLevel
  items: BatchSupportItem[]
  warnings: string[]
  /** 是否有任何一个批次可以生成。省份入口置灰只看这一个值。 */
  anyGeneratorReady: boolean
}

// ---------------------------------------------------------------------------
// 志愿条目
// ---------------------------------------------------------------------------

export interface RawHistoryRecord {
  year?: number
  minScore?: number
  minRank?: number
  avgScore?: number
  maxScore?: number
  planCount?: number
  batch?: string
  subjectType?: string
  groupCode?: string
  dataSourceType?: string
  confidenceLabel?: string
  rankSourceType?: string
  rankSourceNote?: string
  rankLow?: number
  rankHigh?: number
  rankSourceUrl?: string
  rankSourcePageUrl?: string
}

export interface RawVolunteerItem {
  index?: number
  provinceCode?: string
  volunteerUnitType?: string
  volunteerUnitLabel?: string
  universityName?: string
  schoolId?: string
  groupCode?: string
  groupName?: string
  groupMajors?: string[]
  obeyAdjustment?: boolean
  majorName?: string
  province?: string
  city?: string
  tags?: string[]
  gradient?: string
  historyMinScore?: number
  historyMinRank?: number
  referenceYear?: number
  resubjectRequirement?: string
  subjectRequirementSource?: string
  latestPlanCount?: number
  planTrend?: string
  planRiskNote?: string
  planExpansionIndex?: number
  planExpansionLabel?: string
  planExpansionNote?: string
  schoolEnrollmentIndex?: number
  schoolEnrollmentLabel?: string
  schoolEnrollmentNote?: string
  rankGap?: number
  rankGapRatio?: number
  recommendationScore?: number
  precisionScore?: number
  precisionLabel?: string
  precisionNote?: string
  chanceScore?: number
  chanceLevel?: string
  /** 等渗校准后的参考概率。专业组省份已含退档折减。展示优先用这个。 */
  calibratedProbability?: number
  confidenceLevel?: string
  dataConfidence?: number
  predictedMinRank?: number
  rankDiff?: number
  riskLevel?: string
  riskColor?: string
  predictedRank?: number
  trend?: string
  matchScore?: number
  matchTag?: string
  schoolNature?: string
  dataSourceType?: string
  confidenceLabel?: string
  recommendReason?: string
  riskReason?: string
  alternativeOption?: string
  suitableFor?: string
  schoolOfficialUrl?: string
  admissionSiteUrl?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
  tuitionInfoUrl?: string
  requirementSourceUrl?: string
  requirementSourceYear?: number
  requirementSourceName?: string
  historyRecords?: RawHistoryRecord[]
  withinConfiguredRange?: boolean
  rangeNote?: string
  needsManualReview?: boolean
  reviewFlags?: string[]
  referenceFitLevel?: string
  dataConfidenceScore?: number
  algorithmExplanation?: string
  legacySubjectFallback?: boolean
  specialTypeFlag?: boolean
  excludedReason?: string
  recruitType?: string
}

/**
 * 列表层。字段全部必填，UI 不需要兜底。
 *
 * 按 ADR 的 A2 决策，对外只暴露两个数字：机会指数与数据可信度。
 * 其余评分（recommendationScore / matchScore / precisionScore）留在 detail 里作解释用。
 */
export interface VolunteerItemBrief {
  index: number
  universityName: string
  /** 贵州是专业名；专业组省份是专业组名 */
  displayName: string
  schoolId: string
  city: string
  tags: string[]
  gradient: Gradient
  /** 机会指数 0-100。对外必须使用「机会指数」这一合规称谓。 */
  chanceScore: number
  chanceLevel: string
  /** 数据可信度 0-100 */
  confidence: number
  confidenceLabel: string
  riskLevel: string
  riskColor: RiskColor
  historyMinScore: number
  historyMinRank: number
  referenceYear: number
  needsManualReview: boolean
  /** 是否用了新高考改革前的文理科数据兜底 */
  legacyFallback: boolean
}

export interface VolunteerItemDetail extends VolunteerItemBrief {
  raw: RawVolunteerItem
}

// ---------------------------------------------------------------------------
// 方案
// ---------------------------------------------------------------------------

export interface RawManualReviewItem {
  index?: number
  universityName?: string
  majorName?: string
  gradient?: string
  reasons?: string[]
  evidenceLinks?: string[]
  confidenceLabel?: string
  dataSourceType?: string
  subjectRequirementSource?: string
}

export interface ManualReviewItem {
  index: number
  universityName: string
  majorName: string
  gradient: string
  reasons: string[]
  evidenceLinks: string[]
}

export interface RawPlanMetrics {
  totalCount?: number
  chongCount?: number
  wenCount?: number
  baoCount?: number
  dianCount?: number
  manualReviewCount?: number
  officialRequirementCount?: number
  missingRequirementCount?: number
  lowConfidenceCount?: number
  legacyFallbackCount?: number
  avgPrecisionScore?: number
  portfolioSafetyProbability?: number
  portfolioSafetyLevel?: string
  portfolioSafetyNote?: string
  safeTailCount?: number
  targetCount?: number
  provinceCode?: string
  volunteerUnitType?: string
  generationCostMs?: number
  strategyMode?: string
  [key: string]: unknown
}

export interface RawVolunteerPlan {
  id?: number
  provinceCode?: string
  provinceName?: string
  volunteerUnitType?: string
  volunteerUnitLabel?: string
  targetBatch?: string
  targetCount?: number
  totalScore?: number
  provinceRank?: number
  firstSubject?: string
  resubjects?: string[]
  preferredMajors?: string[]
  preferredRegions?: string[]
  strategyMode?: string
  decisionPriority?: string
  careerGoal?: string
  tuitionBudget?: string
  acceptPrivate?: boolean
  acceptSinoForeign?: boolean
  safetyCode?: string
  accessKey?: string
  items?: RawVolunteerItem[]
  createdAt?: string
  dataQualityWarning?: string
  manualReviewItems?: RawManualReviewItem[]
  metrics?: RawPlanMetrics
  referenceProbabilityNotice?: string
  warnings?: string[]
  [key: string]: unknown
}

export interface VolunteerPlan {
  id: number
  provinceCode: string
  provinceName: string
  volunteerUnitType: VolunteerUnitType
  volunteerUnitLabel: string
  targetBatch: string
  targetCount: number
  totalScore: number
  provinceRank: number
  firstSubject: string
  resubjects: string[]
  strategyMode: StrategyMode
  /** 方案所有权凭证。必须本地持久化，丢失即无法找回方案。 */
  safetyCode: string
  accessKey: string
  items: VolunteerItemBrief[]
  createdAt: string
  dataQualityWarning: string
  manualReviewItems: ManualReviewItem[]
  metrics: RawPlanMetrics
  /** 免责口径全文，由后端下发，UI 必须原样完整展示 */
  referenceProbabilityNotice: string
  warnings: string[]
  /** 原始响应，详情页与调试用 */
  raw: RawVolunteerPlan
}

// ---------------------------------------------------------------------------
// 位次校验
// ---------------------------------------------------------------------------

export interface RawRankCheckResponse {
  provinceCode?: string
  provinceName?: string
  subjectType?: string
  referenceYear?: number
  rankLow?: number
  rankHigh?: number
  submittedRank?: number
  matched?: boolean
  officialDataReady?: boolean
  sourceName?: string
  sourceUrl?: string
  sourcePageUrl?: string
  parseMethod?: string
  note?: string
  /** 恒有值，UI 必须原样展示 */
  reminder?: string
}

// ---------------------------------------------------------------------------
// 表单
// ---------------------------------------------------------------------------

export interface GradientRange {
  rankOffsetMin: number
  rankOffsetMax: number
}

export type GradientRangeKey = 'chong' | 'wen' | 'bao' | 'dian'
export type GradientRanges = Record<GradientRangeKey, GradientRange>

export interface VolunteerFormData {
  provinceCode: ProvinceCode
  totalScore: number
  provinceRank: number
  firstSubject: FirstSubject
  resubjects: string[]
  preferredMajors: string[]
  preferredRegions: string[]
  strategyMode: StrategyMode
  decisionPriority: string
  careerGoal: string
  tuitionBudget: string
  acceptPrivate: boolean
  acceptSinoForeign: boolean
  agreedDisclaimer: boolean
  disclaimerVersion: string
  gradientRanges?: GradientRanges
}

/** 本地方案存档条目 */
export interface ArchivedPlan {
  planId: number
  safetyCode: string
  accessKey: string
  provinceCode: string
  provinceName: string
  totalScore: number
  provinceRank: number
  itemCount: number
  createdAt: string
  savedAt: number
}
