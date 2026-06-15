<script setup lang="ts">
import { ShieldAlert } from 'lucide-vue-next'

withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  points?: string[]
  confirmLabel?: string
}>(), {
  title: '生成前风险确认',
  points: () => [],
  confirmLabel: '我已知晓并同意：系统结果仅供参考，最终以官方信息为准。',
})

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

function toggle(e: Event) {
  emit('update:modelValue', (e.target as HTMLInputElement).checked)
}
</script>

<template>
  <section class="risk-card" :class="{ 'is-accepted': modelValue }">
    <div class="risk-card__head">
      <ShieldAlert :size="18" />
      <h3 class="risk-card__title">{{ title }}</h3>
    </div>
    <ul class="risk-card__points">
      <li v-for="(point, idx) in points" :key="idx">{{ point }}</li>
    </ul>
    <label class="risk-card__confirm">
      <input type="checkbox" :checked="modelValue" @change="toggle" />
      <span>{{ confirmLabel }}</span>
    </label>
  </section>
</template>

<style scoped>
.risk-card {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #fed7aa;
  background: linear-gradient(180deg, #fffbeb, #fff);
}
.risk-card.is-accepted {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #f0fdf4, #fff);
}
.risk-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #b45309;
}
.risk-card.is-accepted .risk-card__head {
  color: #047857;
}
.risk-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 850;
  color: #0f172a;
}
.risk-card__points {
  margin: 12px 0 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.risk-card__points li {
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}
.risk-card__confirm {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.1);
  cursor: pointer;
}
.risk-card__confirm input {
  width: 18px;
  height: 18px;
  margin-top: 1px;
  flex-shrink: 0;
}
.risk-card__confirm span {
  font-size: 13px;
  line-height: 1.6;
  font-weight: 700;
  color: #1f2937;
}
</style>
