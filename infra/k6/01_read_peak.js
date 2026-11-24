import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '20s', target: 100 },
    { duration: '60s', target: 300 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.005'],
    http_req_duration: ['p(95)<50', 'p(99)<120'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const SCORES = [586, 540, 520, 495, 610, 460, 512, 565];
const QUERIES = ['贵州大学', '西南大学', '重庆邮电', '四川大学', '电子科技', '武汉大学'];

export default function () {
  const score = SCORES[Math.floor(Math.random() * SCORES.length)];
  const kw = QUERIES[Math.floor(Math.random() * QUERIES.length)];

  const r1 = http.get(`${BASE_URL}/api/v2/core/score-rank/estimate?provinceCode=GZ&subjectType=%E7%89%A9%E7%90%86%E7%B1%BB&score=${score}`);
  check(r1, { 'rank status 200': (r) => r.status === 200 });

  const r2 = http.get(`${BASE_URL}/api/v2/core/universities?keyword=${encodeURIComponent(kw)}&page=1&size=10`);
  check(r2, { 'uni search status 200': (r) => r.status === 200 });

  sleep(0.05);
}
