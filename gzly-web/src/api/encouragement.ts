import request from './request'
import type { EncouragementMessage, Result } from '@/types'

export function fetchEncouragementMessages(size = 12) {
  return request.get<Result<EncouragementMessage[]>>('/encouragement-messages', { params: { size } })
}

export function submitEncouragementMessage(data: {
  nickname?: string
  content: string
}) {
  return request.post<Result<EncouragementMessage>>('/encouragement-messages', data)
}
