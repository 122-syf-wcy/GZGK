<script setup lang="ts">
import { FULL_DISCLAIMER, SHORT_DISCLAIMER } from '@/constants/disclaimer'
import { ShieldCheck, X } from 'lucide-vue-next'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <van-popup
    :show="modelValue"
    position="bottom"
    round
    :style="{ maxHeight: '80%', background: '#fffdfa' }"
    @update:show="(v: boolean) => emit('update:modelValue', v)"
  >
    <div class="disclaimer-modal">
      <div class="disclaimer-modal__head">
        <div class="disclaimer-modal__title">
          <ShieldCheck :size="18" />
          完整免责声明
        </div>
        <button class="disclaimer-modal__close" type="button" aria-label="关闭" @click="close">
          <X :size="18" />
        </button>
      </div>
      <p class="disclaimer-modal__short">{{ SHORT_DISCLAIMER }}</p>
      <p class="disclaimer-modal__body">{{ FULL_DISCLAIMER }}</p>
      <button class="disclaimer-modal__ack" type="button" @click="close">我已了解</button>
    </div>
  </van-popup>
</template>

<style scoped>
.disclaimer-modal {
  padding: 22px 18px calc(env(safe-area-inset-bottom, 0px) + 18px);
}
.disclaimer-modal__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.disclaimer-modal__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 850;
  color: #0f172a;
}
.disclaimer-modal__close {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: #f1f5f9;
  color: #475569;
  flex-shrink: 0;
}
.disclaimer-modal__short {
  margin: 14px 0 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.6;
}
.disclaimer-modal__body {
  margin: 14px 0 0;
  font-size: 13px;
  line-height: 1.85;
  color: #475569;
}
.disclaimer-modal__ack {
  width: 100%;
  min-height: 46px;
  margin-top: 18px;
  border: none;
  border-radius: 12px;
  background: #0f172a;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
}
</style>
