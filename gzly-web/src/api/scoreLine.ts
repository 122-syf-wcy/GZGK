import http from './request'
import type { Result, PageResult, ScoreLine, SchoolScoreSummary } from '@/types'
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
