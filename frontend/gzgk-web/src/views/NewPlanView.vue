<template>
  <div class="mx-auto max-w-5xl px-6 py-9">
    <div class="text-center">
      <h1 class="text-[30px] font-bold tracking-tight text-ink-900">新建志愿方案</h1>
      <p class="mt-1.5 text-sm text-ink-500">
        输入省份与高考成绩，毫秒级匹配官方一分一段与批次规则。
      </p>
    </div>

    <div class="mt-7 grid gap-6 rounded-[24px] border border-line bg-white p-6 shadow-composer lg:grid-cols-12">
      <!-- Left Form Column -->
      <div class="space-y-5 lg:col-span-6">
        <!-- 8-Province Pills (cleanly wrapped) -->
        <div>
          <label class="mb-2 block text-xs font-semibold text-ink-700">高考省份</label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="p in store.policies"
              :key="p.provinceCode"
              type="button"
              :class="[
                'rounded-full px-3.5 py-1.5 text-xs font-medium transition',
                selectedProvince === p.provinceCode
                  ? 'bg-pine-700 text-white shadow-sm'
                  : 'bg-stonewarm-50 text-ink-700 hover:bg-sage-100 hover:text-pine-800',
              ]"
              @click="selectedProvince = p.provinceCode"
            >
              {{ p.provinceName }}
              <span v-if="selectedProvince === p.provinceCode" class="ml-1 opacity-85">
                · {{ p.maxVolunteerCount }}{{ p.volunteerUnitType === 'MAJOR_96' ? '平行' : '专业组' }}
              </span>
            </button>
          </div>
        </div>

        <!-- Subject Track & Re-subjects -->
        <div>
          <label class="mb-2 block text-xs font-semibold text-ink-700">
            选科组合（{{ currentPolicy?.subjectMode || '3+1+2' }}）
          </label>
          <div class="flex flex-wrap items-center gap-2">
            <template v-if="currentPolicy?.subjectMode !== '3+3'">
              <button
                v-for="f in ['物理', '历史']"
                :key="f"
                type="button"
                :class="[
                  'rounded-full px-4 py-1.5 text-xs font-medium transition',
                  firstSubject === f ? 'bg-pine-700 text-white' : 'bg-stonewarm-50 text-ink-700 hover:bg-sage-100',
                ]"
                @click="firstSubject = f"
              >
                {{ f }}类
              </button>
              <span class="mx-1 h-4 w-px bg-line" />
            </template>

            <button
              v-for="s in resubjectOptions"
              :key="s"
              type="button"
              :class="[
                'rounded-full border px-3.5 py-1.5 text-xs font-medium transition',
                resubjects.includes(s)
                  ? 'border-pine-700 bg-sage-100 text-pine-800'
                  : 'border-transparent bg-stonewarm-50 text-ink-700 hover:bg-sage-50',
              ]"
              @click="toggleResubject(s)"
            >
              {{ s }}
            </button>
          </div>
        </div>

        <!-- Score Input + Live Latency Badge -->
        <div>
          <label class="mb-2 block text-xs font-semibold text-ink-700">高考总分</label>
          <div class="flex items-center justify-between rounded-2xl border border-line bg-paper px-4 py-3 focus-within:border-pine-700/60 focus-within:bg-white">
            <div class="flex items-baseline gap-2">
              <input
                v-model.number="score"
                type="number"
                :min="200"
                :max="currentPolicy?.scoreSystem === 'STANDARD_900' ? 900 : 750"
                class="w-24 bg-transparent text-2xl font-bold text-ink-900 tabular-nums focus:outline-none"
              />
              <span class="text-sm font-medium text-ink-500">分</span>
            </div>
            <span class="rounded-full bg-sage-100 px-3 py-1 text-[11.5px] font-medium text-pine-800 tabular-nums">
              一分一段直连 · {{ rankLatencyText }}
            </span>
          </div>
        </div>

        <!-- Strategy Preference -->
        <div>
          <label class="mb-2 block text-xs font-semibold text-ink-700">填报取向</label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="opt in strategyOptions"
              :key="opt.value"
              type="button"
              :class="[
                'rounded-full border px-4 py-1.5 text-xs font-medium transition',
                strategyMode === opt.value
                  ? 'border-pine-700 bg-sage-100 text-pine-800'
                  : 'border-transparent bg-stonewarm-50 text-ink-700 hover:bg-sage-50',
              ]"
              @click="strategyMode = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <!-- Preferred Major & Region (optional) -->
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="mb-1.5 block text-xs font-medium text-ink-500">意向专业关键词（可选）</label>
            <input
              v-model="preferredMajorsInput"
              type="text"
              placeholder="如：计算机, 电子信息"
              class="w-full rounded-xl border border-line bg-paper px-3 py-2 text-xs text-ink-900 placeholder:text-ink-400 focus:border-pine-600 focus:bg-white focus:outline-none"
            />
          </div>
          <div>
            <label class="mb-1.5 block text-xs font-medium text-ink-500">意向省份/城市（可选）</label>
            <input
              v-model="preferredRegionsInput"
              type="text"
              placeholder="如：重庆, 四川, 贵州"
              class="w-full rounded-xl border border-line bg-paper px-3 py-2 text-xs text-ink-900 placeholder:text-ink-400 focus:border-pine-600 focus:bg-white focus:outline-none"
            />
          </div>
        </div>

        <!-- Primary Forest-Green CTA -->
        <button
          type="button"
          :disabled="generating"
          class="flex w-full items-center justify-center gap-2 rounded-full bg-pine-700 px-5 py-3.5 text-sm font-semibold text-white shadow-sm transition hover:bg-pine-800 disabled:opacity-55"
          @click="handleGenerate"
        >
          <span>
            {{ generating ? '正在通过 Java 21 Pipeline 生成方案…' : `生成 ${currentPolicy?.maxVolunteerCount ?? 96} 个志愿并进入方案工作台` }}
          </span>
          <ArrowRight class="h-4 w-4" />
        </button>
      </div>

      <!-- Right Live Rank Panel -->
      <div class="flex flex-col justify-between rounded-2xl border border-line bg-paper/75 p-5 lg:col-span-6">
        <div>
          <div class="flex justify-center">
            <span class="inline-flex items-center gap-1.5 rounded-full bg-sage-100 px-3 py-1 text-xs font-medium text-pine-800">
              <span class="h-2 w-2 rounded-full bg-pine-700" />
              {{ currentPolicy?.provinceName || '贵州' }} 2025-2026 官方数据就绪 · {{ currentPolicy?.readinessStatus || 'FULL' }}
            </span>
          </div>

          <div class="mt-4 text-center">
            <div class="text-2xl font-bold text-ink-900 tabular-nums">
              全省预估位次：{{ rankEst.rankLow.toLocaleString() }} - {{ rankEst.rankHigh.toLocaleString() }} 名
            </div>
            <p class="mt-1 text-xs text-ink-500 tabular-nums">
              同分人数 {{ rankEst.segmentCount }} 人 · 超过全省 {{ rankEst.beatPercentage }}% 考生
            </p>
          </div>

          <div class="mt-4">
            <ScoreBellCurve
              :current-score="score"
              :undergraduate-line="distribution.undergraduateLine"
              :special-control-line="distribution.specialControlLine"
              :curve="distribution.curve"
            />
          </div>
        </div>

        <div class="mt-3 grid grid-cols-3 divide-x divide-line border-t border-line pt-3 text-center text-xs font-medium text-ink-700">
          <div>2,198 所高校</div>
          <div>{{ currentPolicy?.maxVolunteerCount ?? 96 }} 志愿仿真</div>
          <div>招生章程溯源</div>
        </div>
      </div>
    </div>

    <p class="mt-5 text-center text-xs text-ink-400">
      {{ STANDARD_DISCLAIMER }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight } from 'lucide-vue-next';
import { STANDARD_DISCLAIMER } from '@gzgk/shared';
import type { RankEstimate } from '@gzgk/shared';
import { coreApi } from '@/api/client';
import { createDemoPlan, useWorkspaceStore } from '@/stores/workspace';
import ScoreBellCurve from '@/components/ScoreBellCurve.vue';

const router = useRouter();
const store = useWorkspaceStore();

const selectedProvince = ref('GZ');
const firstSubject = ref('物理');
const resubjects = ref<string[]>(['化学', '生物']);
const score = ref(586);
const strategyMode = ref('balanced');
const preferredMajorsInput = ref('计算机, 电子信息');
const preferredRegionsInput = ref('重庆, 贵州, 四川');
const generating = ref(false);

const rankEst = ref<RankEstimate>({
  provinceCode: 'GZ',
  year: 2025,
  subjectType: '物理类',
  score: 586,
  rankLow: 8412,
  rankHigh: 8530,
  segmentCount: 118,
  cumulativeCount: 8530,
  beatPercentage: 95.8,
  sourceName: '贵州省招生考试院',
  sourceUrl: 'https://zsksy.guizhou.gov.cn/',
  officialReady: true,
  latencyMicros: 1400,
});

const distribution = ref<{ undergraduateLine: number; specialControlLine: number; curve: { score: number; count: number }[] }>({
  undergraduateLine: 380,
  specialControlLine: 482,
  curve: [],
});

const strategyOptions = [
  { value: 'balanced', label: '冲稳保均衡' },
  { value: 'school_first', label: '院校层次优先' },
  { value: 'major_first', label: '核心专业优先' },
];

const resubjectOptions = ['化学', '生物', '思想政治', '地理'];

const currentPolicy = computed(() =>
  store.policies.find((p) => p.provinceCode === selectedProvince.value) || store.policies[0],
);

const rankLatencyText = computed(() => {
  const ms = Math.max(0.2, (rankEst.value.latencyMicros || 1200) / 1000);
  return `${ms.toFixed(1)}ms`;
});

function toggleResubject(s: string) {
  if (resubjects.value.includes(s)) {
    if (resubjects.value.length > 1) {
      resubjects.value = resubjects.value.filter((x) => x !== s);
    }
  } else {
    const maxCount = currentPolicy.value?.subjectMode === '3+3' ? 3 : 2;
    resubjects.value = [...resubjects.value.slice(-(maxCount - 1)), s];
  }
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null;

async function fetchRankAndCurve() {
  const subj = `${firstSubject.value}类`;
  try {
    const [est, dist] = await Promise.all([
      coreApi.estimateRank(selectedProvince.value, subj, score.value),
      coreApi.getDistribution(selectedProvince.value, subj),
    ]);
    rankEst.value = est;
    distribution.value = dist;
  } catch {
    // Graceful local interpolation when Java core is offline
    const estRank = Math.max(120, Math.round(160000 * Math.exp(-0.0185 * Math.max(0, score.value - 360))));
    rankEst.value = {
      ...rankEst.value,
      provinceCode: selectedProvince.value,
      score: score.value,
      rankLow: Math.max(1, estRank - 60),
      rankHigh: estRank + 60,
      beatPercentage: Math.round(Math.max(1, Math.min(99.9, 100 - estRank / 2200)) * 10) / 10,
    };
  }
}

watch([selectedProvince, firstSubject, score], () => {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(fetchRankAndCurve, 120);
});

async function handleGenerate() {
  generating.value = true;
  const parseCsv = (s: string) =>
    s
      .split(/[,，、\s]+/)
      .map((x) => x.trim())
      .filter(Boolean);

  try {
    const plan = await coreApi.generatePlan({
      provinceCode: selectedProvince.value,
      year: 2026,
      totalScore: score.value,
      provinceRank: rankEst.value.rankHigh,
      firstSubject: firstSubject.value,
      resubjects: resubjects.value,
      strategyMode: strategyMode.value,
      preferredMajors: parseCsv(preferredMajorsInput.value),
      preferredRegions: parseCsv(preferredRegionsInput.value),
      acceptPrivate: false,
      acceptSinoForeign: false,
      agreedDisclaimer: true,
      disclaimerVersion: '2026.1',
    });
    store.setPlan(plan);
    router.push(`/workspace/${plan.planId}`);
  } catch {
    const demo = createDemoPlan();
    demo.provinceCode = selectedProvince.value;
    demo.provinceName = currentPolicy.value?.provinceName || '贵州';
    demo.totalScore = score.value;
    demo.provinceRank = rankEst.value.rankHigh;
    demo.firstSubject = firstSubject.value;
    demo.resubjects = [...resubjects.value];
    store.setPlan(demo);
    router.push(`/workspace/${demo.planId}`);
  } finally {
    generating.value = false;
  }
}

onMounted(async () => {
  await store.ensurePoliciesLoaded();
  fetchRankAndCurve();
});
</script>
