<script setup lang="ts">
/**
 * 首启隐私与免责双确认。
 *
 * 应用商店审核的硬性要求：首次启动必须明示隐私政策与免责边界，
 * 未同意不得进入主流程、不得采集任何数据。挂在启动页（首页）上，
 * 同意状态落本机存储，版本升级时可通过更换存储键强制重新确认。
 */
import { ref } from 'vue'
import GzButton from './GzButton.vue'
import { STORAGE_KEYS } from '@/constants/config'
import { DISCLAIMER_UPDATED_AT, DISCLAIMER_VERSION } from '@/constants/compliance'

const visible = ref(false)

try {
  visible.value = !uni.getStorageSync(STORAGE_KEYS.privacyAgreed)
} catch {
  visible.value = true
}

function agree() {
  try {
    uni.setStorageSync(STORAGE_KEYS.privacyAgreed, { agreedAt: Date.now(), version: DISCLAIMER_VERSION })
  } catch {
    // 存储失败时本次会话仍放行，下次启动会再询问
  }
  visible.value = false
}

function refuse() {
  uni.showModal({
    title: '需要你的同意',
    content: '未同意隐私政策与免责边界前，系统不会采集任何信息，也无法提供服务。你可以退出应用，或返回继续阅读。',
    confirmText: '继续阅读',
    cancelText: '退出',
    success: (res) => {
      if (res.cancel) {
        // #ifdef APP-PLUS
        plus.runtime.quit()
        // #endif
      }
    },
  })
}

function openDisclaimer() {
  uni.navigateTo({ url: '/pages/me/disclaimer' })
}
</script>

<template>
  <view v-if="visible" class="gate">
    <view class="gate__mask" />
    <view class="gate__panel">
      <text class="gate__title">欢迎使用</text>
      <text class="gate__sub">在开始前，请了解三件事</text>

      <view class="gate__item">
        <text class="gate__item-title">这是公益工具，不是官方渠道</text>
        <text class="gate__item-desc">完全免费，无广告。不代表任何省级招生考试机构或高校，结果仅供参考。</text>
      </view>
      <view class="gate__item">
        <text class="gate__item-title">只收集生成方案必需的信息</text>
        <text class="gate__item-desc">分数、位次、选科与偏好。请勿填写身份证号、准考证号、账号密码等敏感信息。</text>
      </view>
      <view class="gate__item">
        <text class="gate__item-title">最终决定权在你自己</text>
        <text class="gate__item-desc">所有输出为「机会指数」等参考信息，不构成录取承诺，填报前必须核对官方材料。</text>
      </view>

      <text class="gate__link" @tap="openDisclaimer">
        阅读完整《免责声明与使用边界》（{{ DISCLAIMER_VERSION }} · {{ DISCLAIMER_UPDATED_AT }}）
      </text>

      <GzButton block @tap="agree">同意并开始使用</GzButton>
      <view class="gate__refuse" @tap="refuse">
        <text class="gate__refuse-text">不同意</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.gate {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 1200;

  &__mask {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    background: rgba(16, 24, 40, 0.5);
  }

  &__panel {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--gz-surface);
    border-radius: var(--gz-radius-xl) var(--gz-radius-xl) 0 0;
    padding: $gz-space-6 $gz-page-x calc(#{$gz-space-4} + env(safe-area-inset-bottom));
  }

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    color: var(--gz-text);
  }

  &__sub {
    display: block;
    margin: 8rpx 0 $gz-space-4;
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
  }

  &__item {
    padding: $gz-space-3 0;
    border-top: 2rpx solid var(--gz-border);
  }

  &__item-title {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text);
  }

  &__item-desc {
    display: block;
    margin-top: 6rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.7;
  }

  &__link {
    display: block;
    margin: $gz-space-2 0 $gz-space-4;
    font-size: $gz-text-xs;
    color: var(--gz-wen);
  }

  &__refuse {
    margin-top: $gz-space-2;
    padding: 16rpx;
    text-align: center;
  }

  &__refuse-text {
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
  }
}
</style>
