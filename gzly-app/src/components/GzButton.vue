<script setup lang="ts">
/** 按钮。primary 是参考图里的近黑胶囊，ghost 描边，subtle 浅灰底。 */
withDefaults(defineProps<{
  type?: 'primary' | 'ghost' | 'subtle'
  size?: 'md' | 'sm'
  block?: boolean
  disabled?: boolean
  loading?: boolean
}>(), {
  type: 'primary',
  size: 'md',
  block: false,
  disabled: false,
  loading: false,
})

const emit = defineEmits<{ (e: 'tap'): void }>()

function onTap(disabled?: boolean, loading?: boolean) {
  if (disabled || loading) return
  emit('tap')
}
</script>

<template>
  <view
    class="btn"
    :class="[`btn--${type}`, `btn--${size}`, { 'btn--block': block, 'btn--disabled': disabled || loading }]"
    @tap="onTap(disabled, loading)"
  >
    <view v-if="loading" class="btn__spinner" />
    <text class="btn__text"><slot /></text>
  </view>
</template>

<style lang="scss" scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--gz-radius-full);
  transition: opacity 0.15s ease, transform 0.12s ease;

  &:not(.btn--disabled):active {
    transform: scale(0.96);
  }

  &--block {
    display: flex;
    width: 100%;
  }

  &--md {
    padding: 22rpx 44rpx;
    min-height: 88rpx;
  }

  &--md .btn__text {
    font-size: $gz-text-body;
  }

  &--sm {
    padding: 12rpx 28rpx;
    min-height: 60rpx;
  }

  &--sm .btn__text {
    font-size: $gz-text-sm;
  }

  &--primary {
    background: var(--gz-ink);
  }

  &--primary .btn__text {
    color: var(--gz-text-inverse);
  }

  &--ghost {
    background: transparent;
    border: 2rpx solid var(--gz-border-strong);
  }

  &--ghost .btn__text {
    color: var(--gz-text);
  }

  &--subtle {
    background: var(--gz-surface-2);
  }

  &--subtle .btn__text {
    color: var(--gz-text);
  }

  &--disabled {
    opacity: 0.4;
  }

  &__spinner {
    width: 26rpx;
    height: 26rpx;
    margin-right: 14rpx;
    border-radius: 50%;
    border: 4rpx solid rgba(255, 255, 255, 0.35);
    border-top-color: #fff;
    animation: gz-spin 0.7s linear infinite;
  }

  &--ghost &__spinner,
  &--subtle &__spinner {
    border-color: rgba(16, 24, 40, 0.18);
    border-top-color: var(--gz-text);
  }
}

@keyframes gz-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
