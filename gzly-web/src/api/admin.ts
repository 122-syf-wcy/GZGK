import request from './request'
import type {
  AdminAiConfig,
  AdminEncouragementMessageListData,
  AdminAiModelListResult,
  AdminAiConfigTestResult,
  AdminOfficialLinkListData,
  AdminPlanCleanupRequest,
  AdminPlanCleanupResponse,
  AdminPlanDeleteResponse,
  AdminPlanListData,
  AdminPlanListParams,
  Announcement,
  DataYearReadinessDto,
  Result,
  SaveAdminAiConfigRequest,
} from '@/types'

export function fetchAdminStats() {
  return request.get('/admin/stats')
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

export function fetchAdminPlans(
  pageOrParams: number | AdminPlanListParams = 1,
  size = 20,
  search = '',
) {
  const params = typeof pageOrParams === 'object'
    ? { page: 1, size: 20, ...pageOrParams }
    : { page: pageOrParams, size, search }
  return request.get<Result<AdminPlanListData>>('/admin/plans', { params })
}

export function deleteAdminPlan(id: number, reason?: string) {
  return request.delete<Result<AdminPlanDeleteResponse>>(`/admin/plans/${id}`, {
    data: { reason },
  })
}

export function batchDeleteAdminPlans(ids: number[], reason?: string) {
  return request.post<Result<AdminPlanDeleteResponse>>('/admin/plans/batch-delete', { ids, reason })
}

export function cleanupAdminTestPlans(data: AdminPlanCleanupRequest) {
  return request.post<Result<AdminPlanCleanupResponse>>('/admin/plans/cleanup-test-records', data)
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

export function fetchAdminDataYearReadiness(provinceCode = 'GZ', year = 2026) {
  return request.get<Result<DataYearReadinessDto>>('/admin/data-year-readiness', {
    params: { provinceCode, year },
  })
}

// ── 运营收口：AI 问答会话、AI 状态、反馈工单、方案恢复、巡检 ──

export function fetchAdminAiQaSessions(page = 1, size = 20, regionCode?: string) {
  return request.get('/admin/ai-qa/sessions', { params: { page, size, regionCode } })
}

export function fetchAdminAiOpsStatus() {
  return request.get('/admin/ai-ops/ai-status')
}

export function testAdminAiVolunteer() {
  return request.post('/admin/ai-ops/test-volunteer')
}

export function testAdminAiQa() {
  return request.post('/admin/ai-ops/test-ai-qa')
}

export function fetchAdminFeedbackOps(page = 1, size = 20, handleStatus?: number) {
  return request.get('/admin/feedback-ops/list', { params: { page, size, handleStatus } })
}

export function updateAdminFeedbackStatus(id: number, handleStatus: number, handleNote?: string) {
  return request.post('/admin/feedback-ops/status', { id, handleStatus, handleNote })
}

export function restoreAdminPlan(id: number) {
  return request.post(`/admin/plans/${id}/restore`)
}

export function batchRestoreAdminPlans(ids: number[]) {
  return request.post('/admin/plans/batch-restore', { ids })
}

export function fetchAdminOpsHealthCheck() {
  return request.get('/admin/ops/health-check')
}
