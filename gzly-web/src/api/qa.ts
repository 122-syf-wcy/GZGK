import request from './request'

/** 查询某大学已审核问答 */
export function fetchQaList(schoolId: string, page = 1, pageSize = 20) {
  return request.get('/qa/list', { params: { schoolId, page, pageSize } })
}

/** 提交问题 */
export function submitQuestion(data: { schoolId: string; content: string; authorName?: string; authorType?: string }) {
  return request.post('/qa/ask', data)
}

/** 提交回答 */
export function submitAnswer(data: { questionId: number; content: string; authorName?: string; authorType?: string }) {
  return request.post('/qa/answer', data)
}

/** 点赞 */
export function likeQa(id: number) {
  return request.post(`/qa/like/${id}`)
}

/** 管理: 待审核列表 */
export function fetchQaPending(page = 1, size = 20) {
  return request.get('/admin/qa/pending', { params: { page, size } })
}

/** 管理: 审核 */
export function reviewQa(id: number, status: number, reason = '') {
  return request.post('/admin/qa/review', { id, status, reason })
}

/** 管理: 批量审核 */
export function reviewQaBatch(ids: number[], approved: boolean, reason = '') {
  return request.post('/admin/qa/review/batch', { ids, approved, reason })
}

/** 管理: 删除 */
export function deleteQa(id: number) {
  return request.delete(`/admin/qa/${id}`)
}

/** 管理: 待审核数量 */
export function fetchQaPendingCount() {
  return request.get('/admin/qa/count')
}

/** 管理: 全部QA列表(可按学校+状态筛选) */
export function fetchQaAll(params: { schoolId?: string; status?: number; page?: number; size?: number }) {
  return request.get('/admin/qa/list', { params })
}

/** 校友管理员: 回复问题 */
export function alumniReplyQa(data: { questionId: number; content: string; authorName?: string }) {
  return request.post('/alumni/qa/reply', data)
}

/** 校友管理员: 本校待审核列表 */
export function fetchAlumniQaPending(page = 1, size = 20) {
  return request.get('/alumni/qa/pending', { params: { page, size } })
}

/** 校友管理员：本校回复记录 */
export function fetchAlumniQaHistory() {
  return request.get('/alumni/qa/history')
}

/** 校友管理员: 审核 */
export function alumniReviewQa(data: { id: number; status: number; reason?: string }) {
  return request.post('/alumni/qa/review', data)
}

/** 校友管理员：修改 AI 退回说明 */
export function updateAlumniQaNote(id: number, reviewNote: string) {
  return request.post('/alumni/qa/review/update-note', { id, reviewNote })
}

/** 校友管理员：编辑被拒绝的回复并重新提交 */
export function editAndResubmitAlumniReply(id: number, content: string) {
  return request.post('/alumni/qa/reply/edit-and-resubmit', { id, content })
}

/** 系统管理员：学校级问答风险面板 */
export function fetchQaMonitorSchools(filter = 'all') {
  return request.get('/admin/qa/monitor/schools', { params: { filter } })
}

/** 系统管理员：异常问答日志 */
export function fetchQaMonitorLogs(params: { schoolId?: string; status?: number; page?: number; size?: number }) {
  return request.get('/admin/qa/monitor/logs', { params })
}

/** 系统管理员：下架异常问答 */
export function hideQaByAdmin(id: number, reason: string) {
  return request.post('/admin/qa/monitor/hide', { id, reason })
}

/** 系统管理员：学校禁评开关 */
export function toggleQaSchoolStatus(data: { schoolId: string; disabled: boolean; reason?: string; disabledUntil?: string | null }) {
  return request.post('/admin/qa/monitor/toggle-school', data)
}
