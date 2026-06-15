<script setup lang="ts">
import { ref } from 'vue'
import { Info } from 'lucide-vue-next'
import DisclaimerModal from './DisclaimerModal.vue'
import { SHORT_DISCLAIMER } from '@/constants/disclaimer'

withDefaults(defineProps<{
  text?: string
  tone?: 'info' | 'warn' | 'muted'
  expandable?: boolean
}>(), {
  text: SHORT_DISCLAIMER,
  tone: 'info',
  expandable: true,
})

const showModal = ref(false)
</script>

<template>
  <div class="disclaimer-notice" :class="`is-${tone}`" role="note">
    <Info :size="14" class="disclaimer-notice__icon" />
    <span class="disclaimer-notice__text">{{ text }}</span>
    <button
      v-if="expandable"
      type="button"
      class="disclaimer-notice__more"
      @click="showModal = true"
    >
      查看完整免责声明
    </button>
    <DisclaimerModal v-model="showModal" />
  </div>
</template>

<style scoped>
.disclaimer-notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.6;
}
.disclaimer-notice.is-info {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1e40af;
}
.disclaimer-notice.is-warn {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
}
.disclaimer-notice.is-muted {
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: #64748b;
}
.disclaimer-notice__icon {
  flex-shrink: 0;
  margin-top: 2px;
}
.disclaimer-notice__text {
  flex: 1;
  min-width: 0;
}
.disclaimer-notice__more {
  border: none;
  background: transparent;
  color: inherit;
  font-size: 12px;
  font-weight: 800;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  white-space: nowrap;
}
</style>
