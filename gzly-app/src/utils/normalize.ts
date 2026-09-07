/**
 * 线上 JSON → UI 模型的归一化。
 *
 * 后端配置了 `default-property-inclusion: non_null`，值为 null 的字段在响应里
 * 直接消失，因此任何字段都可能"不存在"。这一层负责把不确定性一次性吃掉，
 * 让页面拿到的对象字段全部有值。
 *
 * 原则：缺字段时给保守默认值，绝不猜测。数字缺失给 0 并由 UI 显示"暂无"，
 * 不给一个看起来合理但实际是编造的值。
 */

import type {
  BatchSupport,
  BatchSupportItem,
  Gradient,
  ManualReviewItem,
  RawBatchSupportItem,
  RawBatchSupportResponse,
  RawManualReviewItem,
  RawVolunteerItem,
  RawVolunteerPlan,
  ReadinessLevel,
  RiskColor,
  StrategyMode,
  VolunteerItemBrief,
  VolunteerItemDetail,
  VolunteerPlan,
  VolunteerUnitType,
} from '@/types'

function str(value: unknown, fallback = ''): string {
  return typeof value === 'string' && value.length > 0 ? value : fallback
}

function num(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function bool(value: unknown, fallback = false): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function list<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : []
}

const GRADIENTS: Gradient[] = ['冲', '稳', '保', '垫']
function gradientOf(value: unknown): Gradient {
  return GRADIENTS.includes(value as Gradient) ? (value as Gradient) : '稳'
}

const RISK_COLORS: RiskColor[] = ['green', 'yellow', 'red']
function riskColorOf(value: unknown): RiskColor {
  return RISK_COLORS.includes(value as RiskColor) ? (value as RiskColor) : 'yellow'
}

/**
 * 机会指数取值优先级：校准值 > 原始值。
 *
 * 按 ADR 的 A2，对外展示应使用等渗校准后的概率——只有校准过的数值才具备
 * "62 分大致对应 62% 历史达线频率"的可解释性。后端未下发校准值时退回原始值。
 */
function resolveChance(raw: RawVolunteerItem): number {
  const calibrated = num(raw.calibratedProbability, -1)
  if (calibrated >= 0) return Math.round(calibrated)
  return Math.round(num(raw.chanceScore))
}

/**
 * 数据可信度合并为单一维度。
 * 后端并存 dataConfidenceScore / dataConfidence / confidenceLabel 三套口径。
 */
function resolveConfidence(raw: RawVolunteerItem): number {
  const score = num(raw.dataConfidenceScore, -1)
  if (score >= 0) return Math.round(score)
  const legacy = num(raw.dataConfidence, -1)
  if (legacy >= 0) return Math.round(legacy)
  if (raw.confidenceLabel === '高可信') return 85
  if (raw.confidenceLabel === '中可信') return 65
  return 45
}

/** 贵州展示专业名，专业组省份展示专业组名 */
function resolveDisplayName(raw: RawVolunteerItem): string {
  const major = str(raw.majorName)
  if (major) return major
  const group = str(raw.groupName)
  if (group) return group
  const code = str(raw.groupCode)
  return code ? `专业组 ${code}` : '未命名志愿'
}

export function normalizeVolunteerItem(raw: RawVolunteerItem, fallbackIndex: number): VolunteerItemDetail {
  return {
    index: num(raw.index, fallbackIndex),
    universityName: str(raw.universityName, '未知院校'),
    displayName: resolveDisplayName(raw),
    schoolId: str(raw.schoolId),
    city: str(raw.city) || str(raw.province),
    tags: list<string>(raw.tags),
    gradient: gradientOf(raw.gradient),
    chanceScore: resolveChance(raw),
    chanceLevel: str(raw.chanceLevel, '数据不足'),
    confidence: resolveConfidence(raw),
    confidenceLabel: str(raw.confidenceLabel, '需复核'),
    riskLevel: str(raw.riskLevel, '需复核'),
    riskColor: riskColorOf(raw.riskColor),
    historyMinScore: num(raw.historyMinScore),
    historyMinRank: num(raw.historyMinRank),
    referenceYear: num(raw.referenceYear),
    needsManualReview: bool(raw.needsManualReview),
    legacyFallback: bool(raw.legacySubjectFallback),
    raw,
  }
}

export function toBrief(detail: VolunteerItemDetail): VolunteerItemBrief {
  const { raw, ...brief } = detail
  return brief
}

function normalizeManualReview(raw: RawManualReviewItem, fallbackIndex: number): ManualReviewItem {
  return {
    index: num(raw.index, fallbackIndex),
    universityName: str(raw.universityName, '未知院校'),
    majorName: str(raw.majorName),
    gradient: str(raw.gradient),
    reasons: list<string>(raw.reasons),
    evidenceLinks: list<string>(raw.evidenceLinks).filter(url => /^https?:\/\//i.test(url)),
  }
}

const STRATEGY_MODES: StrategyMode[] = ['保守型', '均衡型', '冲刺型']

export function normalizePlan(raw: RawVolunteerPlan): VolunteerPlan {
  const items = list<RawVolunteerItem>(raw.items).map((item, idx) => normalizeVolunteerItem(item, idx + 1))
  const unitType = raw.volunteerUnitType === 'PROFESSIONAL_GROUP_45'
    ? 'PROFESSIONAL_GROUP_45'
    : 'MAJOR_96'

  return {
    id: num(raw.id),
    provinceCode: str(raw.provinceCode, 'GZ'),
    provinceName: str(raw.provinceName, '贵州'),
    volunteerUnitType: unitType as VolunteerUnitType,
    volunteerUnitLabel: str(raw.volunteerUnitLabel, unitType === 'MAJOR_96' ? '专业（类）+ 院校' : '院校专业组'),
    targetBatch: str(raw.targetBatch),
    targetCount: num(raw.targetCount, items.length),
    totalScore: num(raw.totalScore),
    provinceRank: num(raw.provinceRank),
    firstSubject: str(raw.firstSubject),
    resubjects: list<string>(raw.resubjects),
    strategyMode: STRATEGY_MODES.includes(raw.strategyMode as StrategyMode)
      ? (raw.strategyMode as StrategyMode)
      : '均衡型',
    safetyCode: str(raw.safetyCode),
    accessKey: str(raw.accessKey),
    items: items.map(toBrief),
    createdAt: str(raw.createdAt),
    dataQualityWarning: str(raw.dataQualityWarning),
    manualReviewItems: list<RawManualReviewItem>(raw.manualReviewItems)
      .map((m, idx) => normalizeManualReview(m, idx + 1)),
    metrics: raw.metrics || {},
    referenceProbabilityNotice: str(raw.referenceProbabilityNotice),
    warnings: list<string>(raw.warnings),
    raw,
  }
}

const READINESS: ReadinessLevel[] = ['LOCKED', 'QUERY_ONLY', 'ESTIMATE', 'FULL']

function normalizeBatchItem(raw: RawBatchSupportItem): BatchSupportItem {
  return {
    batchCode: str(raw.batchCode),
    batchName: str(raw.batchName, '未命名批次'),
    candidateType: str(raw.candidateType, '普通类'),
    supportLevel: (raw.supportLevel || 'QUERY_ONLY') as BatchSupportItem['supportLevel'],
    targetCount: num(raw.targetCount, num(raw.maxVolunteerCount)),
    volunteerMode: str(raw.volunteerMode),
    policyStatus: str(raw.policyStatus, 'unconfigured'),
    officialSourceTitle: str(raw.officialSourceTitle),
    officialSourceUrl: str(raw.officialSourceUrl),
    generatorReady: bool(raw.generatorReady),
    supportReason: str(raw.supportReason),
    missingData: list<string>(raw.missingData),
  }
}

export function normalizeBatchSupport(raw: RawBatchSupportResponse, provinceCode: string): BatchSupport {
  const items = list<RawBatchSupportItem>(raw.items).map(normalizeBatchItem)
  return {
    provinceCode: str(raw.provinceCode, provinceCode),
    provinceName: str(raw.provinceName),
    year: num(raw.year, num(raw.targetYear)),
    latestOfficialDataYear: num(raw.latestOfficialDataYear),
    dataSourceYears: list<number>(raw.dataSourceYears),
    readinessLevel: READINESS.includes(raw.readinessLevel as ReadinessLevel)
      ? (raw.readinessLevel as ReadinessLevel)
      : 'LOCKED',
    items,
    warnings: list<string>(raw.warnings),
    anyGeneratorReady: items.some(item => item.generatorReady),
  }
}
