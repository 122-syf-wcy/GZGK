import http from './request'
import type { OfficialLink, Result, PageResult, University } from '@/types'

/** 查询院校列表 */
export function getUniversityList(params: {
  keyword?: string
  province?: string
  region?: string
  tag?: string
  page?: number
  pageSize?: number
}) {
  return http.get<Result<PageResult<University>>>('/university/list', { params })
}

/** 查询院校详情 */
export function getUniversityDetail(id: number) {
  return http.get<Result<University>>(`/university/${id}`)
}

export function getUniversityBySchoolId(schoolId: string) {
  return http.get<Result<University>>('/university/by-school-id', {
    params: { schoolId },
  })
}

export function getUniversityOfficialLinks(schoolId: string) {
  return http.get<Result<OfficialLink | null>>('/university/official-links', {
    params: { schoolId },
  })
}

export {
  getUniversityList as fetchUniversities,
  getUniversityDetail as fetchUniversityDetail,
  getUniversityBySchoolId as fetchUniversityBySchoolId,
  getUniversityOfficialLinks as fetchUniversityOfficialLinks,
}
