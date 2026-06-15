import http from './request'
import type { Result } from '@/types'

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
  score?: number | null
  rank?: number | null
  subjects?: string
  batch?: string
  majorPreference?: string
  regionPreference?: string
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

export async function fetchAiQaRegions(): Promise<AiQaRegion[]> {
  const res = await http.get<Result<AiQaRegion[]>>('/ai-qa/regions')
  return res.data.data ?? []
}

export async function createAiQaSession(payload: CreateSessionPayload): Promise<CreateSessionResult> {
  const res = await http.post<Result<CreateSessionResult>>('/ai-qa/sessions', payload)
  return res.data.data
}

export async function restoreAiQaSession(conversationCode: string): Promise<RestoreResult> {
  const res = await http.post<Result<RestoreResult>>('/ai-qa/sessions/restore', { conversationCode })
  return res.data.data
}

export async function sendAiQaMessage(
  sessionUid: string,
  conversationCode: string,
  content: string,
): Promise<SendMessageResult> {
  const res = await http.post<Result<SendMessageResult>>(
    `/ai-qa/sessions/${encodeURIComponent(sessionUid)}/messages`,
    { conversationCode, content },
    { timeout: 120000 },
  )
  return res.data.data
}

export async function listAiQaMessages(
  sessionUid: string,
  conversationCode: string,
): Promise<RestoreResult> {
  const res = await http.get<Result<RestoreResult>>(
    `/ai-qa/sessions/${encodeURIComponent(sessionUid)}/messages`,
    { params: { code: conversationCode } },
  )
  return res.data.data
}
