<template>
  <div id="report-body" ref="contentRef" class="ai-content-area">
    <div class="chat-container">
      <div class="ai-avatar-row">
        <div class="ai-avatar"><Bot :size="18" /></div>
        <div class="ai-meta">
          <span class="ai-label">志愿分析助手</span>
          <span class="ai-status">
            <span v-if="streaming" class="status-dot status-dot--active"></span>
            <span v-else class="status-dot status-dot--done"></span>
            {{ streaming ? '分析中...' : '分析完成' }}
          </span>
        </div>
      </div>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Bot } from 'lucide-vue-next'

defineProps<{ streaming: boolean }>()
</script>
