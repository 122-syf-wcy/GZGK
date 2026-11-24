export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  timestampMs?: number;
}

export interface ProvincePolicyView {
  provinceCode: string;
  provinceName: string;
  subjectMode: '3+1+2' | '3+3' | string;
  scoreSystem: 'RAW_750' | 'STANDARD_900' | string;
  volunteerUnitType: 'MAJOR_96' | 'PROFESSIONAL_GROUP_45' | string;
  volunteerModeLabel: string;
  maxVolunteerCount: number;
  majorPerGroupCount: number;
  hasAdjustment: boolean;
  sigmaMin: number;
  readinessStatus: 'FULL' | 'ESTIMATE' | 'BLOCKED' | string;
  officialSourceName: string;
  officialSourceUrl: string;
  defaultGradientCounts: Record<string, number>;
}

export interface RankEstimate {
  provinceCode: string;
  year: number;
  subjectType: string;
  score: number;
  rankLow: number;
  rankHigh: number;
  segmentCount: number;
  cumulativeCount: number;
  beatPercentage: number;
  sourceName: string;
  sourceUrl: string;
  officialReady: boolean;
  latencyMicros: number;
}

export interface GradientCounts {
  sprint: number;
  steady: number;
  safe: number;
  pad: number;
}

export interface PlanItem {
  volunteerIndex: number;
  itemId: string;
  gradient: '冲' | '稳' | '保' | '垫' | string;
  schoolId: string;
  universityName: string;
  majorCode: string;
  majorName: string;
  province: string;
  city: string;
  schoolTags: string[];
  schoolNature: string;
  tuition: string;
  latestMinScore: number;
  latestMinRank: number;
  predictedMinRank: number;
  planCount: number;
  planChangeRate: number;
  chanceScore: number;
  chanceLevel: string;
  calibratedProb: number;
  riskLevel: string;
  needsManualReview: boolean;
  manualReviewReason: string;
  officialCharterUrl: string;
  recent3yRanks: number[];
  locked: boolean;
  aiHighlighted?: boolean;
}

export interface PlanMetrics {
  pipelineTimeMs: number;
  cacheHit: boolean;
  singleFlightCoalesced: boolean;
  predictionEngine: string;
  recalledCandidates: number;
  hydrationQueries: number;
  monteCarloIterations: number;
}

export interface PlanSnapshot {
  planId: string;
  version: number;
  provinceCode: string;
  provinceName: string;
  volunteerUnitType: string;
  targetBatch: string;
  totalScore: number;
  provinceRank: number;
  firstSubject: string;
  resubjects: string[];
  strategyMode: string;
  fingerprint: string;
  readinessStatus: string;
  gradientCounts: GradientCounts;
  listHitRate: number;
  manualReviewCount: number;
  shrinkWarningCount: number;
  diagnosisNotes: string[];
  disclaimer: string;
  metrics: PlanMetrics;
  items: PlanItem[];
}

export interface UniversityRecord {
  schoolId: string;
  name: string;
  province: string;
  city: string;
  level: string;
  typeName: string;
  tags: string[];
  is985: boolean;
  is211: boolean;
  isDualClass: boolean;
  natureName: string;
  belong: string;
  schoolSite: string;
  officialCharterUrl: string;
  summary: string;
}

export interface MajorScoreRecord {
  provinceCode: string;
  schoolId: string;
  universityName: string;
  majorCode: string;
  majorName: string;
  year: number;
  subjectType: string;
  batch: string;
  resubjectRequirement: string;
  minScore: number | null;
  minRank: number | null;
  planCount: number | null;
}

export interface RagSource {
  index: number;
  chunkId: string;
  title: string;
  docType: string;
  schoolName?: string;
  excerpt: string;
  highlight: string;
  sourceUrl?: string;
  verified: boolean;
  score?: number;
}

export interface AgentTraceStep {
  stage: string;
  tool?: string;
  label: string;
  latencyMs?: number;
}

export interface ExpertReportCard {
  expertId: 'gradient_risk' | 'charter_audit' | 'career_city' | string;
  title: string;
  status: 'running' | 'completed' | 'warning' | string;
  summary: string;
  latencyMs?: number;
}

export interface DiffChangeEntry {
  action: 'REMOVE' | 'ADD' | 'MOVE' | string;
  volunteerIndex: number;
  universityName: string;
  majorName: string;
  gradient: string;
  chanceScore: number;
  summary: string;
}

export interface HitlDiffCard {
  interruptId: string;
  planId: string;
  baseVersion: number;
  beforeHitRate: number;
  afterHitRate: number;
  hitRateDelta: number;
  gradientCliffResolved: boolean;
  changes: DiffChangeEntry[];
  resolved?: 'approved' | 'rejected';
}

export interface AgentContextStats {
  totalTokens: number;
  budgetTokens: number;
  triggerTokens: number;
  usagePercent: number;
  messageCount: number;
  compressionCount: number;
  hasRunningSummary: boolean;
  runningSummaryPreview: string;
  memoryCount: number;
  nativeToolsCount: number;
  mcpToolsCount: number;
}

export interface AgentToolInfo {
  name: string;
  source: 'native-grpc' | 'mcp' | string;
  server: string;
  description: string;
}

export interface StudentMemoryItem {
  key: string;
  category: string;
  value: string;
  sourceQuote?: string;
  updatedAt?: number;
}

export interface SseEnvelope {
  eventId: string;
  threadId?: string;
  type:
    | 'status'
    | 'tool_call'
    | 'tool_result'
    | 'expert_progress'
    | 'reasoning'
    | 'content'
    | 'sources'
    | 'diff_card'
    | 'context_stats'
    | 'final'
    | 'error';
  timestampMs: number;
  payload: Record<string, any>;
}
