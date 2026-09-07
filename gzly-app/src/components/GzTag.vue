<script setup lang="ts">
/** 小标签。tone 对应状态语义色，浅底口径。 */
withDefaults(defineProps<{
  tone?: 'neutral' | 'ok' | 'warn' | 'danger' | 'info' | 'chong' | 'wen' | 'bao' | 'dian'
  plain?: boolean
}>(), { tone: 'neutral', plain: false })
</script>

<template>
  <view class="tag" :class="[`tag--${tone}`, { 'tag--plain': plain }]">
    <text class="tag__text"><slot /></text>
  </view>
</template>

<style lang="scss" scoped>
.tag {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: var(--gz-radius-full);
  background: var(--gz-surface-3);

  &__text {
    font-size: 20rpx;
    line-height: 1.5;
    color: var(--gz-text-2);
    white-space: nowrap;
  }

  &--ok {
    background: rgba(22, 163, 74, 0.1);
  }
  &--ok .tag__text {
    color: var(--gz-ok);
  }

  &--warn {
    background: rgba(217, 119, 6, 0.1);
  }
  &--warn .tag__text {
    color: var(--gz-warn);
  }

  &--danger {
    background: rgba(220, 38, 38, 0.1);
  }
  &--danger .tag__text {
    color: var(--gz-danger);
  }

  &--info {
    background: rgba(37, 99, 235, 0.1);
  }
  &--info .tag__text {
    color: var(--gz-wen);
  }

  &--chong {
    background: rgba(220, 38, 38, 0.1);
  }
  &--chong .tag__text {
    color: var(--gz-chong);
  }

  &--wen {
    background: rgba(37, 99, 235, 0.1);
  }
  &--wen .tag__text {
    color: var(--gz-wen);
  }

  &--bao {
    background: rgba(22, 163, 74, 0.1);
  }
  &--bao .tag__text {
    color: var(--gz-bao);
  }

  &--dian {
    background: rgba(124, 58, 237, 0.1);
  }
  &--dian .tag__text {
    color: var(--gz-dian);
  }

  &--plain {
    background: transparent;
    border: 2rpx solid var(--gz-border);
  }
}
</style>
