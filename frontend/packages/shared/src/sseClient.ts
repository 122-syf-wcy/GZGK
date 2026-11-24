import type { SseEnvelope } from './types';

export interface SseStreamOptions {
  url: string;
  body: Record<string, unknown>;
  lastEventId?: string;
  signal?: AbortSignal;
  onEvent: (ev: SseEnvelope) => void;
  onDone?: () => void;
  onError?: (err: Error) => void;
}

/**
 * Fetch-based POST SSE streaming client supporting `Last-Event-ID` resumption
 * and unified `contracts/sse-protocol.schema.json` frame parsing.
 */
export async function streamSsePost(opts: SseStreamOptions): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  };
  if (opts.lastEventId) {
    headers['Last-Event-ID'] = opts.lastEventId;
  }

  const resp = await fetch(opts.url, {
    method: 'POST',
    headers,
    body: JSON.stringify(opts.body),
    signal: opts.signal,
  });

  if (!resp.ok || !resp.body) {
    throw new Error(`SSE stream failed with HTTP ${resp.status}`);
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split('\n\n');
      buffer = blocks.pop() ?? '';

      for (const block of blocks) {
        const lines = block.split('\n');
        let dataLine = '';
        for (const line of lines) {
          if (line.startsWith('data:')) {
            dataLine += line.slice(5).trim();
          }
        }
        if (!dataLine) continue;
        if (dataLine === '[DONE]') {
          opts.onDone?.();
          return;
        }
        try {
          const parsed = JSON.parse(dataLine) as SseEnvelope;
          opts.onEvent(parsed);
        } catch {
          // ignore malformed partial heartbeat
        }
      }
    }
    opts.onDone?.();
  } catch (err) {
    if (opts.onError) {
      opts.onError(err instanceof Error ? err : new Error(String(err)));
    } else {
      throw err;
    }
  }
}
