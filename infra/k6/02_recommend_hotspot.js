import http from 'k6/http';
import { check } from 'k6';

// Simulates Gaokao score-release tidal burst:
// 75% traffic hits hot score buckets (same province/score/rank) to validate
// L2 normalized fingerprint cache + Distributed SingleFlight coalescing.
export const options = {
  scenarios: {
    tidal_burst: {
      executor: 'ramping-vus',
      startVUs: 20,
      stages: [
        { duration: '15s', target: 200 },
        { duration: '45s', target: 400 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<120', 'p(99)<250'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const HOT_SCORES = [
  { score: 586, rank: 8450 },
  { score: 520, rank: 36500 },
  { score: 485, rank: 61200 },
];

export default function () {
  const isHot = Math.random() < 0.75;
  const pick = isHot
    ? HOT_SCORES[Math.floor(Math.random() * HOT_SCORES.length)]
    : { score: 420 + Math.floor(Math.random() * 220), rank: 2000 + Math.floor(Math.random() * 110000) };

  const payload = JSON.stringify({
    provinceCode: 'GZ',
    year: 2026,
    totalScore: pick.score,
    provinceRank: pick.rank,
    firstSubject: '物理',
    resubjects: ['化学', '生物'],
    strategyMode: 'balanced',
    agreedDisclaimer: true,
    disclaimerVersion: '2026.1',
  });

  const res = http.post(`${BASE_URL}/api/v2/core/recommend`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'recommend 200': (r) => r.status === 200,
    'has items': (r) => r.json('data.items.length') > 0,
  });
}
