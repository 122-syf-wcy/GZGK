<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ShieldCheck } from 'lucide-vue-next'
import DisclaimerModal from './DisclaimerModal.vue'
import { FOOTER_DISCLAIMER_LABEL } from '@/constants/disclaimer'

const route = useRoute()
const showModal = ref(false)

// 后台、首页（首页有自带页脚）不重复展示全站页脚
const visible = computed(() => {
  const p = route.path || '/'
  if (p === '/' || p.startsWith('/admin')) return false
  return true
})
</script>

<template>
  <footer v-if="visible" class="global-disclaimer-footer">
    <button type="button" class="global-disclaimer-footer__link" @click="showModal = true">
      <ShieldCheck :size="13" />
      {{ FOOTER_DISCLAIMER_LABEL }}
    </button>
    <DisclaimerModal v-model="showModal" />
  </footer>
</template>

<style scoped>
.global-disclaimer-footer {
  display: flex;
  justify-content: center;
  padding: 16px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
}
.global-disclaimer-footer__link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  text-align: center;
  line-height: 1.5;
}
</style>
