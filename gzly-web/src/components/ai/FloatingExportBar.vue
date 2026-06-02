<template>
  <div class="bottom-bar">
    <button class="action-btn action-btn--primary" @click="$emit('export-image')">
      <Download :size="16" />
      导出志愿表长图
    </button>
    <button class="action-btn action-btn--outline" :disabled="streaming" @click="$emit('reanalyze')">
      <RefreshCw :size="16" />
      {{ streaming ? '分析中…' : '重新分析' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { Download, RefreshCw } from 'lucide-vue-next'

defineProps<{ streaming: boolean }>()
defineEmits<{ 'export-image': []; reanalyze: [] }>()
</script>
