import { httpGet, httpPost, request } from '@/utils/request'

export interface AiQaRegion {
  code: string
  name: string
}

export interface AiQaEvidence {
  title: string
  url: string
  sourceName: string
  summary: string
}

export interface AiQaMessage {
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
  evidence?: AiQaEvidence[]
}

export interface AiQaSessionView {
  sessionUid: string
  regionCode: string
  regionName: string
  examYear?: number
  messageCount?: number
  notice?: string
}

export interface CreateSessionPayload {
  regionCode: string
  score?: number | null
  rank?: number | null
  subjects?: string[]
  batch?: string
  majorPreference?: string
  regionPreference?: string
}

export interface CreateSessionResult {
  sessionUid: string
  conversationCode: string
  regionCode: string
  regionName: string
  examYear?: number
  notice?: string
  disclaimer?: string
}

export interface SendMessageResult {
  assistantMessage: AiQaMessage
  compacted: boolean
  remainingToday: number
}

export interface RestoreResult {
  session: AiQaSessionView
  messages: AiQaMessage[]
}

export function fetchAiQaRegions() {
  return httpGet<AiQaRegion[]>('/ai-qa/regions')
}

export function createAiQaSession(payload: CreateSessionPayload) {
  return httpPost<CreateSessionResult>('/ai-qa/sessions', payload as unknown as Record<string, unknown>)
}

export function restoreAiQaSession(conversationCode: string) {
  return httpPost<RestoreResult>('/ai-qa/sessions/restore', { conversationCode })
}

export function sendAiQaMessage(sessionUid: string, conversationCode: string, content: string) {
  return request<SendMessageResult>(`/ai-qa/sessions/${encodeURIComponent(sessionUid)}/messages`, {
    method: 'POST',
    data: { conversationCode, content },
    timeout: 120000,
  })
}

export function listAiQaMessages(sessionUid: string, conversationCode: string) {
  return httpGet<RestoreResult>(`/ai-qa/sessions/${encodeURIComponent(sessionUid)}/messages`, {
    code: conversationCode,
  })
}
