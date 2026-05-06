<template>
  <div class="floating-export-bar" :class="{ 'is-streaming': streaming }">
    <header class="floating-export-bar__head">
      <Sparkles :size="13" />
      <span>快捷操作</span>
    </header>
    <div class="floating-export-bar__actions">
      <button
        type="button"
        class="floating-export-btn floating-export-btn--primary"
        :disabled="streaming"
        @click="$emit('export-image')"
      >
        <Download :size="16" />
        <span class="floating-export-btn__label">导出志愿表长图</span>
        <span class="floating-export-btn__hint">PNG · 含免责水印</span>
      </button>
      <button
        type="button"
        class="floating-export-btn floating-export-btn--ghost"
        :disabled="streaming"
        @click="$emit('reanalyze')"
      >
        <RefreshCw :size="15" :class="{ 'is-spin': streaming }" />
        <span class="floating-export-btn__label">{{ streaming ? '分析中…' : '重新分析' }}</span>
        <span class="floating-export-btn__hint">基于当前方案重出一份</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Download, RefreshCw, Sparkles } from 'lucide-vue-next'

defineProps<{ streaming: boolean }>()
defineEmits<{ 'export-image': []; reanalyze: [] }>()
</script>

<style scoped>
.floating-export-bar {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 16px);
  z-index: 80;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fffdf7 0%, #fbf6e8 100%);
  border: 1px solid rgba(95, 70, 48, 0.18);
  box-shadow: 0 18px 40px rgba(31, 41, 51, 0.18), 0 2px 6px rgba(31, 41, 51, 0.06);
  backdrop-filter: blur(8px);
}
.floating-export-bar.is-streaming {
  border-color: rgba(180, 83, 9, 0.4);
  background: linear-gradient(180deg, #fff7ed 0%, #fef3c7 100%);
}
.floating-export-bar__head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: #5f4630;
  text-transform: uppercase;
}
.floating-export-bar__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.floating-export-btn {
  position: relative;
  min-height: 56px;
  padding: 8px 12px 8px 38px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: #fffdf7;
  color: #1f2933;
  text-align: left;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  font-size: 13px;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}
.floating-export-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(31, 41, 51, 0.1);
}
.floating-export-btn:active:not(:disabled) {
  transform: translateY(0);
}
.floating-export-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.floating-export-btn > svg {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
}
.floating-export-btn__label {
  display: block;
  font-weight: 800;
  font-size: 14px;
  line-height: 1.2;
}
.floating-export-btn__hint {
  display: block;
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
  line-height: 1.3;
}
.floating-export-btn--primary {
  background: linear-gradient(140deg, #1f2933 0%, #0f172a 100%);
  color: #fffdf7;
  border-color: rgba(15, 23, 42, 0.6);
}
.floating-export-btn--primary .floating-export-btn__hint {
  color: rgba(255, 253, 247, 0.7);
}
.floating-export-btn--ghost {
  background: #f7f2e8;
  color: #1f2933;
  border-color: rgba(31, 41, 51, 0.12);
}
.floating-export-btn .is-spin {
  animation: floating-spin 0.9s linear infinite;
}
@keyframes floating-spin {
  from { transform: translateY(-50%) rotate(0); }
  to { transform: translateY(-50%) rotate(360deg); }
}

@media (min-width: 1024px) {
  .floating-export-bar {
    left: auto;
    right: 24px;
    bottom: 24px;
    width: 360px;
  }
}
</style>
