/** SSE 流式读取工具 */
export function createSSEReader(
  url: string,
  onMessage: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void,
): { close: () => void } {
  const eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data === '[DONE]') {
      eventSource.close()
      onDone()
      return
    }
    try {
      const parsed = JSON.parse(data)
      const content = parsed.choices?.[0]?.delta?.content ?? ''
      if (content) {
        onMessage(content)
      }
    } catch {
      if (data) onMessage(data)
    }
  }

  eventSource.onerror = () => {
    eventSource.close()
    onError(new Error('SSE 连接异常'))
  }

  return {
    close: () => eventSource.close(),
  }
}
