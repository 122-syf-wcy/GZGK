<template>
  <section id="report-overview" class="summary-grid">
    <div class="summary-card summary-card--wide">
      <div class="summary-head"><Sparkles :size="16" /> 先看结论</div>
      <p>{{ summary }}</p>
      <div class="report-overview__meta">
        <span v-for="item in metrics" :key="item.label" class="report-overview__chip">
          <strong>{{ item.label }}</strong>
          <span>{{ item.value }}</span>
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Sparkles } from 'lucide-vue-next'

defineProps<{
  summary: string
  metrics: Array<{ label: string; value: string }>
}>()
</script>
