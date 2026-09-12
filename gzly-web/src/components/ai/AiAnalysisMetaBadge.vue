<template>
  <section v-if="modelLabel || hasIssues" class="ai-meta-card" :class="{ 'is-fallback': fallback }">
    <header class="ai-meta-card__head">
      <span class="ai-meta-card__pill" :class="fallback ? 'pill-warn' : 'pill-ok'">
        <Bot :size="14" />
        {{ fallback ? '规则模板兜底' : 'AI 模型解读' }}
      </span>
      <span v-if="modelLabel" class="ai-meta-card__model">{{ modelLabel }}</span>
    </header>
    <p v-if="fallback && fallbackReason" class="ai-meta-card__reason">
      <AlertTriangle :size="12" />
      <span>{{ fallbackReason }}</span>
    </p>
    <div v-if="hasIssues" class="ai-meta-card__issues" role="alert">
      <header>
        <ShieldAlert :size="13" />
        <strong>数据质量提示</strong>
        <span class="ai-meta-card__issues-count">{{ issues.length }} 条</span>
      </header>
      <ul>
        <li v-for="(text, idx) in issues" :key="`ai-issue-${idx}`">{{ text }}</li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, Bot, ShieldAlert } from 'lucide-vue-next'

const props = defineProps<{
  modelVersion?: string
  fallback?: boolean
  fallbackReason?: string
  issues?: string[]
}>()

const issues = computed(() => props.issues ?? [])
const hasIssues = computed(() => issues.value.length > 0)
const modelLabel = computed(() => {
  const raw = (props.modelVersion ?? '').trim()
  if (!raw) return ''
  if (raw === 'rule-template-v1') return '规则模板 v1'
  return raw
})
</script>

<style scoped>
.ai-meta-card {
  margin-top: 12px;
  padding: 14px 16px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  border-radius: 14px;
  display: grid;
  gap: 10px;
}
.ai-meta-card.is-fallback {
  background: #fff7ed;
  border-color: rgba(202, 138, 4, 0.35);
}
.ai-meta-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.ai-meta-card__pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}
.pill-ok {
  background: #f4f4f2;
  color: #4b4d54;
  border: 1px solid rgba(14, 116, 144, 0.25);
}
.pill-warn {
  background: #f3ecd9;
  color: #7c5f33;
  border: 1px solid rgba(146, 64, 14, 0.3);
}
.ai-meta-card__model {
  font-size: 12px;
  color: #4b4d54;
  font-weight: 700;
  background: #f7f2e8;
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid rgba(31, 41, 51, 0.08);
}
.ai-meta-card__reason {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.55;
  color: #7c5f33;
}
.ai-meta-card__issues {
  border-top: 1px dashed rgba(31, 41, 51, 0.12);
  padding-top: 10px;
}
.ai-meta-card__issues > header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 13px;
  color: #1f2933;
}
.ai-meta-card__issues-count {
  margin-left: auto;
  font-size: 11px;
  color: #6a6c72;
  font-weight: 700;
}
.ai-meta-card__issues ul {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 4px;
}
.ai-meta-card__issues li {
  font-size: 13px;
  line-height: 1.6;
  color: #4b4d54;
}
</style>
