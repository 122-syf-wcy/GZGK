<script setup lang="ts">
/**
 * 生成前风险告知。
 *
 * 合规硬要求：必须滚动到底部才能确认，与 H5 行为一致。确认后才允许在
 * 生成请求里带上 agreedDisclaimer=true 与当前 disclaimerVersion，
 * 后端会强校验，版本不符会以 HTTP 200 + code=-1 返回失败。
 */
import { ref } from 'vue'
import GzButton from './GzButton.vue'
import GzSheet from './GzSheet.vue'
import {
  COMPLIANCE_SECTIONS,
  DISCLAIMER_CONFIRM_TEXT,
  DISCLAIMER_UPDATED_AT,
  DISCLAIMER_VERSION,
} from '@/constants/compliance'

defineProps<{ visible: boolean }>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm', version: string): void
}>()

const reachedBottom = ref(false)

function onScroll(e: { detail: { scrollTop: number; scrollHeight: number } }) {
  // scroll-view 没有直接的"到底"事件可靠触发，这里用滚动位置判断并留 40rpx 容差
  const { scrollTop, scrollHeight } = e.detail
  const viewport = scrollHeight - scrollTop
  if (viewport <= scrollHeight && scrollTop > 0) {
    // 由 scrolltolower 兜底，这里只做非零滚动的记录
  }
}

function onReachBottom() {
  reachedBottom.value = true
}
</script>

<template>
  <view v-if="visible" class="dsc">
    <view class="dsc__mask" @tap="emit('close')" />
    <view class="dsc__panel">
      <view class="dsc__handle" />
      <view class="dsc__head">
        <text class="dsc__title">生成前风险告知</text>
        <text class="dsc__ver">版本 {{ DISCLAIMER_VERSION }} · 更新于 {{ DISCLAIMER_UPDATED_AT }}</text>
      </view>

      <scroll-view
        class="dsc__body"
        scroll-y
        :show-scrollbar="false"
        :lower-threshold="40"
        @scroll="onScroll"
        @scrolltolower="onReachBottom"
      >
        <view v-for="sec in COMPLIANCE_SECTIONS" :key="sec.title" class="sec">
          <text class="sec__title">{{ sec.title }}</text>
          <text v-for="(p, i) in sec.paragraphs" :key="i" class="sec__p">{{ p }}</text>
        </view>
        <view class="dsc__end">
          <text class="dsc__end-text">— 以上为全部内容 —</text>
        </view>
      </scroll-view>

      <view class="dsc__footer">
        <text v-if="!reachedBottom" class="dsc__gate">请滑动阅读到底部后再确认</text>
        <GzButton
          block
          :disabled="!reachedBottom"
          @tap="emit('confirm', DISCLAIMER_VERSION)"
        >
          {{ DISCLAIMER_CONFIRM_TEXT }}
        </GzButton>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.dsc {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 1100;

  &__mask {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    background: rgba(16, 24, 40, 0.48);
  }

  &__panel {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 88vh;
    display: flex;
    flex-direction: column;
    background: var(--gz-surface);
    border-radius: var(--gz-radius-xl) var(--gz-radius-xl) 0 0;
    padding-bottom: env(safe-area-inset-bottom);
  }

  &__handle {
    width: 72rpx;
    height: 8rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-3);
    margin: 16rpx auto 0;
  }

  &__head {
    padding: $gz-space-3 $gz-page-x $gz-space-2;
  }

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: $gz-text-title;
    color: var(--gz-text);
  }

  &__ver {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }

  &__body {
    flex: 1;
    min-height: 0;
    padding: 0 $gz-page-x;
  }

  &__end {
    padding: $gz-space-4 0 $gz-space-6;
    text-align: center;
  }

  &__end-text {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }

  &__footer {
    padding: $gz-space-3 $gz-page-x;
    border-top: 2rpx solid var(--gz-border);
  }

  &__gate {
    display: block;
    text-align: center;
    font-size: $gz-text-xs;
    color: var(--gz-warn);
    margin-bottom: 12rpx;
  }
}

.sec {
  margin-bottom: $gz-space-4;

  &__title {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text);
    margin-bottom: 12rpx;
  }

  &__p {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.8;
    margin-bottom: 10rpx;
  }
}
</style>
