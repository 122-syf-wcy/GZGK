import { httpPost } from '@/utils/request'

export function submitFeedback(data: { content: string; sourcePage?: string }) {
  return httpPost('/feedback', data)
}
