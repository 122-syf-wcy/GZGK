<script setup lang="ts">
/**
 * 自绘导航栏。
 *
 * 页面在 pages.json 里配 navigationStyle: custom，由本组件接管，
 * 这样标题字体、返回箭头、透明滚动效果才能和整体视觉一致。
 */
withDefaults(defineProps<{
  title?: string
  /** 压在图片上时用 light，文字转白 */
  theme?: 'dark' | 'light'
  /** 是否显示返回 */
  back?: boolean
  /** 透明底（配合 hero 图） */
  transparent?: boolean
}>(), { title: '', theme: 'dark', back: true, transparent: false })

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<template>
  <view class="nav" :class="[`nav--${theme}`, { 'nav--transparent': transparent }]">
    <view class="nav__status" />
    <view class="nav__bar">
      <view v-if="back" class="nav__back" @tap="goBack">
        <text class="nav__back-icon">‹</text>
      </view>
      <view v-else class="nav__back nav__back--ghost" />
      <text class="nav__title">{{ title }}</text>
      <view class="nav__slot"><slot name="right" /></view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.nav {
  position: sticky;
  top: 0;
  z-index: 800;
  background: var(--gz-bg);

  &--transparent {
    background: transparent;
  }

  &__status {
    height: env(safe-area-inset-top);
  }

  &__bar {
    height: 88rpx;
    padding: 0 $gz-space-3;
    display: flex;
    align-items: center;
  }

  &__back {
    width: 64rpx;
    height: 64rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--gz-radius-full);

    &--ghost {
      opacity: 0;
    }
  }

  &__back-icon {
    font-size: 48rpx;
    line-height: 1;
    color: var(--gz-text);
    margin-top: -6rpx;
  }

  &__title {
    flex: 1;
    text-align: center;
    font-size: $gz-text-body;
    color: var(--gz-text);
    @include gz-ellipsis;
  }

  &__slot {
    min-width: 64rpx;
    display: flex;
    justify-content: flex-end;
  }

  &--light .nav__title,
  &--light .nav__back-icon {
    color: #fff;
  }
}
</style>
