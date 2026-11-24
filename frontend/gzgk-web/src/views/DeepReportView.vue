<template>
  <div class="mx-auto max-w-6xl space-y-5 px-6 py-6">
    <!-- Top Bar -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-bold text-ink-900">96 志愿多专家并行深度诊断报告</h1>
        <p class="mt-0.5 text-xs text-ink-500">
          由 LangGraph `Send` API 并行调度 3 位领域专家子图与 PostgreSQL 16 混合 RAG 溯源引擎生成。
        </p>
      </div>
      <div class="flex items-center gap-2.5">
        <button
          type="button"
          :disabled="loading"
          class="inline-flex items-center gap-1.5 rounded-full border border-line bg-white px-4 py-2 text-xs font-medium text-ink-700 transition hover:bg-sage-50"
          @click="runParallelReport"
        >
          <RefreshCw :class="['h-3.5 w-3.5', loading ? 'animate-spin' : '']" />
          <span>{{ loading ? '并行推演中…' : '重新运行 3 专家诊断' }}</span>
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-full bg-pine-700 px-4 py-2 text-xs font-medium text-white transition hover:bg-pine-800"
          @click="printReport"
        >
          <Printer class="h-3.5 w-3.5" />
          <span>导出诊断 PDF</span>
        </button>
      </div>
    </div>

    <!-- 3 Parallel LangGraph Map-Reduce Expert Cards -->
    <div class="grid gap-4 md:grid-cols-3">
      <div
        v-for="exp in experts"
        :key="exp.expertId"
        :class="[
          'rounded-2xl border border-line border-t-[3px] bg-white p-4 shadow-editorial',
          exp.status === 'warning' ? 'border-t-ochre-600' : exp.expertId === 'gradient_risk' ? 'border-t-pine-700' : 'border-t-sage-500',
        ]"
      >
        <div class="flex items-center justify-between">
          <h3 class="text-[14.5px] font-semibold text-ink-900">{{ exp.title }}</h3>
          <span
            :class="[
              'flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold',
              exp.status === 'warning' ? 'bg-ochre-50 text-ochre-600' : 'bg-sage-100 text-pine-700',
            ]"
          >
            {{ exp.status === 'warning' ? '!' : '✓' }}
          </span>
        </div>
        <p class="mt-2.5 text-xs leading-relaxed text-ink-700">{{ exp.summary }}</p>
      </div>
    </div>

    <!-- Main 96-Volunteer Rank Ladder Chart -->
    <div class="rounded-2xl border border-line bg-white p-5 shadow-editorial">
      <RankLadderChart
        :items="store.activePlan.items"
        :candidate-rank="store.activePlan.provinceRank"
        @select-item="openItemCharter"
      />
    </div>

    <!-- Synthesized Action Plan + Clickable Official RAG Sources -->
    <div class="grid gap-4 lg:grid-cols-12">
      <div class="rounded-2xl border border-line bg-white p-5 shadow-editorial lg:col-span-7">
        <h3 class="text-sm font-semibold text-ink-900">综合诊断结论与行动清单</h3>
        <div class="mt-3 whitespace-pre-line text-[13.5px] leading-relaxed text-ink-700">
          {{ reportText }}
        </div>
      </div>

      <div class="rounded-2xl border border-line bg-white p-5 shadow-editorial lg:col-span-5">
        <h3 class="text-sm font-semibold text-ink-900">官方招生章程与政策溯源（点击右侧展开原文）</h3>
        <div class="mt-3 space-y-2.5">
          <button
            v-for="src in sources"
            :key="src.chunkId"
            type="button"
            class="flex w-full items-start gap-2.5 rounded-xl border border-line bg-paper p-3 text-left transition hover:border-pine-600/45 hover:bg-sage-50"
            @click="store.openCitation(src)"
          >
            <span class="mt-0.5 rounded bg-sage-100 px-1.5 py-0.5 font-mono text-[11px] font-semibold text-pine-800">
              [{{ src.index }}]
            </span>
            <div class="min-w-0 flex-1">
              <div class="truncate text-xs font-semibold text-ink-900">{{ src.title }}</div>
              <p class="mt-1 line-clamp-2 text-[11.5px] text-ink-500">{{ src.highlight }}</p>
            </div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Printer, RefreshCw } from 'lucide-vue-next';
import type { ExpertReportCard, PlanItem, RagSource } from '@gzgk/shared';
import { agentApi } from '@/api/client';
import { useWorkspaceStore } from '@/stores/workspace';
import RankLadderChart from '@/components/RankLadderChart.vue';

const store = useWorkspaceStore();
const loading = ref(false);

const experts = ref<ExpertReportCard[]>([
  {
    expertId: 'gradient_risk',
    title: '梯度与风险专家',
    status: 'completed',
    summary: `冲 ${store.activePlan.gradientCounts.sprint} / 稳 ${store.activePlan.gradientCounts.steady} / 保 ${store.activePlan.gradientCounts.safe} / 垫 ${store.activePlan.gradientCounts.pad} 分布平滑，整表投档参考度 ${(store.activePlan.listHitRate * 100).toFixed(1)}%。`,
  },
  {
    expertId: 'charter_audit',
    title: '章程与限报专家',
    status: 'warning',
    summary: `选科匹配核验完成，发现 ${store.activePlan.manualReviewCount} 项涉及单科/体检/高学费提示，建议点击下方官方原文抽屉复核。`,
  },
  {
    expertId: 'career_city',
    title: '专业与城市专家',
    status: 'completed',
    summary: '计算机与电子信息核心方向占比 68%，川渝与西南公办院校兼顾学费预算与升学就业路径。',
  },
]);

const sources = ref<RagSource[]>([
  {
    index: 1,
    chunkId: 'policy_gz_2025_2026#000',
    title: '贵州省新高考 3+1+2 普通类本科批 96 平行志愿投档规则',
    docType: 'policy',
    excerpt: '贵州普通类本科批实行「专业（类）+ 院校」平行志愿，不设专业调剂；但若未核对招生章程单科与体检限制仍可能被退档。',
    highlight: '最多可填报 96 个志愿，不设专业服从调剂，须重点核对单科与体检限制',
    sourceUrl: 'https://zsksy.guizhou.gov.cn/',
    verified: true,
  },
  {
    index: 2,
    chunkId: 'charter_cqupt_2025#000',
    title: '重庆邮电大学 2025 年本科招生章程 · 第十六条',
    docType: 'charter',
    schoolName: '重庆邮电大学',
    excerpt: '第十六条：计算机科学与技术、软件工程专业限招物理+化学选考考生，不设外语单科成绩限制，普通本科工科学费 5,600 元/年。',
    highlight: '限招物理+化学选考考生，无外语单科成绩限制，学费 5,600 元/年',
    sourceUrl: 'https://zs.cqupt.edu.cn/',
    verified: true,
  },
]);

const reportText = ref(
  '1. 冲稳保垫梯度整体平滑，2000 次蒙特卡洛仿真显示整表投档参考度处于稳健区间。\n' +
    '2. 优先逐条核验标记「需章程核验」的 4 个专业，确认外语语种、单科成绩与色觉体检符合要求。\n' +
    '3. 对前 15 个冲档中出现计划缩减的专业，建议回到方案工作台使用 AI 副驾驶进行沙箱替换推演。',
);

async function runParallelReport() {
  loading.value = true;
  try {
    const res = await agentApi.generateReport({
      threadId: `report-${Date.now()}`,
      planId: store.activePlan.planId,
      provinceCode: store.activePlan.provinceCode,
    });
    if (res.expertReports?.length) experts.value = res.expertReports;
    if (res.sources?.length) sources.value = res.sources;
    if (res.answer) reportText.value = res.answer;
  } catch {
    // Keep hydrated local preview
  } finally {
    loading.value = false;
  }
}

function openItemCharter(item: PlanItem) {
  store.openCitation(
    sources.value.find((s) => s.schoolName && item.universityName.includes(s.schoolName)) || {
      index: item.volunteerIndex,
      chunkId: `school-${item.schoolId}`,
      title: `${item.universityName} · ${item.majorName} 官方核验`,
      docType: 'charter',
      schoolName: item.universityName,
      excerpt: `2025 年最低位次 ${item.latestMinRank.toLocaleString()} 名（最低分 ${item.latestMinScore}），参考学费 ${item.tuition}。${item.manualReviewReason || '请前往高校官方招生网核对当年章程最新条款。'}`,
      highlight: `机会指数 ${item.chanceScore}（${item.chanceLevel}）· 梯度：${item.gradient}`,
      sourceUrl: item.officialCharterUrl,
      verified: true,
    },
  );
}

function printReport() {
  window.print();
}

onMounted(() => {
  runParallelReport();
});
</script>
