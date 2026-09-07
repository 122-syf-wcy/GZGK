<script setup lang="ts">
/** 骨架屏。列表加载时用它替代整屏 loading，避免布局跳动。 */
withDefaults(defineProps<{ rows?: number; card?: boolean }>(), { rows: 3, card: true })
</script>

<template>
  <view class="sk">
    <view v-for="i in rows" :key="i" class="sk__item" :class="{ 'sk__item--card': card }">
      <view class="sk__line sk__line--title" />
      <view class="sk__line sk__line--long" />
      <view class="sk__line sk__line--short" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sk {
  &__item {
    margin-bottom: $gz-space-3;

    &--card {
      @include gz-card;
      padding: $gz-space-4 $gz-space-3;
      box-shadow: none;
    }
  }

  &__line {
    height: 24rpx;
    border-radius: var(--gz-radius-sm);
    background: var(--gz-surface-3);
    animation: gz-pulse 1.4s ease-in-out infinite;

    &--title {
      width: 40%;
      height: 32rpx;
      margin-bottom: $gz-space-2;
    }

    &--long {
      width: 82%;
      margin-bottom: 12rpx;
    }

    &--short {
      width: 54%;
    }
  }
}

@keyframes gz-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
</style>
