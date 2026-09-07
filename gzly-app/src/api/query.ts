/** 查询类接口：院校、分数线、特殊类型招生、内容。 */

import { http } from '@/utils/request'
import type { PageResult } from '@/types'

export interface UniversityBrief {
  id: number
  schoolId: string
  name: string
  province: string
  city: string
  typeName?: string
  natureName?: string
  tags?: string[]
  f985?: number
  f211?: number
}

export interface UniversityDetail extends UniversityBrief {
  belong?: string
  schoolSite?: string
  address?: string
  content?: string
}

export interface OfficialLinks {
  schoolId?: string
  officialUrl?: string
  admissionSiteUrl?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
  tuitionInfoUrl?: string
}

export async function searchUniversities(params: {
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<UniversityBrief>> {
  const res = await http.get<PageResult<UniversityBrief>>('/university/list', { params })
  return res.data.data || { total: 0, page: 1, pageSize: 20, records: [] }
}

export async function getUniversity(id: number): Promise<UniversityDetail | undefined> {
  const res = await http.get<UniversityDetail>(`/university/${id}`)
  return res.data.data
}

export async function getOfficialLinks(schoolId: string): Promise<OfficialLinks> {
  const res = await http.get<OfficialLinks>('/university/official-links', { params: { schoolId } })
  return res.data.data || {}
}

export interface SchoolScoreRow {
  schoolId: string
  universityName: string
  city?: string
  tags?: string[]
  year?: number
  subjectType?: string
  minScore?: number
  minRank?: number
  yearCount?: number
}

export async function getScoreLineYears(provinceCode: string): Promise<number[]> {
  const res = await http.get<number[]>('/score-line/years', { params: { provinceCode } })
  return res.data.data || []
}

export async function getScoreLineSchools(params: {
  provinceCode: string
  year?: number
  subjectType?: string
  universityName?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<SchoolScoreRow>> {
  const res = await http.get<PageResult<SchoolScoreRow>>('/score-line/schools', { params })
  return res.data.data || { total: 0, page: 1, pageSize: 20, records: [] }
}

export async function getSchoolHistory(params: {
  provinceCode: string
  schoolId: string
  subjectType?: string
}): Promise<Array<SchoolScoreRow & { batch?: string; rankSourceType?: string }>> {
  const res = await http.get<Array<SchoolScoreRow & { batch?: string; rankSourceType?: string }>>(
    '/score-line/school-history',
    { params },
  )
  return res.data.data || []
}

export interface HotMajor {
  name: string
  schoolCount?: number
  avgScore?: number
  avgRank?: number
  heat?: number
}

export async function getHotMajors(subjectType = '物理类', limit = 12): Promise<HotMajor[]> {
  const res = await http.get<HotMajor[]>('/score-line/hot-majors', { params: { subjectType, limit } })
  return res.data.data || []
}

export interface SpecialCategory {
  category: string
  categoryName: string
  count: number
}

export interface SpecialPolicy {
  id: string | number
  year?: number
  category?: string
  categoryName?: string
  title?: string
  summary?: string
  officialUrl?: string
  sourceName?: string
  applyStart?: string
  applyEnd?: string
  publishedAt?: string
}

export async function getSpecialCategories(year = 2026): Promise<SpecialCategory[]> {
  const res = await http.get<SpecialCategory[]>('/special-admissions/categories', { params: { year } })
  return res.data.data || []
}

export async function getSpecialPolicies(params: { year?: number; category?: string }): Promise<SpecialPolicy[]> {
  const res = await http.get<SpecialPolicy[]>('/special-admissions/policies', { params })
  return res.data.data || []
}

export interface Announcement {
  id?: number
  title?: string
  content?: string
  publishedAt?: string
  updatedAt?: string
}

export async function getAnnouncement(): Promise<Announcement | undefined> {
  const res = await http.get<Announcement | undefined>('/announcement/current')
  return res.data.data
}

export interface OnlineStats {
  activeUsers?: number
  totalViews?: number
  todayViews?: number
}

export async function getOnlineStats(): Promise<OnlineStats> {
  const res = await http.get<OnlineStats>('/site-stats/online')
  return res.data.data || {}
}

export interface EncouragementMessage {
  id: number
  nickname: string
  content: string
  createdAt: string
}

export async function listEncouragement(size = 20): Promise<EncouragementMessage[]> {
  const res = await http.get<EncouragementMessage[]>('/encouragement-messages', { params: { size } })
  return res.data.data || []
}

export async function postEncouragement(nickname: string, content: string): Promise<EncouragementMessage> {
  const res = await http.post<EncouragementMessage>('/encouragement-messages', { nickname, content })
  return res.data.data
}

export async function submitFeedback(content: string, sourcePage: string): Promise<void> {
  await http.post('/feedback', { content, sourcePage })
}
