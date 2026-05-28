import http from './request'
import type { Result, PageResult, ScoreLine, SchoolScoreSummary } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

export type ScoreLineType =
  | 'control_line'
  | 'score_rank'
  | 'admission_line'
  | 'major_group_line'
  | 'major_score'
  | 'art_sport'

export interface ScoreLineCapabilityType {
  type: ScoreLineType | string
  label: string
  description?: string
  queryable: boolean
  dataStatus: 'AVAILABLE' | 'MISSING' | 'PARTIAL' | string
  missingReason?: string
  availableYears?: number[]
  sourceTables?: string[]
}

export interface ScoreLineCapability {
  provinceCode: ProvinceCode | string
  provinceName?: string
  officialSourceName?: string
  officialSourceUrl?: string
  policyMode?: string
  subjectMode?: string
  dataStatus?: string
  latestOfficialDataYear?: number
  targetYear?: number
  availableYears?: number[]
  subjectOptions?: string[]
  selectedSubjectOptions?: string[]
  notices?: string[]
  scoreLineTypes: ScoreLineCapabilityType[]
  missingReasonByType?: Record<string, string>
}

export interface ScoreLineQueryRow {
  id?: number
  provinceCode?: string
  provinceName?: string
  year?: number
  scoreLineType?: string
  batchCode?: string
  batchName?: string
  subjectCategory?: string
  schoolCode?: string
  schoolName?: string
  majorGroupCode?: string
  majorGroupName?: string
  majorName?: string
  minScore?: number
  minRank?: number
  sameScoreCount?: number
  score?: number
  cumulativeCount?: number
  rankLow?: number
  rankHigh?: number
  planCount?: number
  requiredSubjects?: string
  sourceFile?: string
  sourceUrl?: string
  sourcePage?: string
  rawText?: string
  dataStatus?: string
}

export interface ScoreLineQueryResponse {
  provinceCode: ProvinceCode | string
  provinceName?: string
  year: number
  scoreLineType: ScoreLineType | string
  subjectCategory?: string
  dataStatus: string
  missingReason?: string
  pageResult: PageResult<ScoreLineQueryRow>
}

export interface ScoreLineQueryParams {
  year?: number
  subjectCategory?: string
  schoolCode?: string
  schoolName?: string
  keyword?: string
  page?: number
  pageSize?: number
}

const SCORE_LINE_ENDPOINTS: Record<string, string> = {
  control_line: 'control-lines',
  score_rank: 'score-rank',
  admission_line: 'admission-lines',
  major_group_line: 'major-group-lines',
  major_score: 'major-score-lines',
  art_sport: 'art-sport-lines',
}

export function getScoreLineCapability(provinceCode: ProvinceCode) {
  return http.get<Result<ScoreLineCapability>>(`/score-lines/${provinceCode}/capability`)
}

export function queryProvinceScoreLines(provinceCode: ProvinceCode, type: ScoreLineType | string, params: ScoreLineQueryParams = {}) {
  const endpoint = SCORE_LINE_ENDPOINTS[type] || type
  return http.get<Result<ScoreLineQueryResponse>>(`/score-lines/${provinceCode}/${endpoint}`, { params })
}

/** 查询历年投档线 */
export function getScoreLineList(params: {
  provinceCode?: ProvinceCode
  year?: number
  subjectType?: string
  universityName?: string
  majorName?: string
  page?: number
  pageSize?: number
}) {
  return http.get<Result<PageResult<ScoreLine>>>('/score-line/list', { params })
}

/** 按院校聚合查询院校级投档线 */
export function getSchoolScoreLineList(params: {
  provinceCode?: ProvinceCode
  year?: number
  subjectType?: string
  universityName?: string
  page?: number
  pageSize?: number
}) {
  return http.get<Result<PageResult<SchoolScoreSummary>>>('/score-line/schools', { params })
}

/** 查询某所院校的历年院校级投档线 */
export function getSchoolScoreLineHistory(params: {
  provinceCode?: ProvinceCode
  schoolId: string
  groupCode?: string
  subjectType?: string
  maxRecords?: number
}) {
  return http.get<Result<ScoreLine[]>>('/score-line/school-history', { params })
}

/** 获取可选年份列表 */
export function getScoreLineYears(provinceCode: ProvinceCode = 'GZ') {
  return http.get<Result<number[]>>('/score-line/years', { params: { provinceCode } })
}

/** 热门专业TOP10 — 基于真实录取数据 */
export function getHotMajors(subjectType: string = '物理类', limit: number = 10) {
  return http.get<Result<HotMajor[]>>('/score-line/hot-majors', { params: { subjectType, limit } })
}

export interface HotMajor {
  rank: number
  name: string
  schoolCount: number
  avgScore: number
  avgRank: number
  heat: number
}

export { getScoreLineList as fetchScoreLines, getScoreLineYears as fetchAvailableYears }
