<script setup lang="ts">
/**
 * 底部弹层。
 *
 * 用 v-if 控制挂载而不是只切 opacity——弹层里常放长列表，
 * 常驻挂载会让首屏多渲染一份 DOM。
 */
withDefaults(defineProps<{
  visible: boolean
  title?: string
  /** 内容区最大高度占屏比例 */
  maxRatio?: number
  /** 点击遮罩是否关闭 */
  maskClosable?: boolean
}>(), { title: '', maxRatio: 0.82, maskClosable: true })

const emit = defineEmits<{ (e: 'close'): void }>()

function onMask(maskClosable: boolean) {
  if (maskClosable) emit('close')
}
</script>

<template>
  <view v-if="visible" class="sheet">
    <view class="sheet__mask" @tap="onMask(maskClosable)" />
    <view class="sheet__panel" :style="{ maxHeight: `${maxRatio * 100}vh` }">
      <view class="sheet__handle" />
      <view v-if="title" class="sheet__head">
        <text class="sheet__title">{{ title }}</text>
        <view class="sheet__close" @tap="emit('close')">
          <text class="sheet__close-icon">×</text>
        </view>
      </view>
      <scroll-view class="sheet__body" scroll-y :show-scrollbar="false">
        <slot />
      </scroll-view>
      <view v-if="$slots.footer" class="sheet__footer">
        <slot name="footer" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sheet {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 1000;

  &__mask {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    background: rgba(16, 24, 40, 0.42);
  }

  &__panel {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: column;
    background: var(--gz-surface);
    border-radius: var(--gz-radius-xl) var(--gz-radius-xl) 0 0;
    padding-bottom: env(safe-area-inset-bottom);
    animation: gz-sheet-up 0.22s ease;
  }

  &__handle {
    width: 72rpx;
    height: 8rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-3);
    margin: 16rpx auto 0;
    flex-shrink: 0;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $gz-space-3 $gz-page-x $gz-space-2;
    flex-shrink: 0;
  }

  &__title {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__close {
    width: 56rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
  }

  &__close-icon {
    font-size: 34rpx;
    line-height: 1;
    color: var(--gz-text-2);
    margin-top: -4rpx;
  }

  &__body {
    flex: 1;
    min-height: 0;
    padding: 0 $gz-page-x;
  }

  &__footer {
    padding: $gz-space-3 $gz-page-x;
    border-top: 2rpx solid var(--gz-border);
    flex-shrink: 0;
  }
}

@keyframes gz-sheet-up {
  from {
    transform: translateY(60rpx);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
