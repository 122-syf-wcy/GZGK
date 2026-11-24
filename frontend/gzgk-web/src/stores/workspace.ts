import { defineStore } from 'pinia';
import { ref } from 'vue';
import type {
  AgentTraceStep,
  HitlDiffCard,
  PlanItem,
  PlanSnapshot,
  ProvincePolicyView,
  RagSource,
} from '@gzgk/shared';
import { STANDARD_DISCLAIMER } from '@gzgk/shared';
import { coreApi } from '@/api/client';

export interface ChatMessageItem {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  traces?: AgentTraceStep[];
  sources?: RagSource[];
  diffCard?: HitlDiffCard;
  streaming?: boolean;
  cacheHit?: boolean;
  threadId?: string;
}

export interface RecentThreadItem {
  id: string;
  title: string;
  route: string;
  subtitle: string;
}

function buildDemoItems(): PlanItem[] {
  const templates: [string, string, string, string, string[], string, number, number, number, double, boolean][] = [
    ['512', '福州大学', '电子信息类', '福州市', ['211', '双一流', '公办'], '冲', 588, 8120, 46, -0.1, false],
    ['118', '南京邮电大学', '通信工程', '南京市', ['双一流', '公办'], '冲', 589, 7850, 42, -0.45, true],
    ['203', '河海大学', '计算机科学与技术', '南京市', ['211', '双一流', '公办'], '冲', 591, 7420, 38, -0.05, false],
    ['110', '西南大学', '软件工程', '重庆市', ['211', '双一流', '公办'], '稳', 585, 8690, 76, 0.0, false],
    ['141', '重庆邮电大学', '计算机科学与技术', '重庆市', ['公办'], '稳', 582, 9150, 81, 0.08, false],
    ['538', '贵州大学', '计算机科学与技术', '贵阳市', ['211', '双一流', '公办'], '稳', 580, 9420, 84, 0.05, false],
    ['311', '西南交通大学', '人工智能', '成都市', ['211', '双一流', '公办'], '稳', 584, 8910, 78, 0.0, false],
    ['420', '昆明理工大学', '自动化', '昆明市', ['公办'], '保', 566, 13200, 93, 0.02, false],
    ['602', '成都信息工程大学', '网络空间安全', '成都市', ['公办'], '保', 562, 14500, 94, 0.04, false],
    ['715', '桂林电子科技大学', '电子科学与技术', '桂林市', ['公办'], '垫', 548, 19600, 96, 0.0, false],
  ];

  const items: PlanItem[] = [];
  for (let i = 0; i < 96; i++) {
    const base = templates[i % templates.length];
    const rankShift = Math.round(i * 155);
    const rank = 6400 + rankShift;
    const gradient: PlanItem['gradient'] = i < 18 ? '冲' : i < 54 ? '稳' : i < 82 ? '保' : '垫';
    const chanceScore = i < 18 ? Math.max(28, 49 - Math.floor(i * 0.8))
      : i < 54 ? Math.min(85, 62 + Math.floor((i - 18) * 0.6))
      : i < 82 ? Math.min(94, 86 + Math.floor((i - 54) * 0.25))
      : 96;
    const shrink = i === 4 ? -0.45 : (i === 9 ? -0.35 : base[9]);
    const needsReview = shrink <= -0.3 || i === 12 || i === 27;
    items.push({
      volunteerIndex: i + 1,
      itemId: `demo-${i + 1}`,
      gradient,
      schoolId: base[0],
      universityName: i < templates.length ? base[1] : `${base[1]}`,
      majorCode: `080${(i % 9) + 1}0${(i % 5) + 1}`,
      majorName: i < templates.length ? base[2] : `${base[2]}（方向${(i % 4) + 1}）`,
      province: base[3].includes('重庆') ? '重庆' : base[3].includes('成都') ? '四川' : base[3].includes('贵阳') ? '贵州' : '福建',
      city: base[3],
      schoolTags: base[4],
      schoolNature: '公办',
      tuition: '5,600元/年',
      latestMinScore: Math.max(460, 594 - Math.floor(i * 0.85)),
      latestMinRank: rank,
      predictedMinRank: rank,
      planCount: shrink < -0.3 ? 6 : 24 + (i % 15),
      planChangeRate: shrink,
      chanceScore,
      chanceLevel: chanceScore >= 90 ? '兜底参考' : chanceScore >= 75 ? '稳妥参考' : chanceScore >= 50 ? '适中' : '冲刺参考',
      calibratedProb: Math.min(0.95, Math.max(0.18, chanceScore / 105)),
      riskLevel: chanceScore >= 90 ? '很低' : chanceScore >= 75 ? '较低' : chanceScore >= 50 ? '中等' : '较高',
      needsManualReview: needsReview,
      manualReviewReason: shrink <= -0.3 ? '当年计划缩减明显，建议沙箱替换' : needsReview ? '请核验招生章程单科与体检要求' : '',
      officialCharterUrl: 'https://zs.cqupt.edu.cn/',
      recent3yRanks: [rank - 240, rank + 180, rank],
      locked: false,
      aiHighlighted: i === 18,
    });
  }
  return items;
}

type double = number;

export function createDemoPlan(): PlanSnapshot {
  return {
    planId: 'demo-gz-586',
    version: 3,
    provinceCode: 'GZ',
    provinceName: '贵州',
    volunteerUnitType: 'MAJOR_96',
    targetBatch: '专业(类)+院校平行志愿',
    totalScore: 586,
    provinceRank: 8450,
    firstSubject: '物理',
    resubjects: ['化学', '生物'],
    strategyMode: 'balanced',
    fingerprint: 'demo-gz-586-phys-chem-bio',
    readinessStatus: 'FULL',
    gradientCounts: { sprint: 18, steady: 36, safe: 28, pad: 14 },
    listHitRate: 0.984,
    manualReviewCount: 4,
    shrinkWarningCount: 2,
    diagnosisNotes: [
      '当前方案共 96 条（冲 18 / 稳 36 / 保 28 / 垫 14），蒙特卡洛 2000 次整表投档参考度 98.4%。',
      '梯度衔接平滑，无明显断层风险。',
      '检测到 2 个冲档专业存在缩招趋势，建议在右侧 AI 志愿顾问发起沙箱替换推演。',
    ],
    disclaimer: STANDARD_DISCLAIMER,
    metrics: {
      pipelineTimeMs: 38,
      cacheHit: false,
      singleFlightCoalesced: false,
      predictionEngine: 'rule-log-rank-fallback',
      recalledCandidates: 1240,
      hydrationQueries: 2,
      monteCarloIterations: 2000,
    },
    items: buildDemoItems(),
  };
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const sidebarCollapsed = ref(false);
  const activePlan = ref<PlanSnapshot>(createDemoPlan());
  const policies = ref<ProvincePolicyView[]>([]);
  const activeCitation = ref<RagSource | null>(null);
  const citationDrawerOpen = ref(false);
  const pendingPromptFromHome = ref<string>('');
  const pendingModeFromHome = ref<string>('advisor');

  const recentThreads = ref<RecentThreadItem[]>([
    { id: 't-risk', title: '本科方案风险复盘', route: '/report/demo-gz-586', subtitle: '多专家并行诊断' },
    { id: 't-gzu', title: '贵州大学与川渝计算机对比', route: '/workspace/demo-gz-586', subtitle: '方案工作台 · 沙箱调优' },
    { id: 't-city', title: '专业选择与招生章程核验', route: '/policies', subtitle: '政策与章程问答' },
  ]);

  const chatMessages = ref<ChatMessageItem[]>([
    {
      id: 'welcome-1',
      role: 'assistant',
      text: '你好，我是你的 **LangGraph AI 志愿顾问**。我可以调用 Java 高并发引擎核查官方一分一段与历年投档线、检索高校招生章程原文，或在修改志愿前先跑一遍 **2000 次蒙特卡洛沙箱推演**。\n\n你可以直接让我「把冲档里缩招严重的去掉，补 2 个川渝计算机稳档」或询问任意院校章程细节。',
    },
  ]);

  async function ensurePoliciesLoaded() {
    if (policies.value.length > 0) return;
    try {
      policies.value = await coreApi.listPolicies();
    } catch {
      policies.value = [
        { provinceCode: 'GZ', provinceName: '贵州', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'MAJOR_96', volunteerModeLabel: '专业(类)+院校平行志愿', maxVolunteerCount: 96, majorPerGroupCount: 0, hasAdjustment: false, sigmaMin: 0.06, readinessStatus: 'FULL', officialSourceName: '贵州省招生考试院', officialSourceUrl: 'https://zsksy.guizhou.gov.cn/', defaultGradientCounts: { sprint: 18, steady: 36, safe: 28, pad: 14 } },
        { provinceCode: 'SC', provinceName: '四川', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 45, majorPerGroupCount: 6, hasAdjustment: true, sigmaMin: 0.06, readinessStatus: 'ESTIMATE', officialSourceName: '四川省教育考试院', officialSourceUrl: 'https://www.sceea.cn/', defaultGradientCounts: { sprint: 10, steady: 18, safe: 12, pad: 5 } },
        { provinceCode: 'HB', provinceName: '湖北', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 45, majorPerGroupCount: 6, hasAdjustment: true, sigmaMin: 0.06, readinessStatus: 'ESTIMATE', officialSourceName: '湖北省教育考试院', officialSourceUrl: 'http://www.hbea.edu.cn/', defaultGradientCounts: { sprint: 15, steady: 15, safe: 11, pad: 4 } },
        { provinceCode: 'AH', provinceName: '安徽', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 45, majorPerGroupCount: 6, hasAdjustment: true, sigmaMin: 0.06, readinessStatus: 'ESTIMATE', officialSourceName: '安徽省教育招生考试院', officialSourceUrl: 'https://www.ahzsks.cn/', defaultGradientCounts: { sprint: 10, steady: 18, safe: 12, pad: 5 } },
        { provinceCode: 'GX', provinceName: '广西', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 40, majorPerGroupCount: 20, hasAdjustment: true, sigmaMin: 0.06, readinessStatus: 'ESTIMATE', officialSourceName: '广西招生考试院', officialSourceUrl: 'https://www.gxeea.cn/', defaultGradientCounts: { sprint: 9, steady: 16, safe: 11, pad: 4 } },
        { provinceCode: 'HI', provinceName: '海南', subjectMode: '3+3', scoreSystem: 'STANDARD_900', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 30, majorPerGroupCount: 6, hasAdjustment: true, sigmaMin: 0.07, readinessStatus: 'ESTIMATE', officialSourceName: '海南省考试局', officialSourceUrl: 'http://ea.hainan.gov.cn/', defaultGradientCounts: { sprint: 10, steady: 10, safe: 7, pad: 3 } },
        { provinceCode: 'YN', provinceName: '云南', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 40, majorPerGroupCount: 10, hasAdjustment: true, sigmaMin: 0.06, readinessStatus: 'ESTIMATE', officialSourceName: '云南省招生考试院', officialSourceUrl: 'https://www.ynzs.cn/', defaultGradientCounts: { sprint: 8, steady: 16, safe: 11, pad: 5 } },
        { provinceCode: 'HA', provinceName: '河南', subjectMode: '3+1+2', scoreSystem: 'RAW_750', volunteerUnitType: 'PROFESSIONAL_GROUP_45', volunteerModeLabel: '院校专业组平行志愿', maxVolunteerCount: 48, majorPerGroupCount: 6, hasAdjustment: true, sigmaMin: 0.08, readinessStatus: 'ESTIMATE', officialSourceName: '河南省教育考试院', officialSourceUrl: 'http://www.haeea.cn/', defaultGradientCounts: { sprint: 10, steady: 18, safe: 14, pad: 6 } },
      ];
    }
  }

  function setPlan(plan: PlanSnapshot) {
    activePlan.value = plan;
  }

  async function refreshPlan(planId: string) {
    if (!planId || planId === 'demo-gz-586') return;
    try {
      const latest = await coreApi.getPlan(planId);
      activePlan.value = latest;
    } catch {
      // keep current in-memory plan
    }
  }

  function openCitation(source: RagSource) {
    activeCitation.value = source;
    citationDrawerOpen.value = true;
  }

  function closeCitation() {
    citationDrawerOpen.value = false;
  }

  function addRecentThread(title: string, route: string, subtitle = 'AI 志愿会话') {
    const clean = title.slice(0, 18);
    recentThreads.value = [
      { id: `t-${Date.now()}`, title: clean, route, subtitle },
      ...recentThreads.value.filter((x) => x.title !== clean).slice(0, 5),
    ];
  }

  return {
    sidebarCollapsed,
    activePlan,
    policies,
    activeCitation,
    citationDrawerOpen,
    pendingPromptFromHome,
    pendingModeFromHome,
    recentThreads,
    chatMessages,
    ensurePoliciesLoaded,
    setPlan,
    refreshPlan,
    openCitation,
    closeCitation,
    addRecentThread,
  };
});
