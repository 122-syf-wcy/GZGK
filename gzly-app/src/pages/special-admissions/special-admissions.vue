<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  fetchSpecialAdmissionCategories,
  fetchSpecialAdmissionPolicies,
  fetchLatestSpecialAdmissionPolicies,
  type SpecialAdmissionCategory,
  type SpecialAdmissionPolicy,
} from '@/api/specialAdmission'
import { mdToLines } from '@/utils/markdown'

const YEAR = 2026
const categories = ref<SpecialAdmissionCategory[]>([])
const activeCategory = ref('')
const policies = ref<SpecialAdmissionPolicy[]>([])
const loading = ref(false)
const errorMsg = ref('')
const expanded = ref<number | null>(null)

async function loadPolicies() {
  loading.value = true
  errorMsg.value = ''
  try {
    if (!activeCategory.value) {
      policies.value = await fetchLatestSpecialAdmissionPolicies(YEAR, 30)
    } else {
      policies.value = await fetchSpecialAdmissionPolicies({ year: YEAR, category: activeCategory.value, limit: 50 })
    }
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

function pickCategory(c: string) {
  activeCategory.value = c
  expanded.value = null
  loadPolicies()
}

function toggle(id: number) {
  expanded.value = expanded.value === id ? null : id
}

onLoad(async () => {
  try {
    categories.value = await fetchSpecialAdmissionCategories(YEAR)
  } catch {
    categories.value = []
  }
  loadPolicies()
})
</script>

<template>
  <view class="page">
    <scroll-view scroll-x class="cat-bar">
      <text class="cat" :class="{ on: activeCategory === '' }" @click="pickCategory('')">全部</text>
      <text
        v-for="c in categories"
        :key="c.category"
        class="cat"
        :class="{ on: activeCategory === c.category }"
        @click="pickCategory(c.category)"
      >{{ c.categoryName }}</text>
    </scroll-view>

    <view v-if="loading" class="hint">加载中…</view>
    <view v-else-if="errorMsg" class="hint error">{{ errorMsg }}</view>
    <view v-else-if="policies.length === 0" class="hint">暂无相关政策</view>

    <view v-else class="list">
      <view v-for="p in policies" :key="p.id" class="card policy" @click="toggle(p.id)">
        <view class="p-top">
          <text class="p-cat">{{ p.categoryName }}</text>
          <text class="p-year">{{ p.year }}</text>
        </view>
        <view class="p-title">{{ p.title }}</view>
        <view class="p-summary">{{ p.summary }}</view>
        <view v-if="expanded === p.id && p.contentMd" class="p-content">
          <text v-for="(l, i) in mdToLines(p.contentMd)" :key="i" class="p-line">{{ l }}</text>
        </view>
        <view class="p-more">{{ expanded === p.id ? '收起 ▲' : '展开全文 ▼' }}</view>
      </view>
    </view>

    <view class="notice">特殊类型招生资格与时间以各省招生考试院和高校官方公告为准，本页仅作政策线索参考。</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.cat-bar {
  white-space: nowrap;
  margin-bottom: 18rpx;
}
.cat {
  display: inline-block;
  font-size: 24rpx;
  color: $gz-text-sub;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 30rpx;
  padding: 12rpx 28rpx;
  margin-right: 12rpx;
}
.cat.on {
  color: #fff;
  background: $gz-primary;
  border-color: $gz-primary;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.policy {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx;
}
.p-top {
  display: flex;
  align-items: center;
  gap: 14rpx;
}
.p-cat {
  font-size: 20rpx;
  color: $gz-primary;
  background: $gz-primary-50;
  border-radius: 8rpx;
  padding: 4rpx 14rpx;
}
.p-year {
  font-size: 22rpx;
  color: $gz-text-weak;
}
.p-title {
  margin-top: 14rpx;
  font-size: 29rpx;
  font-weight: 700;
  color: $gz-text;
  line-height: 1.5;
}
.p-summary {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}
.p-content {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid $gz-border;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.p-line {
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.75;
}
.p-more {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: $gz-primary;
  font-weight: 600;
}
.hint {
  margin-top: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: $gz-text-weak;
}
.hint.error {
  color: #d4380d;
}
.notice {
  margin-top: 24rpx;
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
