import request from './request'

export function checkSchoolHasAdmin(schoolId: string) {
  return request.get('/alumni/has-admin', { params: { schoolId } })
}

export function applyAlumni(data: {
  schoolId: string; nickname: string; phone: string; email?: string;
  credentialUrl?: string; graduationYear?: number; major?: string; bio?: string; password: string;
}) {
  return request.post('/alumni/apply', data)
}

export function checkApplicationStatus(phone: string) {
  return request.get('/alumni/application/status', { params: { phone } })
}

export function alumniLogin(phone: string, password: string) {
  return request.post('/alumni/login', { phone, password })
}

export function getPendingApplications(status = 0, page = 1, size = 20) {
  return request.get('/alumni/admin/applications', { params: { status, page, size } })
}

export function reviewApplication(id: number, approved: boolean, reason = '') {
  return request.post('/alumni/admin/review', { id, approved, reason })
}

export function uploadMedia(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/alumni/media/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function saveMedia(data: {
  schoolId: string; mediaType: number;
  url: string; thumbUrl?: string; caption?: string; sortOrder?: number;
}) {
  return request.post('/alumni/media/save', data)
}

export function listMedia(schoolId: string, status?: number) {
  const params: Record<string, string | number> = { schoolId }
  if (typeof status === 'number') {
    params.status = status
  }
  return request.get('/alumni/media/list', { params })
}

export function getPendingMedia(page = 1, size = 20, status?: number) {
  const params: Record<string, number> = { page, size }
  if (typeof status === 'number') {
    params.status = status
  }
  return request.get('/alumni/media/pending', { params })
}

export function reviewMedia(id: number, approved: boolean, reason = '') {
  return request.post('/alumni/media/review', { id, approved, reason })
}

export function reviewMediaBatch(ids: number[], approved: boolean, reason = '') {
  return request.post('/alumni/media/review/batch', { ids, approved, reason })
}

export function deleteMedia(id: number) {
  return request.delete(`/alumni/media/${id}`)
}

export function submitContentEdit(data: {
  schoolId: string; fieldName: string;
  oldValue?: string; newValue: string;
}) {
  return request.post('/alumni/content/edit', data)
}

export function getPendingEdits(status = 0, page = 1, size = 20) {
  return request.get('/alumni/content/edits', { params: { status, page, size } })
}

export function getMyContentEdits(status?: number) {
  const params: Record<string, number> = {}
  if (typeof status === 'number') {
    params.status = status
  }
  return request.get('/alumni/content/my-edits', { params })
}

export function reviewContentEdit(id: number, approved: boolean, reason = '') {
  return request.post('/alumni/content/review', { id, approved, reason })
}

export function reviewContentEditBatch(ids: number[], approved: boolean, reason = '') {
  return request.post('/alumni/content/review/batch', { ids, approved, reason })
}
