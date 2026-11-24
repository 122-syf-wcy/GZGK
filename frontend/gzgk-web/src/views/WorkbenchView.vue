<template>
  <div class="flex h-full min-h-0 flex-col gap-4 p-5 lg:flex-row">
    <!-- LEFT PANE (62%): Structured 96-Volunteer Virtualized Decision Workbench -->
    <div class="flex min-h-0 flex-1 flex-col gap-3.5 lg:w-[62%]">
      <!-- Top Action & Metrics Card -->
      <div class="rounded-2xl border border-line bg-white p-4 shadow-editorial">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2 text-xs text-ink-500">
            <span class="rounded-full bg-sage-100 px-2.5 py-0.5 font-medium text-pine-800">
              {{ plan.metrics.predictionEngine === 'rule-log-rank-fallback' ? 'Log-Rank 校准引擎' : plan.metrics.predictionEngine }}
            </span>
            <span class="tabular-nums">生成耗时 {{ plan.metrics.pipelineTimeMs }}ms</span>
            <span v-if="plan.metrics.cacheHit" class="rounded bg-ochre-50 px-2 py-0.5 text-ochre-600">
              热点缓存复用
            </span>
          </div>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-full border border-line bg-white px-3 py-1.5 text-xs font-medium text-ink-700 transition hover:bg-stonewarm-50"
              @click="handleRollback"
            >
              <RotateCcw class="h-3.5 w-3.5" />
              <span>撤销</span>
            </button>
            <router-link
              :to="`/report/${plan.planId}`"
              class="inline-flex items-center gap-1 rounded-full border border-line bg-white px-3 py-1.5 text-xs font-medium text-ink-700 transition hover:border-pine-500/40 hover:text-pine-800"
            >
              <Sparkles class="h-3.5 w-3.5 text-pine-700" />
              <span>深度诊断</span>
            </router-link>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-full bg-pine-700 px-3.5 py-1.5 text-xs font-medium text-white transition hover:bg-pine-800"
              @click="exportExcel"
            >
              <Download class="h-3.5 w-3.5" />
              <span>导出 Excel</span>
            </button>
          </div>
        </div>

        <div class="grid items-center gap-4 border-t border-line/70 pt-3.5 md:grid-cols-12">
          <!-- Segmented Gradient Progress Bar -->
          <div class="md:col-span-7">
            <div class="flex h-3.5 w-full overflow-hidden rounded-full bg-stonewarm-100">
              <div class="bg-terracotta-600 transition-all" :style="{ width: pct(plan.gradientCounts.sprint) }" />
              <div class="bg-pine-700 transition-all" :style="{ width: pct(plan.gradientCounts.steady) }" />
              <div class="bg-sage-500 transition-all" :style="{ width: pct(plan.gradientCounts.safe) }" />
              <div class="bg-stonewarm-500 transition-all" :style="{ width: pct(plan.gradientCounts.pad) }" />
            </div>
            <div class="mt-2.5 flex flex-wrap items-center justify-between gap-2 text-xs text-ink-700 tabular-nums">
              <span class="inline-flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-terracotta-600" />冲 <strong>{{ plan.gradientCounts.sprint }}</strong>
              </span>
              <span class="inline-flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-pine-700" />稳 <strong>{{ plan.gradientCounts.steady }}</strong>
              </span>
              <span class="inline-flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-sage-500" />保 <strong>{{ plan.gradientCounts.safe }}</strong>
              </span>
              <span class="inline-flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-stonewarm-500" />垫 <strong>{{ plan.gradientCounts.pad }}</strong>
              </span>
            </div>
          </div>

          <!-- Monte Carlo Gauge -->
          <div class="border-line/70 md:col-span-5 md:border-l md:pl-4">
            <MonteCarloGauge :hit-rate="plan.listHitRate" />
          </div>
        </div>
      </div>

      <!-- Filter Pills & Search -->
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="tab in filterTabs"
            :key="tab.key"
            type="button"
            :class="[
              'rounded-full border px-3 py-1 text-xs font-medium transition tabular-nums',
              activeFilter === tab.key
                ? 'border-pine-700 bg-sage-100 text-pine-800'
                : 'border-line bg-white text-ink-700 hover:bg-sage-50',
            ]"
            @click="activeFilter = tab.key"
          >
            {{ tab.label }} ({{ tab.count }})
          </button>
        </div>

        <input
          v-model="searchKw"
          type="text"
          placeholder="筛选院校 / 专业 / 城市…"
          class="w-48 rounded-full border border-line bg-white px-3.5 py-1.5 text-xs text-ink-900 placeholder:text-ink-400 focus:border-pine-600 focus:outline-none"
        />
      </div>

      <!-- Virtualized 96-Volunteer List (@tanstack/vue-virtual -> 60fps smooth scrolling) -->
      <div ref="listParentRef" class="min-h-0 flex-1 overflow-y-auto pr-1">
        <div :style="{ height: `${rowVirtualizer.getTotalSize()}px`, position: 'relative', width: '100%' }">
          <div
            v-for="vRow in rowVirtualizer.getVirtualItems()"
            :key="String(vRow.key)"
            :style="{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${vRow.start}px)`,
            }"
            class="pb-2.5"
          >
            <article
              v-if="filteredItems[vRow.index]"
              :class="[
                'relative flex flex-wrap items-center justify-between gap-3 rounded-2xl border px-4 py-3.5 transition',
                filteredItems[vRow.index].aiHighlighted
                  ? 'border-[1.5px] border-pine-700 bg-sage-50/70 shadow-editorial'
                  : 'border-line bg-white hover:border-pine-500/35',
              ]"
            >
              <span
                v-if="filteredItems[vRow.index].aiHighlighted"
                class="absolute -top-2.5 right-4 rounded-full bg-pine-700 px-2.5 py-0.5 text-[10.5px] font-medium text-white shadow-sm"
              >
                AI 建议新增
              </span>

              <div class="flex min-w-0 flex-1 items-center gap-3">
                <span class="w-9 shrink-0 font-mono text-sm font-semibold text-ink-700 tabular-nums">
                  #{{ String(filteredItems[vRow.index].volunteerIndex).padStart(2, '0') }}
                </span>
                <span :class="['flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-xs font-semibold', tierClass(filteredItems[vRow.index].gradient)]">
                  {{ filteredItems[vRow.index].gradient }}
                </span>
                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-1.5">
                    <span class="truncate text-[14.5px] font-semibold text-ink-900">
                      {{ filteredItems[vRow.index].universityName }}
                    </span>
                    <span
                      v-for="tag in filteredItems[vRow.index].schoolTags.slice(0, 2)"
                      :key="tag"
                      class="rounded bg-stonewarm-50 px-1.5 py-0.5 text-[10.5px] text-ink-500"
                    >
                      {{ tag }}
                    </span>
                  </div>
                  <div class="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-ink-500">
                    <span class="font-medium text-ink-700">{{ filteredItems[vRow.index].majorName }}</span>
                    <span>· {{ filteredItems[vRow.index].city || filteredItems[vRow.index].province }}</span>
                    <span v-if="filteredItems[vRow.index].planChangeRate <= -0.25" class="rounded bg-terracotta-50 px-1.5 py-0.5 text-[10.5px] font-medium text-terracotta-700">
                      计划缩减 {{ Math.abs(Math.round(filteredItems[vRow.index].planChangeRate * 100)) }}%
                    </span>
                    <span v-else-if="filteredItems[vRow.index].needsManualReview" class="rounded bg-ochre-50 px-1.5 py-0.5 text-[10.5px] font-medium text-ochre-600">
                      需核验章程
                    </span>
                  </div>
                </div>
              </div>

              <div class="flex items-center gap-4 text-right tabular-nums">
                <div>
                  <div class="text-xs font-medium text-ink-900">
                    25年最低 {{ filteredItems[vRow.index].latestMinRank.toLocaleString() }}位
                  </div>
                  <div class="text-[11px] text-ink-400">
                    {{ filteredItems[vRow.index].latestMinScore }}分 · 计划 {{ filteredItems[vRow.index].planCount }}人
                  </div>
                </div>
                <div class="w-20">
                  <div class="text-xs font-semibold text-pine-800">
                    机会指数 {{ filteredItems[vRow.index].chanceScore }}
                  </div>
                  <div class="text-[11px] text-ink-400">{{ filteredItems[vRow.index].chanceLevel }}</div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>

    <!-- RIGHT PANE (38%): Embedded LangGraph AI Copilot -->
    <div class="h-[540px] shrink-0 lg:h-full lg:w-[38%] lg:max-w-[440px]">
      <CopilotPanel @plan-updated="onPlanUpdated" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useVirtualizer } from '@tanstack/vue-virtual';
import { Download, RotateCcw, Sparkles } from 'lucide-vue-next';
import * as XLSX from 'xlsx';
import { coreApi } from '@/api/client';
import { useWorkspaceStore } from '@/stores/workspace';
import CopilotPanel from '@/components/CopilotPanel.vue';
import MonteCarloGauge from '@/components/MonteCarloGauge.vue';

const route = useRoute();
const store = useWorkspaceStore();

const activeFilter = ref<'all' | '冲' | '稳' | '保垫' | 'review' | 'shrink'>('all');
const searchKw = ref('');
const listParentRef = ref<HTMLElement | null>(null);

const plan = computed(() => store.activePlan);

const filterTabs = computed(() => {
  const items = plan.value.items;
  return [
    { key: 'all' as const, label: '全部', count: items.length },
    { key: '冲' as const, label: '冲', count: items.filter((i) => i.gradient === '冲').length },
    { key: '稳' as const, label: '稳', count: items.filter((i) => i.gradient === '稳').length },
    { key: '保垫' as const, label: '保/垫', count: items.filter((i) => i.gradient === '保' || i.gradient === '垫').length },
    { key: 'review' as const, label: '章程核验', count: items.filter((i) => i.needsManualReview).length },
    { key: 'shrink' as const, label: '缩招预警', count: items.filter((i) => i.planChangeRate <= -0.25).length },
  ];
});

const filteredItems = computed(() => {
  const kw = searchKw.value.trim().toLowerCase();
  return plan.value.items.filter((item) => {
    if (activeFilter.value === '冲' && item.gradient !== '冲') return false;
    if (activeFilter.value === '稳' && item.gradient !== '稳') return false;
    if (activeFilter.value === '保垫' && item.gradient !== '保' && item.gradient !== '垫') return false;
    if (activeFilter.value === 'review' && !item.needsManualReview) return false;
    if (activeFilter.value === 'shrink' && item.planChangeRate > -0.25) return false;
    if (!kw) return true;
    return (
      item.universityName.toLowerCase().includes(kw) ||
      item.majorName.toLowerCase().includes(kw) ||
      item.city.toLowerCase().includes(kw) ||
      item.province.toLowerCase().includes(kw)
    );
  });
});

const rowVirtualizer = useVirtualizer(
  computed(() => ({
    count: filteredItems.value.length,
    getScrollElement: () => listParentRef.value,
    estimateSize: () => 84,
    overscan: 8,
  })),
);

function pct(n: number) {
  const total = Math.max(1, plan.value.items.length);
  return `${Math.round((n / total) * 100)}%`;
}

function tierClass(g: string) {
  if (g === '冲') return 'bg-terracotta-50 text-terracotta-700';
  if (g === '稳') return 'bg-sage-100 text-pine-800';
  if (g === '保') return 'bg-[#EDF6F0] text-sage-600';
  return 'bg-stonewarm-100 text-stonewarm-500';
}

async function handleRollback() {
  try {
    const res = await coreApi.rollbackPlan(plan.value.planId);
    store.setPlan(res.updatedPlan);
  } catch {
    // No backend mutation history or offline demo mode
  }
}

function exportExcel() {
  const rows = plan.value.items.map((i) => ({
    志愿序号: i.volunteerIndex,
    梯度: i.gradient,
    院校名称: i.universityName,
    专业名称: i.majorName,
    城市: i.city || i.province,
    性质: i.schoolNature,
    参考学费: i.tuition,
    近一年最低分: i.latestMinScore,
    近一年最低位次: i.latestMinRank,
    机会指数: i.chanceScore,
    参考等级: i.chanceLevel,
    核验提醒: i.manualReviewReason || '正常',
  }));
  const ws = XLSX.utils.json_to_sheet(rows);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, '志愿方案');
  XLSX.writeFile(wb, `GZGK_${plan.value.provinceCode}_${plan.value.totalScore}分_v${plan.value.version}.xlsx`);
}

function onPlanUpdated() {
  // Triggers reactivity smoothly
}

onMounted(() => {
  const pid = route.params.planId as string | undefined;
  if (pid && pid !== plan.value.planId) {
    store.refreshPlan(pid);
  }
});
</script>
