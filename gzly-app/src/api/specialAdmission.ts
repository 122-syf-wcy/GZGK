import { httpGet } from '@/utils/request'

export interface SpecialAdmissionCategory {
  category: string
  categoryName: string
  count: number
}

export interface SpecialAdmissionPolicy {
  id: number
  year: number
  category: string
  categoryName: string
  title: string
  summary: string
  contentMd?: string
  sourceName?: string
  sourceUrl?: string
}

export function fetchSpecialAdmissionCategories(year = 2026) {
  return httpGet<SpecialAdmissionCategory[]>('/special-admissions/categories', { year })
}

export function fetchSpecialAdmissionPolicies(params: { year?: number; category?: string; limit?: number } = {}) {
  return httpGet<SpecialAdmissionPolicy[]>('/special-admissions/policies', params as Record<string, unknown>)
}

export function fetchLatestSpecialAdmissionPolicies(year = 2026, limit = 12) {
  return httpGet<SpecialAdmissionPolicy[]>('/special-admissions/latest', { year, limit })
}
