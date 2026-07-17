import { httpGet, httpPost } from '@/utils/request'

export interface EncouragementMessage {
  id: number
  nickname?: string
  content: string
  createdAt?: string
}

export function fetchEncouragementMessages(size = 20) {
  return httpGet<EncouragementMessage[]>('/encouragement-messages', { size })
}

export function submitEncouragementMessage(data: { nickname?: string; content: string }) {
  return httpPost<EncouragementMessage>('/encouragement-messages', data)
}
