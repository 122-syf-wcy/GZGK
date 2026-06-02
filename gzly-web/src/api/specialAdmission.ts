import http from './request'
import type { Result, SpecialAdmissionCategory, SpecialAdmissionPolicy } from '@/types'
import type { ProvinceCode } from '@/constants/provinces'

export function fetchSpecialAdmissionCategories(year = 2026, provinceCode?: ProvinceCode) {
  return http.get<Result<SpecialAdmissionCategory[]>>('/special-admissions/categories', {
    params: { year, provinceCode },
  })
}

export function fetchSpecialAdmissionPolicies(params: {
  year?: number
  provinceCode?: ProvinceCode
  category?: string
  limit?: number
} = {}) {
  return http.get<Result<SpecialAdmissionPolicy[]>>('/special-admissions/policies', {
    params,
  })
}

export function fetchLatestSpecialAdmissionPolicies(year = 2026, limit = 6, provinceCode?: ProvinceCode) {
  return http.get<Result<SpecialAdmissionPolicy[]>>('/special-admissions/latest', {
    params: { year, limit, provinceCode },
  })
}
