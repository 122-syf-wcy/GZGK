<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getPlanRecords, type PlanRecord } from '@/utils/storage'

const records = ref<PlanRecord[]>([])

function load() {
  records.value = getPlanRecords()
}

function open(r: PlanRecord) {
  uni.navigateTo({ url: `/pages/volunteer-result/volunteer-result?id=${r.planId}` })
}

function goGenerate() {
  uni.navigateTo({ url: '/pages/volunteer/volunteer' })
}

function fmt(ts: number): string {
  try {
    const d = new Date(ts)
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  } catch {
    return ''
  }
}

onShow(() => load())
</script>

<template>
  <view class="page">
    <view v-if="records.length === 0" class="empty">
      <view class="empty-t">本机还没有生成过志愿方案</view>
      <view class="empty-s">去「智能填报」生成后，会自动出现在这里（按本机保存）。</view>
      <view class="empty-btn" hover-class="btn-hover" @click="goGenerate">去生成方案</view>
    </view>

    <view v-else class="list">
      <view class="tip">以下为本机保存的方案记录，点击用安全码打开。换设备请用安全码在结果页找回。</view>
      <view v-for="r in records" :key="r.planId" class="card row" hover-class="row-hover" @click="open(r)">
        <view class="row-main">
          <view class="row-title">{{ r.provinceName || '志愿方案' }} · {{ r.totalScore || '-' }} 分</view>
          <view class="row-sub">{{ r.itemCount || 0 }} 条志愿 · {{ fmt(r.createdAt) }}</view>
        </view>
        <text class="row-go">›</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 24rpx;
}
.tip {
  font-size: 22rpx;
  color: $gz-text-weak;
  line-height: 1.6;
  margin-bottom: 16rpx;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.row {
  display: flex;
  align-items: center;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx 24rpx;
}
.row-hover {
  background: #f0f4ff;
}
.row-main {
  flex: 1;
  min-width: 0;
}
.row-title {
  font-size: 29rpx;
  font-weight: 700;
  color: $gz-text;
}
.row-sub {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: $gz-text-sub;
}
.row-go {
  font-size: 36rpx;
  color: $gz-text-weak;
}
.empty {
  margin-top: 120rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}
.empty-t {
  font-size: 30rpx;
  font-weight: 700;
  color: $gz-text;
}
.empty-s {
  font-size: 24rpx;
  color: $gz-text-sub;
  text-align: center;
  line-height: 1.7;
  padding: 0 40rpx;
}
.empty-btn {
  margin-top: 16rpx;
  height: 80rpx;
  padding: 0 48rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-hover {
  opacity: 0.85;
}
</style>
