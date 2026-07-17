import { httpGet } from '@/utils/request'
import type { PageResult, University, OfficialLink } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

/** 院校列表查询（解包后直接返回 PageResult） */
export function getUniversityList(params: {
  provinceCode?: ProvinceCode
  keyword?: string
  page?: number
  pageSize?: number
}) {
  return httpGet<PageResult<University>>('/university/list', params)
}

/** 院校详情 */
export function getUniversityDetail(id: number) {
  return httpGet<University>(`/university/${id}`)
}

/** 院校官方链接证据链 */
export function getUniversityOfficialLinks(schoolId: string) {
  return httpGet<OfficialLink | null>('/university/official-links', { schoolId })
}
