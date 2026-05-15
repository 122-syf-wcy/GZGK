import request from './request'
import type {
  AdminAiConfig,
  AdminCreateImportJobRequest,
  AdminDataYearReadiness,
  AdminEncouragementMessageListData,
  AdminAiModelListResult,
  AdminAiConfigTestResult,
  AdminImportJob,
  AdminImportJobDetail,
  AdminOfficialLinkListData,
  Announcement,
  PageResult,
  Result,
  SaveAdminAiConfigRequest,
} from '@/types'

export function fetchAdminStats() {
  return request.get('/admin/stats')
}

export function createAdminImportJob(data: AdminCreateImportJobRequest) {
  return request.post<Result<AdminImportJob>>('/admin/import-jobs', data)
}

export function fetchAdminImportJobs(params: {
  provinceCode?: string
  year?: number
  dataType?: string
  status?: string
  page?: number
  pageSize?: number
} = {}) {
  return request.get<Result<PageResult<AdminImportJob>>>('/admin/import-jobs', {
    params: {
      provinceCode: params.provinceCode || 'GZ',
      year: params.year || 2026,
      dataType: params.dataType || undefined,
      status: params.status || undefined,
      page: params.page || 1,
      pageSize: params.pageSize || 20,
    },
  })
}

export function fetchAdminImportJobDetail(jobId: string) {
  return request.get<Result<AdminImportJobDetail>>(`/admin/import-jobs/${jobId}`)
}

export function runAdminImportJobStaging(jobId: string) {
  return request.post<Result<AdminImportJob>>(`/admin/import-jobs/${jobId}/staging`)
}

export function runAdminImportJobQualityCheck(jobId: string) {
  return request.post<Result<AdminImportJob>>(`/admin/import-jobs/${jobId}/quality-check`)
}

export function generateAdminImportJobFormalSql(jobId: string) {
  return request.post<Result<AdminImportJob>>(`/admin/import-jobs/${jobId}/generate-formal-sql`)
}

export function generateAdminImportJobRollbackPlan(jobId: string) {
  return request.post<Result<AdminImportJob>>(`/admin/import-jobs/${jobId}/rollback-plan`)
}

export function fetchAdminDataYearReadiness(provinceCode = 'GZ', year = 2026) {
  return request.get<Result<AdminDataYearReadiness>>('/admin/data-year-readiness', {
    params: { provinceCode, year },
  })
}

export function fetchAdminAiConfig() {
  return request.get<Result<AdminAiConfig>>('/admin/ai-config')
}

export function saveAdminAiConfig(data: SaveAdminAiConfigRequest) {
  return request.post<Result<AdminAiConfig>>('/admin/ai-config', data)
}

export function testAdminAiConfig(data: SaveAdminAiConfigRequest) {
  return request.post<Result<AdminAiConfigTestResult>>('/admin/ai-config/test', data)
}

export function fetchAdminAiModels(data: SaveAdminAiConfigRequest) {
  return request.post<Result<AdminAiModelListResult>>('/admin/ai-config/models', data)
}

export function fetchAdminUsers(page = 1, size = 20) {
  return request.get('/admin/users', { params: { page, size } })
}

export function fetchAdminPlans(page = 1, size = 20, search = '') {
  return request.get('/admin/plans', { params: { page, size, search } })
}

export function fetchAdminUniversities(page = 1, size = 20, search = '') {
  return request.get('/admin/universities', { params: { page, size, search } })
}

export function fetchAdminScoreLines(page = 1, size = 20, search = '', year?: number, subjectType?: string) {
  return request.get('/admin/score-lines', { params: { page, size, search, year, subjectType } })
}

export function fetchAdminMajorScores(page = 1, size = 20, search = '', year?: number, subjectType?: string) {
  return request.get('/admin/major-scores', { params: { page, size, search, year, subjectType } })
}

export function fetchAdminOfficialLinks(
  page = 1,
  size = 20,
  search = '',
  province = '',
  status?: number,
  missingField?: string,
  sortBy: 'priority' | 'hot' | 'id' = 'priority',
  priorityOnly = true,
  windowDays: 30 | 90 = 30,
) {
  return request.get<Result<AdminOfficialLinkListData>>('/admin/official-links', {
    params: { page, size, search, province, status, missingField, sortBy, priorityOnly, windowDays },
  })
}

export function saveAdminOfficialLink(data: {
  schoolId: string
  schoolName?: string
  sourceDomain?: string
  schoolSite?: string
  admissionSite?: string
  admissionBrochureUrl?: string
  majorCatalogUrl?: string
  tuitionInfoUrl?: string
  tuitionRemark?: string
  tuitionSummary?: string
  majorCatalogSummary?: string
  adjustmentRule?: string
  foreignLanguageRule?: string
  physicalExamRule?: string
  singleSubjectRule?: string
  parserNotes?: string
  captureMethod?: string
  captureStatus?: number
  parseStatus?: number
  preserveNonEmpty?: boolean
}) {
  return request.post('/admin/official-links', data)
}

export function batchUpdateAdminOfficialLinkStatus(schoolIds: string[], captureStatus: number) {
  return request.post('/admin/official-links/batch-status', { schoolIds, captureStatus })
}

export function fetchAdminAnnouncements(page = 1, size = 20, status?: number) {
  return request.get('/admin/announcements', { params: { page, size, status } })
}

export function saveAdminAnnouncement(data: Announcement) {
  return request.post('/admin/announcements', data)
}

export function publishAdminAnnouncement(id: number, published: boolean) {
  return request.post('/admin/announcements/publish', { id, published })
}

export function fetchAdminFeedbacks(page = 1, size = 20, status?: number) {
  return request.get('/admin/feedbacks', { params: { page, size, status } })
}

export function markAdminFeedbackRead(id: number, read: boolean) {
  return request.post('/admin/feedbacks/read', { id, read })
}

export function fetchAdminEncouragementMessages(page = 1, size = 20, status?: number) {
  return request.get<Result<AdminEncouragementMessageListData>>('/admin/encouragement-messages', {
    params: { page, size, status },
  })
}

export function deleteAdminEncouragementMessage(id: number) {
  return request.delete<Result<string>>(`/admin/encouragement-messages/${id}`)
}
