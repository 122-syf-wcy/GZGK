import request from './request'

export function submitFeedback(data: {
  content: string
  sourcePage?: string
}) {
  return request.post('/feedback', data)
}
