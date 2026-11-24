<template>
  <div class="flex flex-col items-center justify-center">
    <svg viewBox="0 0 180 100" class="h-20 w-36 overflow-visible">
      <!-- Track -->
      <path
        d="M 20 88 A 70 70 0 0 1 160 88"
        fill="none"
        stroke="#E5E8E2"
        stroke-width="11"
        stroke-linecap="round"
      />
      <!-- Botanical Pine Active Arc -->
      <path
        d="M 20 88 A 70 70 0 0 1 160 88"
        fill="none"
        stroke="#2D523A"
        stroke-width="11"
        stroke-linecap="round"
        :stroke-dasharray="arcLen"
        :stroke-dashoffset="dashOffset"
        class="transition-all duration-700 ease-out"
      />
      <!-- Needle tip dot -->
      <circle :cx="tipX" :cy="tipY" r="5" fill="#FFFFFF" stroke="#2D523A" stroke-width="3" />
    </svg>
    <div class="-mt-5 text-center">
      <div class="text-lg font-semibold text-ink-900 tabular-nums">
        整表投档参考度 {{ pctText }}%
      </div>
      <div class="text-[11px] text-ink-400">2000 次蒙特卡洛组合仿真</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  hitRate: number; // 0..1
}>();

const clamped = computed(() => Math.max(0.05, Math.min(0.999, props.hitRate || 0)));
const arcLen = Math.PI * 70;
const dashOffset = computed(() => arcLen * (1 - clamped.value));
const angleRad = computed(() => Math.PI * (1 - clamped.value));
const tipX = computed(() => 90 + 70 * Math.cos(angleRad.value));
const tipY = computed(() => 88 - 70 * Math.sin(angleRad.value));
const pctText = computed(() => (clamped.value * 100).toFixed(1));
</script>
