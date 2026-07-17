<script setup lang="ts">
import { DISCLAIMER_POINTS, SITE_DISCLAIMER_VERSION } from '@/constants/disclaimer'
import { submitFeedback } from '@/api/feedback'
import appIcon from '@/static/app-icon.png'

const version = '1.0.0'

function goMyPlans() {
  uni.navigateTo({ url: '/pages/my-plans/my-plans' })
}

function openFeedback() {
  uni.showModal({
    title: '意见反馈',
    editable: true,
    placeholderText: '说说你的建议或遇到的问题（10-500 字）',
    success: async (res) => {
      if (!res.confirm) return
      const content = (res.content || '').trim()
      if (content.length < 10 || content.length > 500) {
        uni.showToast({ title: '反馈需 10-500 字', icon: 'none' })
        return
      }
      try {
        await submitFeedback({ content, sourcePage: 'app/mine' })
        uni.showToast({ title: '反馈已收到，感谢', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: (e as Error).message, icon: 'none' })
      }
    },
  })
}

function openDisclaimer() {
  uni.navigateTo({ url: '/pages/legal/legal' })
}
function openPrivacy() {
  uni.navigateTo({ url: '/pages/legal/legal?tab=privacy' })
}
</script>

<template>
  <view class="page">
    <view class="card profile">
      <image class="logo" :src="appIcon" mode="aspectFit" />
      <view class="pname">高考志愿助手</view>
      <view class="pver">v{{ version }} · 公益版</view>
    </view>

    <view class="card actions">
      <view class="row" hover-class="row-hover" @click="goMyPlans">
        <text class="row-label">我的方案</text>
        <text class="row-go">›</text>
      </view>
      <view class="row" hover-class="row-hover" @click="openFeedback">
        <text class="row-label">意见反馈</text>
        <text class="row-go">›</text>
      </view>
      <view class="row" hover-class="row-hover" @click="openDisclaimer">
        <text class="row-label">用户协议与免责声明</text>
        <text class="row-go">›</text>
      </view>
      <view class="row no-border" hover-class="row-hover" @click="openPrivacy">
        <text class="row-label">隐私政策</text>
        <text class="row-go">›</text>
      </view>
    </view>

    <view class="card">
      <view class="sec-title">使用须知</view>
      <view v-for="(p, i) in DISCLAIMER_POINTS" :key="i" class="point">
        <text class="dot">{{ i + 1 }}</text>
        <text class="ptext">{{ p }}</text>
      </view>
      <view class="ver-line">免责声明版本：{{ SITE_DISCLAIMER_VERSION }}</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 24rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 32rpx;
  margin-bottom: 20rpx;
}
.profile {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.logo {
  width: 120rpx;
  height: 120rpx;
  border-radius: 28rpx;
}
.pname {
  margin-top: 18rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: $gz-text;
}
.pver {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $gz-text-weak;
}
.actions {
  padding: 8rpx 28rpx;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 92rpx;
  border-bottom: 1rpx solid $gz-border;
}
.row.no-border {
  border-bottom: none;
}
.row-hover {
  background: #f0f4ff;
}
.row-label {
  font-size: 28rpx;
  color: $gz-text;
}
.row-go {
  font-size: 34rpx;
  color: $gz-text-weak;
}
.sec-title {
  font-size: 30rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.point {
  display: flex;
  gap: 14rpx;
  margin-bottom: 14rpx;
  align-items: flex-start;
}
.dot {
  flex: none;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $gz-primary-50;
  color: $gz-primary;
  font-size: 22rpx;
  text-align: center;
  line-height: 36rpx;
}
.ptext {
  flex: 1;
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}
.ver-line {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $gz-text-weak;
}
</style>
