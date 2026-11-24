import type {
  AgentContextStats,
  AgentToolInfo,
  ApiResponse,
  MajorScoreRecord,
  PlanSnapshot,
  ProvincePolicyView,
  RagSource,
  RankEstimate,
  StudentMemoryItem,
  UniversityRecord,
} from '@gzgk/shared';

const CORE_BASE = '/api/v2/core';
const AGENT_BASE = '/api/v2/agent';

async function getJson<T>(url: string): Promise<T> {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const payload = (await resp.json()) as ApiResponse<T>;
  if (payload.code !== 0) throw new Error(payload.message || '请求失败');
  return payload.data;
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  const payload = (await resp.json()) as ApiResponse<T>;
  if (payload.code !== 0) throw new Error(payload.message || '请求失败');
  return payload.data;
}

export const coreApi = {
  listPolicies: () => getJson<ProvincePolicyView[]>(`${CORE_BASE}/policies`),
  estimateRank: (provinceCode: string, subjectType: string, score: number) =>
    getJson<RankEstimate>(
      `${CORE_BASE}/score-rank/estimate?provinceCode=${encodeURIComponent(provinceCode)}&subjectType=${encodeURIComponent(subjectType)}&score=${score}`,
    ),
  getDistribution: (provinceCode: string, subjectType: string) =>
    getJson<{
      provinceCode: string;
      subjectType: string;
      undergraduateLine: number;
      specialControlLine: number;
      curve: { score: number; count: number }[];
    }>(
      `${CORE_BASE}/score-rank/distribution?provinceCode=${encodeURIComponent(provinceCode)}&subjectType=${encodeURIComponent(subjectType)}`,
    ),
  queryScoreLines: (params: { provinceCode: string; schoolName?: string; majorName?: string; subjectType?: string; limit?: number }) => {
    const q = new URLSearchParams();
    q.set('provinceCode', params.provinceCode);
    if (params.schoolName) q.set('schoolName', params.schoolName);
    if (params.majorName) q.set('majorName', params.majorName);
    if (params.subjectType) q.set('subjectType', params.subjectType);
    q.set('limit', String(params.limit ?? 40));
    return getJson<MajorScoreRecord[]>(`${CORE_BASE}/score-rank/lines?${q.toString()}`);
  },
  searchUniversities: (params: { keyword?: string; province?: string; tag?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams();
    if (params.keyword) q.set('keyword', params.keyword);
    if (params.province) q.set('province', params.province);
    if (params.tag) q.set('tag', params.tag);
    q.set('page', String(params.page ?? 1));
    q.set('size', String(params.size ?? 18));
    return getJson<{ total: number; page: number; size: number; items: UniversityRecord[] }>(
      `${CORE_BASE}/universities?${q.toString()}`,
    );
  },
  generatePlan: (payload: Record<string, unknown>) => postJson<PlanSnapshot>(`${CORE_BASE}/recommend`, payload),
  getPlan: (planId: string) => getJson<PlanSnapshot>(`${CORE_BASE}/plans/${encodeURIComponent(planId)}`),
  rollbackPlan: (planId: string) =>
    postJson<{ newVersion: number; updatedPlan: PlanSnapshot; message: string }>(
      `${CORE_BASE}/plans/${encodeURIComponent(planId)}/rollback`,
      {},
    ),
};

export const agentApi = {
  streamUrl: `${AGENT_BASE}/chat/stream`,
  resumeHitl: (threadId: string, decision: 'approve' | 'reject') =>
    postJson<{
      threadId: string;
      decision: string;
      traces: { stage: string; label: string; latencyMs?: number }[];
      applyResult?: { applied: boolean; newVersion: number; newListHitRate: number; message: string };
      answer: string;
    }>(`${AGENT_BASE}/threads/${encodeURIComponent(threadId)}/resume`, { decision }),
  generateReport: (payload: { threadId: string; planId: string; provinceCode: string; message?: string }) =>
    postJson<{
      expertReports: { expertId: string; title: string; status: string; summary: string; latencyMs?: number }[];
      sources: RagSource[];
      answer: string;
    }>(`${AGENT_BASE}/report/generate`, payload),
  searchKnowledge: (q: string, provinceCode = 'GZ') =>
    getJson<RagSource[]>(`${AGENT_BASE}/rag/search?q=${encodeURIComponent(q)}&provinceCode=${encodeURIComponent(provinceCode)}`),
  listTools: () => getJson<AgentToolInfo[]>(`${AGENT_BASE}/tools`),
  getMemories: (userId = 'demo-student') =>
    getJson<StudentMemoryItem[]>(`${AGENT_BASE}/memory/${encodeURIComponent(userId)}`),
  deleteMemory: async (userId: string, key: string) => {
    const resp = await fetch(`${AGENT_BASE}/memory/${encodeURIComponent(userId)}/${encodeURIComponent(key)}`, {
      method: 'DELETE',
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  },
  compactThread: (threadId: string, userId = 'demo-student') =>
    postJson<{
      compressed: boolean;
      beforeTokens: number;
      afterTokens: number;
      removedCount: number;
      runningSummary: string;
      contextStats: AgentContextStats;
    }>(`${AGENT_BASE}/threads/${encodeURIComponent(threadId)}/compact?userId=${encodeURIComponent(userId)}`, {}),
};
