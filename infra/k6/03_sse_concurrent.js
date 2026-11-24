import http from 'k6/http';
import { check, sleep } from 'k6';

// Stress tests WebFlux SSE proxy + Python async LangGraph stream endpoint.
export const options = {
  vus: 50,
  duration: '45s',
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_waiting: ['p(95)<1500'], // TTFT / initial stream handshake
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const PLAN_ID = __ENV.PLAN_ID || 'demo-plan-gz-586';

export default function () {
  const payload = JSON.stringify({
    threadId: `k6-thread-${__VU}-${__ITER}`,
    planId: PLAN_ID,
    message: '贵州物理类 586 分冲稳保梯度怎么看？有缩招风险吗？',
    mode: 'advisor',
  });

  const res = http.post(`${BASE_URL}/api/v2/agent/chat/stream`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    timeout: '30s',
  });

  check(res, {
    'sse 200': (r) => r.status === 200,
    'contains done': (r) => r.body && r.body.includes('[DONE]'),
  });
  sleep(0.5);
}
