<template>
  <aside class="report-nav gz-card">
    <div class="report-nav__title">解读目录</div>
    <div class="report-nav__desc">先看结论，再看正文和附录。</div>
    <div class="report-nav__progress">
      <span>阅读进度</span>
      <strong>{{ Math.round(progress * 100) }}%</strong>
    </div>
    <button
      v-for="anchor in anchors"
      :key="anchor.id"
      class="report-nav__item"
      :class="{ active: activeSection === anchor.id }"
      :title="anchor.label"
      @click="$emit('scroll-to', anchor.id)"
    >
      {{ anchor.label }}
    </button>
  </aside>
</template>

<script setup lang="ts">
defineProps<{
  anchors: Array<{ id: string; label: string }>
  activeSection: string
  progress: number
}>()

defineEmits<{ 'scroll-to': [id: string] }>()
</script>

<style scoped>
.report-nav {
  position: sticky;
  top: 92px;
  align-self: flex-start;
  max-height: calc(100dvh - 116px);
  overflow: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.1);
  box-shadow: 0 10px 28px rgba(31, 41, 51, 0.06);
}
.report-nav__title {
  font-size: 15px;
  font-weight: 900;
  color: #1f2933;
}
.report-nav__desc {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}
.report-nav__progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 6px;
  padding: 8px 10px;
  border-radius: 12px;
  background: #f4f0e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
}
.report-nav__progress span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}
.report-nav__progress strong {
  color: #1f2933;
  font-size: 14px;
  font-weight: 900;
}
.report-nav__item {
  position: relative;
  min-height: 34px;
  padding: 0 12px 0 18px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  text-align: left;
  line-height: 1.35;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.report-nav__item::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 50%;
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
  transform: translateY(-50%);
}
.report-nav__item:hover {
  background: #f7f2e8;
  color: #1f2933;
}
.report-nav__item.active {
  background: #efe7d8;
  border-color: rgba(31, 41, 51, 0.12);
  color: #1f2933;
}
.report-nav__item.active::before {
  height: 18px;
  background: #1f2933;
}
</style>
