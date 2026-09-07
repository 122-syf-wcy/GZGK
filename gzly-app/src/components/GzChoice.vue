<script setup lang="ts">
/**
 * 选项组：单选或多选的胶囊按钮组。
 * 表单里的策略模式、优先级、选科都用它，避免引入 picker 打断填写节奏。
 */
withDefaults(defineProps<{
  options: Array<{ value: string; label: string; disabled?: boolean }>
  /** 单选传 string，多选传 string[] */
  modelValue: string | string[]
  multiple?: boolean
  /** 多选时的最大选中数 */
  max?: number
}>(), { multiple: false, max: 99 })

const emit = defineEmits<{ (e: 'update:modelValue', v: string | string[]): void }>()

function isActive(value: string, modelValue: string | string[]) {
  return Array.isArray(modelValue) ? modelValue.includes(value) : modelValue === value
}

function pick(
  value: string,
  disabled: boolean | undefined,
  multiple: boolean,
  modelValue: string | string[],
  max: number,
) {
  if (disabled) return
  if (!multiple) {
    emit('update:modelValue', value)
    return
  }
  const list = Array.isArray(modelValue) ? [...modelValue] : []
  const idx = list.indexOf(value)
  if (idx >= 0) {
    list.splice(idx, 1)
  } else {
    if (list.length >= max) {
      uni.showToast({ title: `最多选 ${max} 项`, icon: 'none' })
      return
    }
    list.push(value)
  }
  emit('update:modelValue', list)
}
</script>

<template>
  <view class="choice">
    <view
      v-for="opt in options"
      :key="opt.value"
      class="choice__item"
      :class="{
        'choice__item--active': isActive(opt.value, modelValue),
        'choice__item--disabled': opt.disabled,
      }"
      @tap="pick(opt.value, opt.disabled, multiple, modelValue, max)"
    >
      <text class="choice__label">{{ opt.label }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.choice {
  display: flex;
  flex-wrap: wrap;

  &__item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 72rpx;
    padding: 0 30rpx;
    margin: 0 12rpx 12rpx 0;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
    border: 2rpx solid transparent;
    transition: all 0.15s ease;

    &--active {
      background: var(--gz-ink);
      border-color: var(--gz-ink);
    }

    &--disabled {
      opacity: 0.36;
    }
  }

  &__label {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }

  &__item--active &__label {
    color: var(--gz-text-inverse);
  }
}
</style>
