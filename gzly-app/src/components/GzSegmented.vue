<script setup lang="ts">
/** 分段控件。用于科类切换、梯度切换、方案风格切换。 */
defineProps<{
  options: Array<{ value: string; label: string; badge?: number | string }>
  modelValue: string
  /** 项目多时可横向滚动 */
  scroll?: boolean
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<template>
  <scroll-view v-if="scroll" class="seg seg--scroll" scroll-x :show-scrollbar="false">
    <view class="seg__row">
      <view
        v-for="opt in options"
        :key="opt.value"
        class="seg__item"
        :class="{ 'seg__item--active': opt.value === modelValue }"
        @tap="emit('update:modelValue', opt.value)"
      >
        <text class="seg__label">{{ opt.label }}</text>
        <text v-if="opt.badge !== undefined" class="seg__badge">{{ opt.badge }}</text>
      </view>
    </view>
  </scroll-view>

  <view v-else class="seg">
    <view
      v-for="opt in options"
      :key="opt.value"
      class="seg__item seg__item--flex"
      :class="{ 'seg__item--active': opt.value === modelValue }"
      @tap="emit('update:modelValue', opt.value)"
    >
      <text class="seg__label">{{ opt.label }}</text>
      <text v-if="opt.badge !== undefined" class="seg__badge">{{ opt.badge }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.seg {
  display: flex;
  padding: 6rpx;
  border-radius: var(--gz-radius-full);
  background: var(--gz-surface-2);
  white-space: nowrap;

  &--scroll {
    display: block;
  }

  &__row {
    display: inline-flex;
  }

  &__item {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    height: 68rpx;
    padding: 0 32rpx;
    border-radius: var(--gz-radius-full);
    transition: background-color 0.18s ease;

    &--flex {
      flex: 1;
      padding: 0 16rpx;
    }

    &--active {
      background: var(--gz-surface);
      box-shadow: 0 4rpx 16rpx rgba(16, 24, 40, 0.08);
    }
  }

  &__label {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }

  &__item--active &__label {
    color: var(--gz-text);
  }

  &__badge {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-left: 8rpx;
  }
}
</style>
