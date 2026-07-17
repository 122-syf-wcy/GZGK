import { httpGet } from '@/utils/request'
import type { ProvinceScoreLineCapability, ProvinceScoreLineQueryResult } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

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

/** 省份分数线查询能力矩阵 */
export function getProvinceScoreLineCapability(provinceCode: ProvinceCode) {
  return httpGet<ProvinceScoreLineCapability>(`/score-lines/${provinceCode.toLowerCase()}/capability`)
}

function endpoint(type: string): string {
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
  return httpGet<ProvinceScoreLineQueryResult>(
    `/score-lines/${provinceCode.toLowerCase()}/${endpoint(type)}`,
    params as unknown as Record<string, unknown>,
  )
}
