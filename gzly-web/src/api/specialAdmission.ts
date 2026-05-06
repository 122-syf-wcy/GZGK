import http from './request'
import type { Result, SpecialAdmissionCategory, SpecialAdmissionPolicy } from '@/types'

export function fetchSpecialAdmissionCategories(year = 2026) {
  return http.get<Result<SpecialAdmissionCategory[]>>('/special-admissions/categories', {
    params: { year },
  })
}

export function fetchSpecialAdmissionPolicies(params: {
  year?: number
  category?: string
  limit?: number
} = {}) {
  return http.get<Result<SpecialAdmissionPolicy[]>>('/special-admissions/policies', {
    params,
  })
}

export function fetchLatestSpecialAdmissionPolicies(year = 2026, limit = 6) {
  return http.get<Result<SpecialAdmissionPolicy[]>>('/special-admissions/latest', {
    params: { year, limit },
  })
}
