/** 与后端 com.gzly.common.Result<T> 对齐 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 与后端 com.gzly.common.PageResult<T> 对齐 */
export interface PageResult<T = unknown> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

/** 与 gzly-web types/index.ts 中 University 对齐（App 端仅取展示所需字段） */
export interface University {
  id: number
  schoolId?: string
  name: string
  province: string
  city: string
  typeName?: string
  natureName?: string
  belong?: string
  /** 办学层次标签：985/211/双一流/公办/民办 */
  tags: string[]
  logoUrl?: string
  schoolSite?: string
  phone?: string
  email?: string
  address?: string
  content?: string
  level?: string
  f985?: boolean
  f211?: boolean
  dualClass?: boolean
}

/** 院校官方链接证据链（取展示所需字段） */
export interface OfficialLink {
  schoolId: string
  schoolName?: string
  schoolSite?: string
  admissionSite?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
  tuitionInfoUrl?: string
  tuitionSummary?: string
  tuitionRemark?: string
  majorCatalogSummary?: string
  adjustmentRule?: string
  foreignLanguageRule?: string
  physicalExamRule?: string
  singleSubjectRule?: string
}

/* ---------------- 位次校验 ---------------- */
export interface RankCheckResponse {
  provinceCode?: string
  provinceName?: string
  subjectType?: string
  referenceYear?: number
  rankLow?: number
  rankHigh?: number
  officialDataReady?: boolean
  sourceName?: string
  sourceUrl?: string
  note?: string
  reminder?: string
}

/* ---------------- 志愿方案 ---------------- */
export interface VolunteerItem {
  index: number
  universityName: string
  schoolId?: string
  majorName: string
  province?: string
  city?: string
  tags?: string[]
  gradient?: string
  historyMinScore?: number
  historyMinRank?: number
  referenceYear?: number
  resubjectRequirement?: string
  chanceScore?: number
  chanceLevel?: string
  matchScore?: number
  dataSourceType?: string
  confidenceLabel?: string
  riskLevel?: string
  riskColor?: string
  recommendReason?: string
  riskReason?: string
  alternativeOption?: string
  suitableFor?: string
  needsManualReview?: boolean
  reasons?: string[]
  schoolOfficialUrl?: string
  admissionSiteUrl?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
}

export interface VolunteerPlan {
  id: number
  provinceCode?: string
  provinceName?: string
  volunteerUnitLabel?: string
  targetBatch?: string
  targetCount?: number
  totalScore?: number
  provinceRank?: number
  firstSubject?: string
  resubjects?: string[]
  strategyMode?: string
  safetyCode?: string
  accessKey?: string
  items: VolunteerItem[]
  manualReviewItems?: VolunteerItem[]
  createdAt?: string
  referenceProbabilityNotice?: string
  supportLevel?: string
  recommendMode?: string
  engineName?: string
  warnings?: string[]
  dataQualityWarning?: string
}

/** 志愿 AI 解读（JSON 结构化版，非 SSE） */
export interface AiAnalysisSection {
  title: string
  content: string
}

export interface AiAnalysisResponse {
  planId?: number
  status?: string
  conclusion?: string
  diagnosisSections?: AiAnalysisSection[]
  topKeepDirections?: string[]
  topRiskPoints?: string[]
  actionSteps?: AiAnalysisSection[]
  reorderAdvice?: string[]
  disclaimer?: string
  aiModelVersion?: string
  aiFallbackUsed?: boolean
  aiFallbackReason?: string
  dataIssues?: string[]
}

/* ---------------- 多省分数线 ---------------- */
export interface ScoreLineTypeMeta {
  type: string
  label: string
  description?: string
  available?: boolean | null
}

export interface ProvinceScoreLineCapability {
  provinceCode: string
  provinceName: string
  officialSourceName?: string
  officialSourceUrl?: string
  policyMode?: string
  subjectMode?: string
  dataStatus?: string
  latestOfficialDataYear?: number
  targetYear?: number
  availableYears: number[]
  subjectOptions: string[]
  selectedSubjectOptions?: string[]
  notices?: string[]
  scoreLineTypes: ScoreLineTypeMeta[]
  missingReasonByType?: Record<string, string>
}

export interface ScoreLineRecord {
  id?: number
  year?: number
  subjectCategory?: string
  batchName?: string
  /** 一分一段 */
  score?: number
  sameScoreCount?: number
  cumulativeCount?: number
  rankLow?: number
  rankHigh?: number
  /** 投档线 / 专业线 */
  schoolCode?: string
  schoolName?: string
  majorGroupCode?: string
  majorName?: string
  minScore?: number
  minRank?: number
  requiredSubjects?: string
  sourceUrl?: string
}

export interface ProvinceScoreLineQueryResult {
  provinceCode: string
  provinceName: string
  year?: number
  scoreLineType: string
  subjectCategory?: string
  dataStatus: string
  missingReason?: string
  pageResult?: PageResult<ScoreLineRecord>
}
