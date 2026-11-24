<template>
  <div class="w-full">
    <svg viewBox="0 0 420 180" class="h-44 w-full">
      <defs>
        <linearGradient id="pineCurveGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#2D523A" stop-opacity="0.34" />
          <stop offset="100%" stop-color="#2D523A" stop-opacity="0.03" />
        </linearGradient>
      </defs>

      <!-- Baseline -->
      <line x1="20" y1="152" x2="400" y2="152" stroke="#E5E8E2" stroke-width="1" />

      <!-- Undergraduate Line Marker -->
      <line :x1="xOf(undergraduateLine)" y1="42" :x2="xOf(undergraduateLine)" y2="152" stroke="#A5B0A8" stroke-dasharray="4 4" stroke-width="1" />
      <text :x="xOf(undergraduateLine)" y="26" text-anchor="middle" fill="#6B776E" font-size="10">本科线</text>
      <text :x="xOf(undergraduateLine)" y="38" text-anchor="middle" fill="#6B776E" font-size="10" class="tabular-nums">{{ undergraduateLine }}分</text>

      <!-- Special Control Line Marker -->
      <line :x1="xOf(specialControlLine)" y1="42" :x2="xOf(specialControlLine)" y2="152" stroke="#A5B0A8" stroke-dasharray="4 4" stroke-width="1" />
      <text :x="xOf(specialControlLine)" y="26" text-anchor="middle" fill="#6B776E" font-size="10">特控线</text>
      <text :x="xOf(specialControlLine)" y="38" text-anchor="middle" fill="#6B776E" font-size="10" class="tabular-nums">{{ specialControlLine }}分</text>

      <!-- Area & Curve -->
      <path :d="areaPath" fill="url(#pineCurveGrad)" />
      <path :d="linePath" fill="none" stroke="#2D523A" stroke-width="2.2" stroke-linecap="round" />

      <!-- Candidate Score Pin -->
      <line :x1="xOf(currentScore)" y1="62" :x2="xOf(currentScore)" y2="152" stroke="#2D523A" stroke-width="2" stroke-opacity="0.65" />
      <circle :cx="xOf(currentScore)" :cy="yOfScore(currentScore)" r="5" fill="#FFFFFF" stroke="#2D523A" stroke-width="2.5" />
      <g :transform="`translate(${Math.min(358, Math.max(58, xOf(currentScore)))}, 50)`">
        <rect x="-28" y="-12" width="56" height="22" rx="8" fill="#2D523A" />
        <text x="0" y="3" text-anchor="middle" fill="#FFFFFF" font-size="11" font-weight="600" class="tabular-nums">{{ currentScore }}分</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    currentScore: number;
    undergraduateLine?: number;
    specialControlLine?: number;
    curve?: { score: number; count: number }[];
  }>(),
  {
    undergraduateLine: 380,
    specialControlLine: 482,
    curve: () => [],
  },
);

const minScore = 240;
const maxScore = 700;

const normalizedPoints = computed(() => {
  if (props.curve && props.curve.length >= 6) {
    const sorted = [...props.curve].sort((a, b) => a.score - b.score);
    const peak = Math.max(...sorted.map((p) => p.count), 1);
    return sorted.map((p) => ({ score: p.score, ratio: p.count / peak }));
  }
  const pts: { score: number; ratio: number }[] = [];
  for (let s = minScore; s <= maxScore; s += 10) {
    const ratio = Math.exp(-Math.pow((s - 472) / 86, 2));
    pts.push({ score: s, ratio });
  }
  return pts;
});

function xOf(score: number) {
  const clamped = Math.max(minScore, Math.min(maxScore, score));
  return 24 + ((clamped - minScore) / (maxScore - minScore)) * 372;
}

function yOfRatio(ratio: number) {
  return 152 - Math.max(0.02, Math.min(1, ratio)) * 96;
}

function yOfScore(score: number) {
  const pts = normalizedPoints.value;
  let best = pts[0];
  let dist = Infinity;
  for (const p of pts) {
    const d = Math.abs(p.score - score);
    if (d < dist) {
      dist = d;
      best = p;
    }
  }
  return yOfRatio(best?.ratio ?? 0.35);
}

const linePath = computed(() =>
  normalizedPoints.value
    .map((p, idx) => `${idx === 0 ? 'M' : 'L'} ${xOf(p.score).toFixed(1)} ${yOfRatio(p.ratio).toFixed(1)}`)
    .join(' '),
);

const areaPath = computed(() => {
  const pts = normalizedPoints.value;
  if (!pts.length) return '';
  const firstX = xOf(pts[0].score).toFixed(1);
  const lastX = xOf(pts[pts.length - 1].score).toFixed(1);
  return `${linePath.value} L ${lastX} 152 L ${firstX} 152 Z`;
});
</script>
