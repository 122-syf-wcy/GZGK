/** 统一响应格式 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

/** 大学信息 */
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
  qaDisabled?: number
  qaDisabledReason?: string
  qaDisabledUntil?: string
}

export interface OfficialLink {
  id?: number
  schoolId: string
  schoolName?: string
  sourceDomain?: string
  schoolSite?: string
  admissionSite?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
  tuitionInfoUrl?: string
  tuitionRemark?: string
  tuitionSummary?: string
  majorCatalogSummary?: string
  adjustmentRule?: string
  foreignLanguageRule?: string
  physicalExamRule?: string
  singleSubjectRule?: string
  parserNotes?: string
  captureMethod?: string
  captureStatus?: number
  parseStatus?: number
  lastVerifiedAt?: string
  lastCapturedAt?: string
  lastParsedAt?: string
}

export interface AdminOfficialLinkItem {
  id: number
  schoolId: string
  name: string
  province: string
  city: string
  tags: string[]
  schoolSite?: string
  natureName?: string
  officialLink?: OfficialLink | null
  planHitCount: number
  hotScore: number
  gapCount: number
  missingFields: string[]
  priorityLevel: 'P0' | 'P1' | 'P2'
  priorityReasons: string[]
}

export interface AdminOfficialLinkStats {
  totalUniversities: number
  linkedCount: number
  pendingCount: number
  emptyCount: number
  brochureCount: number
  majorCatalogCount: number
  tuitionCount: number
  tuitionSummaryCount: number
  majorSummaryCount: number
  parsedCount: number
  adjustmentCount: number
  foreignRuleCount: number
  physicalRuleCount: number
  singleSubjectCount: number
  activeWindowDays: number
  recentPlanCount: number
  fallbackTriggered: boolean
  priorityQueueCount: number
  priorityP0Count: number
  priorityP1Count: number
  priorityP2Count: number
}

export interface AdminOfficialLinkListData extends PageResult<AdminOfficialLinkItem> {
  stats: AdminOfficialLinkStats
}

export interface Announcement {
  id?: number
  title: string
  contentMd: string
  status?: number
  popupEnabled?: number
  sortOrder?: number
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface SpecialAdmissionCategory {
  category: string
  categoryName: string
  count: number
}

export interface SpecialAdmissionPolicy {
  id: number
  year: number
  category: string
  categoryName: string
  title: string
  summary: string
  contentMd: string
  officialUrl?: string
  sourceName?: string
  sourceType?: 'official' | 'wechat' | 'reprint' | string
  applyStart?: string
  applyEnd?: string
  examTime?: string
  targetStudents?: string
  requirements?: string
  publishedAt?: string
}

export interface AdminAiConfig {
  id?: number
  providerName: string
  baseUrl: string
  chatModel: string
  reviewModel: string
  visionModel: string
  maxTokens: number
  temperature: number
  systemPrompt: string
  enabled: boolean
  hasApiKey: boolean
  apiKeyMasked?: string
  configSource?: 'database' | 'environment' | string
  updatedAt?: string
}

export interface SaveAdminAiConfigRequest {
  providerName: string
  baseUrl: string
  apiKey?: string
  chatModel: string
  reviewModel: string
  visionModel: string
  maxTokens: number
  temperature: number
  systemPrompt: string
  enabled: boolean
}

export interface AdminAiConfigTestResult {
  success: boolean
  message: string
  latencyMs?: number
  model?: string
}

export interface AdminAiModelListResult {
  success: boolean
  message: string
  models: string[]
}

export interface FeedbackItem {
  id: number
  content: string
  sourcePage?: string
  status: number
  ipHash?: string
  createdAt: string
  updatedAt?: string
  readAt?: string
}

export interface EncouragementMessage {
  id: number
  nickname: string
  content: string
  createdAt: string
}

export interface AdminEncouragementMessage extends EncouragementMessage {
  status: number
  updatedAt?: string
}

export interface AdminEncouragementMessageListData {
  items: AdminEncouragementMessage[]
  total: number
  page: number
  pageSize: number
  visibleCount: number
  hiddenCount: number
}

/** 历年投档线 */
export interface ScoreLine {
  id: number
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  schoolId?: string
  groupCode?: string
  groupName?: string
  year: number
  /** 首选物理 | 首选历史 */
  subjectType: string
  universityName: string
  majorName: string
  batch?: string
  minScore: number
  /** 最低位次（核心测算依据） */
  minRank: number
  /** 再选科目要求 */
  resubjectRequirement: string
  /** 数据来源：专业级优先，院校级为回退 */
  dataSourceType?: '专业级' | '院校级' | '院校专业组'
  confidenceLabel?: '高可信' | '中可信' | '需复核'
  /** 位次来源：原始录取位次 / 一分一段换算 / 缺失 */
  rankSourceType?: 'original' | 'score_rank_converted' | 'missing' | string
  rankSourceNote?: string
  rankLow?: number
  rankHigh?: number
  rankSourceUrl?: string
  rankSourcePageUrl?: string
  sourceUrl?: string
  sourcePageUrl?: string
}

/** 院校级分数线聚合卡片 */
export interface SchoolScoreSummary {
  id: number
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  volunteerUnitType?: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45' | string
  schoolId: string
  universityName: string
  groupCode?: string
  groupName?: string
  subjectType: string
  latestYear: number
  minScore: number
  minRank: number
  batch?: string
  availableYearCount: number
  firstYear?: number
  lastYear?: number
  dataSourceType?: '院校级' | '院校专业组'
  confidenceLabel?: '中可信' | '需复核'
}

export type GradientKey = '冲' | '稳' | '保' | '垫'
export type GradientRangeKey = 'chong' | 'wen' | 'bao' | 'dian'

export interface GradientRangeInput {
  rankOffsetMin: number
  rankOffsetMax: number
}

export type GradientRanges = Record<GradientRangeKey, GradientRangeInput>

export interface GradientRangeDetail {
  gradient: GradientKey
  rankOffsetMin: number
  rankOffsetMax: number
  rankLow: number
  rankHigh: number
  targetCount: number
  actualCount: number
  label: string
  rankRatioMin?: number
  rankRatioMax?: number
  rangeSourceNote?: string
}

export interface GradientRangeSummary {
  source: 'preset' | 'custom' | string
  strategyMode: VolunteerFormData['strategyMode']
  explanation: string
  ranges: Partial<Record<GradientKey, GradientRangeDetail>>
}

export interface HistoryRecord {
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  groupCode?: string
  year?: number
  minScore?: number
  minRank?: number
  avgScore?: number
  maxScore?: number
  planCount?: number
  batch?: string
  subjectType?: string
  dataSourceType?: '专业级' | '院校级' | '院校专业组'
  confidenceLabel?: '高可信' | '中可信' | '需复核'
  rankSourceType?: 'original' | 'score_rank_converted' | 'missing' | string
  rankSourceNote?: string
  rankLow?: number
  rankHigh?: number
  rankSourceUrl?: string
  rankSourcePageUrl?: string
}

/** 志愿表单 */
export interface VolunteerFormData {
  /** 省份代码：GZ=贵州，SC=四川，HB=湖北，AH=安徽 */
  provinceCode: 'GZ' | 'SC' | 'HB' | 'AH'
  totalScore: number
  provinceRank: number
  /** 首选科目：物理 | 历史 */
  firstSubject: '物理' | '历史'
  /** 再选科目（最多2门） */
  resubjects: string[]
  /** 意向专业 */
  preferredMajors: string[]
  /** 意向地区 */
  preferredRegions: string[]
  /** 方案取向 */
  strategyMode: '保守型' | '均衡型' | '冲刺型'
  /** 决策优先级 */
  decisionPriority: '学校优先' | '专业优先'
  /** 长期目标 */
  careerGoal: '就业优先' | '升学优先' | '城市机会优先'
  /** 预算偏好 */
  tuitionBudget: '低预算' | '均衡预算' | '不限制'
  /** 是否接受民办 */
  acceptPrivate: boolean
  /** 是否接受中外合作 / 港澳台合作办学 */
  acceptSinoForeign: boolean
  agreedDisclaimer: boolean
  disclaimerVersion: string
  gradientRanges?: GradientRanges
}

/** 单个志愿项 */
export interface VolunteerItem {
  index: number
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  volunteerUnitType?: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45' | string
  volunteerUnitLabel?: string
  universityName: string
  schoolId?: string
  groupCode?: string
  groupName?: string
  groupMajors?: string[]
  obeyAdjustment?: boolean
  majorName: string
  province: string
  city: string
  tags: string[]
  schoolNature?: string
  /** 冲 | 稳 | 保 | 垫 */
  gradient: GradientKey
  /** 历年最低分 */
  historyMinScore: number
  /** 历年最低位次 */
  historyMinRank: number
  /** 参考年份 */
  referenceYear: number
  /** 再选科目要求 */
  resubjectRequirement: string
  /** 选科要求来源: official_requirement / score_line / inferred / missing */
  subjectRequirementSource?: string
  /** 最近一年可用招生计划数 */
  latestPlanCount?: number
  /** 招生计划变化趋势 */
  planTrend?: '扩招' | '缩招' | '基本稳定' | '单年计划' | '计划数暂缺' | string
  /** 招生计划变化解释 */
  planRiskNote?: string
  /** 当年计划相对近年基准的扩招指数，100=稳定 */
  planExpansionIndex?: number
  planExpansionLabel?: string
  planExpansionNote?: string
  /** 院校/志愿单位招生供给指数，0-100 */
  schoolEnrollmentIndex?: number
  schoolEnrollmentLabel?: string
  schoolEnrollmentNote?: string
  /** 历史最低位次 - 考生位次，正数表示历史最低位次更宽松 */
  rankGap?: number
  /** 位次差占考生位次比例，百分比 */
  rankGapRatio?: number
  /** 综合推荐分，仅用于排序解释 */
  recommendationScore?: number
  /** 推荐精度分：位次、计划、供给、数据置信度综合 */
  precisionScore?: number
  precisionLabel?: '精度较高' | '精度中等' | '精度偏低' | '必须复核' | string
  precisionNote?: string
  /** 用户可见机会指数 0-100，仅作辅助参考 */
  chanceScore?: number
  /** 机会等级：冲刺参考 / 适中 / 稳妥参考 / 兜底参考 */
  chanceLevel?: string
  /** 数据参考度等级：高 / 中 / 低 / 数据不足 */
  confidenceLevel?: string
  /** 数据参考度 0-100 */
  dataConfidence?: number
  /** 预测参考位次 */
  predictedMinRank?: number
  /** 预测参考位次 - 考生位次 */
  rankDiff?: number
  /** 兼容旧方案本地缓存，后端新版公共响应不再返回 */
  admissionProb?: number
  /** 兼容旧方案本地缓存，后端新版公共响应不再返回 */
  probLevel?: string
  /** 风险等级: 低风险/中风险/高风险 */
  riskLevel?: string
  /** 风险颜色: green/yellow/red */
  riskColor?: string
  /** 预测下一年位次 */
  predictedRank?: number
  /** 趋势: 竞争加剧/基本稳定/竞争缓和 */
  trend?: string
  /** 意向匹配度 0-100 */
  matchScore?: number
  /** 匹配标签: 专业匹配/地区匹配/双匹配 */
  matchTag?: string
  /** 数据来源: 专业级 / 院校级 */
  dataSourceType?: '专业级' | '院校级' | '院校专业组'
  /** 数据可信度标签 */
  confidenceLabel?: '高可信' | '中可信' | '需复核'
  /** 推荐原因 */
  recommendReason?: string
  /** 风险原因 */
  riskReason?: string
  /** 替代建议 */
  alternativeOption?: string
  /** 更适合哪类考生 */
  suitableFor?: string
  /** ── 数据可信度证据链（v6.79 新增） ── */
  schoolOfficialUrl?: string
  admissionSiteUrl?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
  tuitionInfoUrl?: string
  requirementSourceUrl?: string
  requirementSourceYear?: number
  requirementSourceName?: string
  /** 近三次可用录取记录，专业级优先，缺失时回退院校级 */
  historyRecords?: HistoryRecord[]
  /** 是否位于本次配置的梯度区间内 */
  withinConfiguredRange?: boolean
  /** 梯度区间解释 */
  rangeNote?: string
  /** 是否需要人工复核 */
  needsManualReview?: boolean
  /** 触发人工复核的原因标签 */
  reviewFlags?: string[]
  /** 参考匹配等级：较高 / 中等 / 偏低 / 需复核 */
  referenceFitLevel?: '较高' | '中等' | '偏低' | '需复核' | string
  /** 数据置信度分：0-100，仅表示数据完整度和可解释性 */
  dataConfidenceScore?: number
  /** 本条推荐的算法解释 */
  algorithmExplanation?: string
  /** 是否使用旧文理科历史数据作为兼容参考 */
  legacySubjectFallback?: boolean
  /** 是否疑似专项、军警、艺术体育等特殊招生类型 */
  specialTypeFlag?: boolean
  /** 被排除或需复核的特殊类型原因 */
  excludedReason?: string
}

/** 强制人工复核清单条目 */
export interface ManualReviewItem {
  index: number
  universityName: string
  majorName: string
  gradient: string
  reasons: string[]
  evidenceLinks?: string[]
  confidenceLabel?: string
  dataSourceType?: string
  subjectRequirementSource?: string
}

/** 监控指标（同时供前端展示与运维消费） */
export interface PlanMetrics {
  totalCount: number
  chongCount: number
  wenCount: number
  baoCount: number
  dianCount: number
  missingRequirementCount: number
  nonMajorLevelCount: number
  manualReviewCount: number
  legacyFallbackCount?: number
  specialExcludedCount?: number
  lowConfidenceCount?: number
  officialRequirementCount?: number
  expandedPlanCount?: number
  shrunkPlanCount?: number
  missingPlanIndexCount?: number
  highSupplyCount?: number
  lowSupplyCount?: number
  avgPrecisionScore?: number
  portfolioSafetyProbability?: number
  portfolioSafetyLevel?: string
  portfolioSafetyNote?: string
  safeTailCount?: number
  targetCount?: number
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  volunteerUnitType?: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45' | string
  generationCostMs?: number
  generatedAtMs?: number
  // === 算法报告 P1 评估指标（V7.38 新增） ===
  /** 主列表中被归入非普通招生类型的条数。正常生成应恒为 0，>0 表示规则前置被绕过。 */
  ruleViolationCount?: number
  /** ruleViolationCount / totalCount，对应报告中的 Rule Violation Rate。 */
  ruleViolationRate?: number
  /** 前 20 志愿中机会指数 < 35 的占比，对应报告中的 Over-Risk Exposure。 */
  overRiskExposure?: number
  /** 前 20 志愿中机会指数 ≥ 75 的占比，作为 First-20 Hit Rate 的代理指标。 */
  firstTwentyHitRate?: number
  /** 主列表按 recruit_type 归一后的条数分布，键为 RecruitTypeClassifier 常量。 */
  recruitTypeBreakdown?: Record<string, number>
  /** 当前策略模式（保守型 / 均衡型 / 冲刺型）。 */
  strategyMode?: string
  /** overRiskExposure 的策略自适应基线（含 5% 容忍）。 */
  overRiskExposureBaseline?: number
  /** firstTwentyHitRate 的策略自适应基线（含 5% 容忍）。 */
  firstTwentyHitRateBaseline?: number
  /** overRiskExposure 是否突破当前策略基线。 */
  overRiskExposureBreached?: boolean
  /** firstTwentyHitRate 是否低于当前策略基线。 */
  firstTwentyHitRateBreached?: boolean
}

/** 报考顾问建议 */
export interface AdvisorAdvice {
  title?: string
  positioning?: string
  priorityAdvice?: string
  gradientAdvice?: string
  cityAdvice?: string
  majorAdvice?: string
  planChangeAdvice?: string
  riskChecklist?: string[]
  actionItems?: string[]
  sourceNote?: string
  sourceProjectName?: string
  sourceProjectUrl?: string
}

/** 手填位次校验响应 */
export interface RankCheckResponse {
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  provinceName?: string
  subjectType: string
  referenceYear?: number
  rankLow?: number
  rankHigh?: number
  submittedRank?: number
  matched?: boolean
  officialDataReady: boolean
  sourceName?: string
  sourceUrl?: string
  sourcePageUrl?: string
  parseMethod?: string
  note?: string
  reminder?: string
}

/** 生成时采用的分数→位次估算摘要 */
export interface RankEstimateSummary extends RankCheckResponse {
  totalScore: number
  effectiveRank?: number
  estimatedRank?: number
  rankEstimated: boolean
}

/** 志愿方案 */
export interface VolunteerPlan {
  id: number
  provinceCode?: 'GZ' | 'SC' | 'HB' | 'AH' | string
  provinceName?: string
  volunteerUnitType?: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45' | string
  volunteerUnitLabel?: string
  targetBatch?: string
  targetCount?: number
  totalScore: number
  provinceRank: number
  firstSubject: string
  resubjects: string[]
  preferredMajors?: string[]
  preferredRegions?: string[]
  strategyMode?: VolunteerFormData['strategyMode']
  decisionPriority?: VolunteerFormData['decisionPriority']
  careerGoal?: VolunteerFormData['careerGoal']
  tuitionBudget?: VolunteerFormData['tuitionBudget']
  acceptPrivate?: boolean
  acceptSinoForeign?: boolean
  safetyCode?: string
  accessKey?: string
  items: VolunteerItem[]
  createdAt: string
  dataQualityWarning?: string
  manualReviewItems?: ManualReviewItem[]
  metrics?: PlanMetrics
  referenceProbabilityNotice?: string
  gradientRangeSummary?: GradientRangeSummary
  rankEstimate?: RankEstimateSummary
  advisorAdvice?: AdvisorAdvice
  policy?: {
    id?: number
    province?: string
    year?: number
    candidateType?: string
    batchCode?: string
    batchName?: string
    volunteerMode?: string
    maxVolunteerCount?: number
    policyStatus?: 'confirmed' | 'draft' | 'pending_confirm' | string
    officialSourceTitle?: string
    officialSourceUrl?: string
  }
  modelInfo?: {
    modelVersion?: string
    fallbackUsed?: boolean
    visibleMetric?: string
    appliedCount?: number
    fallbackReason?: string
  }
  warnings?: string[]
}

export type RecommendationPhase =
  | 'PRE_OFFICIAL_DATA'
  | 'OFFICIAL_DATA_PARTIAL'
  | 'OFFICIAL_DATA_IMPORTED'
  | 'MODEL_RETRAINED'
  | string

export interface DataYearReadiness {
  provinceCode?: string
  year?: number
  policyReady: boolean
  scoreSegmentReady: boolean
  admissionPlanReady: boolean
  majorRequirementReady: boolean
  majorMetaReady: boolean
  mlTrainingReady: boolean
  historicalTrainingReady: boolean
  recommendationPhase: RecommendationPhase
  latestImportBatchId?: string
  lastCheckedAt?: string
  remarks?: string
}

/** 管理员只读 readiness DTO（GET /admin/data-year-readiness） */
export interface DataYearReadinessDto {
  provinceCode: string
  year: number
  activeAdmissionYear: number
  latestOfficialDataYear: number
  trainingYears: number[]
  dataSourceYears: number[]
  recommendationPhase: RecommendationPhase
  officialDataReady: boolean
  modelRetrained: boolean
  estimateMode: boolean
  dataReadiness: DataYearReadiness
  phaseDescription: string
  nextActions: string[]
}

/** 推荐院校项 */
export interface RecommendItem {
  schoolId: string
  universityName: string
  majorName: string
  similarity: number
  latestMinRank: number
  latestMinScore: number
  latestYear: number
  reason: string
}

/** AI 分析消息 */
export interface AiMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}
