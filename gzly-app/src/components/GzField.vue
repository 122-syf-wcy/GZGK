<script setup lang="ts">
/** 表单字段：标签 + 输入 + 后缀 + 提示/错误。uni-app 内置 input，不需要组件库。 */
import { inputValue } from '@/utils/request'

withDefaults(defineProps<{
  label?: string
  modelValue: string | number
  placeholder?: string
  type?: 'text' | 'number' | 'digit'
  suffix?: string
  hint?: string
  error?: string
  disabled?: boolean
  maxlength?: number
}>(), {
  label: '',
  placeholder: '',
  type: 'text',
  suffix: '',
  hint: '',
  error: '',
  disabled: false,
  maxlength: 140,
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'blur'): void
}>()

function onInput(e: unknown) {
  emit('update:modelValue', inputValue(e))
}
</script>

<template>
  <view class="field" :class="{ 'field--error': !!error, 'field--disabled': disabled }">
    <text v-if="label" class="field__label">{{ label }}</text>
    <view class="field__box">
      <input
        class="field__input"
        :value="String(modelValue ?? '')"
        :type="type"
        :placeholder="placeholder"
        :disabled="disabled"
        :maxlength="maxlength"
        placeholder-class="field__ph"
        @input="onInput"
        @blur="emit('blur')"
      />
      <text v-if="suffix" class="field__suffix">{{ suffix }}</text>
      <slot name="action" />
    </view>
    <text v-if="error" class="field__msg field__msg--error">{{ error }}</text>
    <text v-else-if="hint" class="field__msg">{{ hint }}</text>
  </view>
</template>

<style lang="scss" scoped>
.field {
  display: flex;
  flex-direction: column;

  &__label {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    margin-bottom: 12rpx;
  }

  &__box {
    display: flex;
    align-items: center;
    height: 96rpx;
    padding: 0 $gz-space-3;
    border-radius: var(--gz-radius-md);
    background: var(--gz-surface-2);
    border: 2rpx solid transparent;
  }

  &__input {
    flex: 1;
    min-width: 0;
    height: 100%;
    font-size: $gz-text-body;
    color: var(--gz-text);
  }

  &__ph {
    color: var(--gz-text-3);
  }

  &__suffix {
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
    margin-left: 12rpx;
  }

  &__msg {
    margin-top: 10rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    line-height: 1.6;

    &--error {
      color: var(--gz-danger);
    }
  }

  &--error &__box {
    border-color: var(--gz-danger);
    background: rgba(220, 38, 38, 0.05);
  }

  &--disabled &__box {
    opacity: 0.55;
  }
}
</style>
