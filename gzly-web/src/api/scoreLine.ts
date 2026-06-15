import http from './request'
import type {
  Result,
  PageResult,
  ScoreLine,
  SchoolScoreSummary,
  ProvinceScoreLineCapability,
  ProvinceScoreLineQueryResult,
} from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

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

/** 获取省份分数线查询能力矩阵 */
export function getProvinceScoreLineCapability(provinceCode: ProvinceCode) {
  return http.get<Result<ProvinceScoreLineCapability>>(`/score-lines/${provinceCode.toLowerCase()}/capability`)
}

export interface ProvinceScoreLineQueryParams {
  year?: number
  batchCode?: string
  subjectCategory?: string
  subjectType?: string
  selectedSubjects?: string
  schoolCode?: string
  schoolName?: string
  majorGroupCode?: string
  majorName?: string
  score?: number
  page?: number
  pageSize?: number
}

function scoreLineEndpoint(type: string): string {
  if (type === 'control_line') return 'control-lines'
  if (type === 'score_rank') return 'score-rank'
  if (type === 'major_group_line') return 'major-group-lines'
  if (type === 'major_score') return 'major-score-lines'
  if (type === 'art_sport') return 'art-sport-lines'
  return 'admission-lines'
}

/** 按省份 adapter 查询分数线，不跨省回退 */
export function queryProvinceScoreLines(
  provinceCode: ProvinceCode,
  type: string,
  params: ProvinceScoreLineQueryParams,
) {
  return http.get<Result<ProvinceScoreLineQueryResult>>(
    `/score-lines/${provinceCode.toLowerCase()}/${scoreLineEndpoint(type)}`,
    { params },
  )
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
