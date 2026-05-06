<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { CheckCircle, ChevronDown, ShieldCheck } from 'lucide-vue-next'
import {
  COMPLIANCE_SECTIONS,
  DISCLAIMER_CONFIRM_TEXT,
  DISCLAIMER_UPDATED_AT,
  DISCLAIMER_VERSION,
} from '@/constants/compliance'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  confirm: [version: string]
}>()

const show = ref(false)
const bodyRef = ref<HTMLElement | null>(null)
const hasReadToEnd = ref(false)

const agreeButtonText = computed(() => (
  hasReadToEnd.value ? '我已阅读并确认' : '请先阅读至底部'
))

function checkReadProgress() {
  const el = bodyRef.value
  if (!el) return
  hasReadToEnd.value = el.scrollTop + el.clientHeight >= el.scrollHeight - 8
}

function open() {
  show.value = true
  hasReadToEnd.value = false
  nextTick(checkReadProgress)
}

function onAgree() {
  if (!hasReadToEnd.value) return
  emit('update:modelValue', true)
  emit('confirm', DISCLAIMER_VERSION)
  show.value = false
}

watch(show, (visible) => {
  if (visible) nextTick(checkReadProgress)
})

defineExpose({ open })
</script>

<template>
  <van-popup v-model:show="show" position="bottom" round :style="{ maxHeight: '86%' }">
    <div class="disclaimer-content">
      <div class="disclaimer-header">
        <div class="disclaimer-icon">
          <ShieldCheck :size="20" />
        </div>
        <div class="disclaimer-heading">
          <h3 class="disclaimer-title">生成前风险告知</h3>
          <p class="disclaimer-meta">版本 {{ DISCLAIMER_VERSION }} · 更新于 {{ DISCLAIMER_UPDATED_AT }}</p>
        </div>
      </div>

      <div ref="bodyRef" class="disclaimer-body" @scroll.passive="checkReadProgress">
        <p class="disclaimer-intro">
          请完整阅读以下内容。确认后系统才会调用志愿生成接口，并记录本次确认版本与确认时间。
        </p>

        <section v-for="section in COMPLIANCE_SECTIONS" :key="section.title" class="clause">
          <h4>{{ section.title }}</h4>
          <p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
          <ul v-if="section.bullets?.length">
            <li v-for="bullet in section.bullets" :key="bullet">{{ bullet }}</li>
          </ul>
        </section>
      </div>

      <div class="read-status" :class="{ 'read-status--done': hasReadToEnd }">
        <CheckCircle v-if="hasReadToEnd" :size="16" />
        <ChevronDown v-else :size="16" />
        <span>{{ hasReadToEnd ? DISCLAIMER_CONFIRM_TEXT : '请向下滚动并阅读完整风险告知' }}</span>
      </div>

      <div class="disclaimer-actions">
        <button class="agree-btn" :disabled="!hasReadToEnd" @click="onAgree">
          <CheckCircle :size="18" />
          <span>{{ agreeButtonText }}</span>
        </button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.disclaimer-content {
  padding: var(--gz-space-5) var(--gz-space-4);
}

.disclaimer-header {
  display: flex;
  align-items: center;
  gap: var(--gz-space-3);
  margin-bottom: var(--gz-space-4);
}

.disclaimer-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 12px;
  background: #0f172a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.disclaimer-heading {
  min-width: 0;
}

.disclaimer-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--gz-text-primary);
}

.disclaimer-meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--gz-text-tertiary);
}

.disclaimer-body {
  max-height: 56vh;
  overflow-y: auto;
  margin-bottom: var(--gz-space-3);
  padding-right: var(--gz-space-2);
}

.disclaimer-intro {
  font-size: 14px;
  color: var(--gz-text-secondary);
  margin-bottom: var(--gz-space-4);
  line-height: 1.6;
}

.clause {
  margin-bottom: var(--gz-space-4);
  padding: var(--gz-space-3) var(--gz-space-4);
  background: rgba(15, 23, 42, 0.035);
  border-radius: var(--gz-radius-md);
  border-left: 3px solid #0f172a;
}

.clause h4 {
  font-size: 14px;
  font-weight: 700;
  color: var(--gz-text-primary);
  margin-bottom: 6px;
}

.clause p,
.clause li {
  font-size: 13px;
  line-height: 1.75;
  color: var(--gz-text-secondary);
}

.clause p + p {
  margin-top: 6px;
}

.clause ul {
  margin-top: 8px;
  padding-left: 18px;
}

.read-status {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: var(--gz-space-3);
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
  line-height: 1.5;
}

.read-status--done {
  background: #f0fdf4;
  color: #166534;
}

.read-status svg {
  flex-shrink: 0;
  margin-top: 1px;
}

.disclaimer-actions {
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.agree-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  min-height: 48px;
  padding: 0 16px;
  border: none;
  border-radius: var(--gz-radius-full);
  background: #0f172a;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: transform var(--gz-transition-fast), opacity var(--gz-transition-fast);
}

.agree-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.agree-btn:active:not(:disabled) {
  transform: scale(0.98);
}
</style>
