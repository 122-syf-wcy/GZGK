<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { USER_AGREEMENT, PRIVACY_POLICY, SITE_DISCLAIMER_VERSION, LIABILITY_NOTICE } from '@/constants/disclaimer'

const tab = ref<'agreement' | 'privacy'>('agreement')

onLoad((options) => {
  if (options?.tab === 'privacy') tab.value = 'privacy'
})
</script>

<template>
  <view class="page">
    <view class="seg">
      <text class="seg-i" :class="{ on: tab === 'agreement' }" @click="tab = 'agreement'">用户协议与免责声明</text>
      <text class="seg-i" :class="{ on: tab === 'privacy' }" @click="tab = 'privacy'">隐私政策</text>
    </view>

    <view v-if="tab === 'agreement'">
      <view class="liability">
        <text class="liability-h">重要提示</text>
        <text class="liability-t">{{ LIABILITY_NOTICE }}</text>
      </view>
      <view v-for="(s, i) in USER_AGREEMENT" :key="i" class="card">
        <view class="sec"><text class="bar" />{{ s.title }}</view>
        <view v-for="(it, j) in s.items" :key="j" class="li"><text class="dot">·</text><text class="li-t">{{ it }}</text></view>
      </view>
    </view>

    <view v-else>
      <view v-for="(s, i) in PRIVACY_POLICY" :key="i" class="card">
        <view class="sec"><text class="bar" />{{ s.title }}</view>
        <view v-for="(it, j) in s.items" :key="j" class="li"><text class="dot">·</text><text class="li-t">{{ it }}</text></view>
      </view>
    </view>

    <view class="ver">版本：{{ SITE_DISCLAIMER_VERSION }}　·　本工具为公益参考，不构成录取预测或填报承诺</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.seg {
  display: flex;
  gap: 8rpx;
  padding: 8rpx;
  margin-bottom: 18rpx;
  background: $gz-bg-subtle;
  border: 1rpx solid $gz-border;
  border-radius: 999rpx;
}
.seg-i {
  flex: 1;
  text-align: center;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 25rpx;
  font-weight: 700;
  color: $gz-text-sub;
  border-radius: 999rpx;
}
.seg-i.on {
  background: $gz-primary;
  color: #fff;
}
.liability {
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 18rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 18rpx;
}
.liability-h {
  display: block;
  font-size: 26rpx;
  font-weight: 800;
  color: $gz-warn;
  margin-bottom: 10rpx;
}
.liability-t {
  display: block;
  font-size: 24rpx;
  color: $gz-warn;
  line-height: 1.8;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx;
  margin-bottom: 16rpx;
}
.sec {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
  margin-bottom: 14rpx;
}
.bar {
  width: 8rpx;
  height: 30rpx;
  border-radius: 6rpx;
  background: $gz-primary;
}
.li {
  display: flex;
  gap: 12rpx;
  margin-bottom: 12rpx;
}
.li:last-child {
  margin-bottom: 0;
}
.dot {
  flex: none;
  color: $gz-primary;
  font-weight: 800;
  font-size: 26rpx;
}
.li-t {
  flex: 1;
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.85;
}
.ver {
  margin-top: 8rpx;
  font-size: 21rpx;
  color: $gz-text-weak;
  line-height: 1.6;
  text-align: center;
}
</style>
