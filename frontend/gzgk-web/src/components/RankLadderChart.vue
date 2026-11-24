<template>
  <div class="relative w-full">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <h3 class="text-[15px] font-semibold text-ink-900">96 志愿位次阶梯曲线</h3>
      <div class="flex items-center gap-4 text-xs text-ink-500">
        <span class="inline-flex items-center gap-1.5">
          <span class="h-2.5 w-2.5 rounded-full bg-terracotta-600" /> 冲刺
        </span>
        <span class="inline-flex items-center gap-1.5">
          <span class="h-2.5 w-2.5 rounded-full bg-pine-700" /> 稳妥
        </span>
        <span class="inline-flex items-center gap-1.5">
          <span class="h-2.5 w-2.5 rounded-full bg-sage-500" /> 保底/垫底
        </span>
      </div>
    </div>

    <svg viewBox="0 0 640 270" class="w-full overflow-visible">
      <!-- Horizontal Gridlines -->
      <g v-for="tick in yTicks" :key="tick">
        <line x1="62" :y1="yOf(tick)" x2="622" :y2="yOf(tick)" stroke="#ECEFE9" stroke-width="1" />
        <text x="54" :y="yOf(tick) + 4" text-anchor="end" fill="#8A958D" font-size="11" class="tabular-nums">
          {{ tick.toLocaleString() }}名
        </text>
      </g>

      <!-- Candidate Rank Reference Dashed Line -->
      <line
        x1="62"
        :y1="yOf(candidateRank)"
        x2="622"
        :y2="yOf(candidateRank)"
        stroke="#2D523A"
        stroke-dasharray="5 5"
        stroke-width="1.3"
      />
      <text
        x="618"
        :y="yOf(candidateRank) - 7"
        text-anchor="end"
        fill="#2D523A"
        font-size="11.5"
        font-weight="600"
        class="tabular-nums"
      >
        考生位次 {{ candidateRank.toLocaleString() }}名
      </text>

      <!-- Volunteer Rank Points -->
      <g v-for="pt in sampledItems" :key="pt.itemId">
        <circle
          :cx="xOf(pt.volunteerIndex)"
          :cy="yOf(pt.predictedMinRank)"
          :r="hovered?.itemId === pt.itemId || pt.aiHighlighted ? 5.5 : 3.6"
          :fill="colorFor(pt.gradient)"
          :stroke="pt.aiHighlighted ? '#2D523A' : '#FFFFFF'"
          :stroke-width="pt.aiHighlighted ? 2.5 : 1"
          class="cursor-pointer transition-all"
          @mouseenter="hovered = pt"
          @mouseleave="hovered = null"
          @click="$emit('selectItem', pt)"
        />
      </g>

      <!-- X-Axis Ticks -->
      <g v-for="idx in xTicks" :key="idx">
        <text :x="xOf(idx)" y="256" text-anchor="middle" fill="#8A958D" font-size="11" class="tabular-nums">
          #{{ idx }}
        </text>
      </g>
    </svg>

    <!-- Hover Tooltip -->
    <div
      v-if="hovered"
      class="mt-2 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-line bg-paper px-4 py-2 text-xs text-ink-700"
    >
      <span class="font-medium text-ink-900">
        #{{ hovered.volunteerIndex }} {{ hovered.universityName }} · {{ hovered.majorName }}
      </span>
      <span class="tabular-nums">
        预测参考位次 {{ hovered.predictedMinRank.toLocaleString() }}名 · 机会指数 {{ hovered.chanceScore }}（{{ hovered.chanceLevel }}）
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { PlanItem } from '@gzgk/shared';

const props = defineProps<{
  items: PlanItem[];
  candidateRank: number;
}>();

defineEmits<{
  selectItem: [item: PlanItem];
}>();

const hovered = ref<PlanItem | null>(null);

const sampledItems = computed(() => {
  if (props.items.length <= 48) return props.items;
  return props.items.filter((_, idx) => idx % 2 === 0 || idx === props.items.length - 1);
});

const minRankVal = computed(() => {
  const vals = props.items.map((i) => i.predictedMinRank).filter((n) => n > 0);
  return Math.max(100, Math.floor((Math.min(...vals, props.candidateRank) * 0.85) / 500) * 500);
});

const maxRankVal = computed(() => {
  const vals = props.items.map((i) => i.predictedMinRank).filter((n) => n > 0);
  return Math.ceil((Math.max(...vals, props.candidateRank * 1.6) * 1.05) / 500) * 500;
});

const yTicks = computed(() => {
  const lo = minRankVal.value;
  const hi = maxRankVal.value;
  const step = Math.max(500, Math.round((hi - lo) / 4 / 500) * 500);
  return [lo, lo + step, lo + step * 2, lo + step * 3, hi];
});

const xTicks = computed(() => {
  const n = Math.max(props.items.length, 45);
  return [1, Math.round(n * 0.2), Math.round(n * 0.4), Math.round(n * 0.6), Math.round(n * 0.8), n];
});

function xOf(idx: number) {
  const n = Math.max(props.items.length, 1);
  return 72 + ((idx - 1) / Math.max(1, n - 1)) * 544;
}

function yOf(rank: number) {
  const lo = minRankVal.value;
  const hi = Math.max(lo + 1000, maxRankVal.value);
  const clamped = Math.max(lo, Math.min(hi, rank));
  return 20 + ((clamped - lo) / (hi - lo)) * 212;
}

function colorFor(g: string) {
  if (g === '冲') return '#C85A42';
  if (g === '稳') return '#2D523A';
  return '#4F8A6B';
}
</script>
