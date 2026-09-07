<script setup lang="ts">
import GzNavBar from '@/components/GzNavBar.vue'
import {
  COMPLIANCE_SECTIONS,
  DISCLAIMER_UPDATED_AT,
  DISCLAIMER_VERSION,
  REFERENCE_PROBABILITY_NOTICE,
} from '@/constants/compliance'
</script>

<template>
  <view class="page">
    <GzNavBar title="免责声明" />

    <view class="pad">
      <view class="head">
        <text class="head__title">使用边界与免责</text>
        <text class="head__ver">版本 {{ DISCLAIMER_VERSION }} · 更新于 {{ DISCLAIMER_UPDATED_AT }}</text>
      </view>

      <view class="notice">
        <text class="notice__text">{{ REFERENCE_PROBABILITY_NOTICE }}</text>
      </view>

      <view v-for="sec in COMPLIANCE_SECTIONS" :key="sec.title" class="sec">
        <text class="sec__title">{{ sec.title }}</text>
        <text v-for="(p, i) in sec.paragraphs" :key="i" class="sec__p">{{ p }}</text>
      </view>

      <view class="tail">
        <text class="tail__text">
          本告知用于说明系统能力边界和使用风险，不构成正式法律意见。如对填报有疑问，请咨询学校老师或具备资质的专业人士。
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
}

.pad {
  padding: 0 $gz-page-x;
}

.head {
  padding: $gz-space-3 0 $gz-space-4;

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    color: var(--gz-text);
  }

  &__ver {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }
}

.notice {
  padding: $gz-space-3;
  margin-bottom: $gz-space-4;
  border-radius: var(--gz-radius-md);
  background: var(--gz-surface-2);

  &__text {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.85;
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
    margin-bottom: 10rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.85;
  }
}

.tail {
  padding: $gz-space-4 0 $gz-space-8;
  border-top: 2rpx solid var(--gz-border);

  &__text {
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.8;
  }
}
</style>
