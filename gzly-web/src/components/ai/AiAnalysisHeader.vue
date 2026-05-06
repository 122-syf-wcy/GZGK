<template>
  <header class="page-header">
    <div class="page-header-inner">
      <button class="back-btn" aria-label="返回上一页" @click="$emit('back')">
        <ArrowLeft :size="20" />
      </button>
      <h1 class="page-header-title">AI 深度解读</h1>
    </div>
    <div class="reading-progress" aria-hidden="true">
      <span :style="{ width: `${Math.round(progress * 100)}%` }"></span>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'

defineProps<{ progress: number }>()
defineEmits<{ back: [] }>()
</script>

<style scoped>
.page-header {
  position: sticky;
  top: 0;
  z-index: var(--gz-z-sticky);
  background: rgba(250, 248, 242, 0.94);
  border-bottom: 1px solid rgba(31, 41, 51, 0.1);
  backdrop-filter: blur(10px);
}
.page-header-inner {
  max-width: var(--gz-desktop-max);
  margin: 0 auto;
  padding: var(--gz-space-3) var(--gz-space-4);
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
}
.reading-progress {
  height: 2px;
  background: rgba(31, 41, 51, 0.08);
}
.reading-progress span {
  display: block;
  height: 100%;
  width: 0;
  background: #1f2933;
  transition: width 0.16s ease-out;
}
.back-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 999px;
  background: #ede7db;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1f2933;
  box-shadow: inset 0 0 0 1px rgba(31, 41, 51, 0.08);
}
.page-header-title {
  font-size: 18px;
  font-weight: 800;
  color: #1f2933;
}
</style>
