/**
 * POST + ReadableStream 方式消费 SSE（EventSource 只支持 GET）。
 * 用于 Agent 问答等需要请求体携带凭证的流式接口。
 */
export interface AgentStreamEvent {
  type: 'reasoning' | 'content' | 'final' | 'tool_call' | 'tool_result' | 'sources' | string
  text?: string
  tool?: string
  label?: string
  /** sources 事件：命中的策略库片段 */
  chunks?: Array<Record<string, unknown>>
}

export interface PostSseHandlers {
  onEvent: (event: AgentStreamEvent) => void
  onDone: () => void
  /** status 为 HTTP 状态码；网络层失败时为 0 */
  onError: (message: string, status: number) => void
}

export async function postSseStream(url: string, body: unknown, handlers: PostSseHandlers): Promise<void> {
  let res: Response
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    handlers.onError('网络连接异常，请检查网络后重试。', 0)
    return
  }
  if (!res.ok || !res.body) {
    handlers.onError('服务暂时不可用，请稍后重试。', res.status)
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const handleData = (data: string): boolean => {
    if (data === '[DONE]') {
      handlers.onDone()
      return true
    }
    if (data.startsWith('[ERROR]')) {
      handlers.onError(data.slice('[ERROR]'.length).trim() || 'AI 服务暂时不可用', -1)
      return true
    }
    try {
      handlers.onEvent(JSON.parse(data) as AgentStreamEvent)
    } catch {
      // 非 JSON 数据按纯文本正文处理，兼容旧协议
      if (data) handlers.onEvent({ type: 'content', text: data })
    }
    return false
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep = buffer.indexOf('\n\n')
    while (sep !== -1) {
      const rawEvent = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      for (const line of rawEvent.split('\n')) {
        if (!line.startsWith('data:')) continue
        if (handleData(line.slice(5).trim())) return
      }
      sep = buffer.indexOf('\n\n')
    }
  }
  handlers.onDone()
}
