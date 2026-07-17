<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { getUniversityList } from '@/api/university'
import type { University } from '@/types'

const keyword = ref('')
const list = ref<University[]>([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const finished = ref(false)
const loaded = ref(false)
const errorMsg = ref('')

async function load(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    finished.value = false
    list.value = []
    total.value = 0
  }
  if (finished.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await getUniversityList({
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      pageSize,
    })
    const items = res.items || []
    list.value = page.value === 1 ? items : [...list.value, ...items]
    total.value = res.total || 0
    if (items.length < pageSize || list.value.length >= total.value) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
    uni.showToast({ title: errorMsg.value, icon: 'none' })
  } finally {
    loading.value = false
    loaded.value = true
  }
}

function onSearch() {
  load(true)
}

function goDetail(u: University) {
  uni.navigateTo({
    url: `/pages/university-detail/university-detail?id=${u.id}&schoolId=${encodeURIComponent(u.schoolId || '')}`,
  })
}

onLoad(() => load(true))
onReachBottom(() => load(false))
</script>

<template>
  <view class="page">
    <view class="search">
      <input
        v-model="keyword"
        class="search-input"
        type="text"
        placeholder="输入院校名称关键字"
        confirm-type="search"
        @confirm="onSearch"
      />
      <view class="search-btn" hover-class="search-btn-hover" @click="onSearch">搜索</view>
    </view>

    <view v-if="total > 0" class="count">共 {{ total }} 所院校</view>

    <view class="list">
      <view v-for="u in list" :key="u.id" class="card" hover-class="card-hover" @click="goDetail(u)">
        <view class="card-name">{{ u.name }}</view>
        <view class="card-meta">
          <text class="meta">{{ u.province }}{{ u.city ? ' · ' + u.city : '' }}</text>
          <text v-if="u.typeName" class="meta">{{ u.typeName }}</text>
          <text v-if="u.natureName" class="meta">{{ u.natureName }}</text>
        </view>
        <view v-if="u.tags && u.tags.length" class="tags">
          <text v-for="t in u.tags" :key="t" class="tag">{{ t }}</text>
        </view>
      </view>
    </view>

    <view v-if="loading" class="hint">加载中…</view>
    <view v-else-if="errorMsg" class="hint error" @click="load(true)">{{ errorMsg }}，点击重试</view>
    <view v-else-if="loaded && list.length === 0" class="hint">没有找到相关院校</view>
    <view v-else-if="finished && list.length > 0" class="hint">没有更多了</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.search {
  display: flex;
  gap: 16rpx;
  align-items: center;
}
.search-input {
  flex: 1;
  height: 76rpx;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
}
.search-btn {
  flex: none;
  height: 76rpx;
  padding: 0 32rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.search-btn-hover {
  opacity: 0.85;
}
.count {
  margin: 20rpx 4rpx 0;
  font-size: 24rpx;
  color: $gz-text-sub;
}
.list {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 24rpx;
}
.card-hover {
  background: #f0f4ff;
}
.card-name {
  font-size: 32rpx;
  font-weight: 700;
  color: $gz-text;
}
.card-meta {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.meta {
  font-size: 24rpx;
  color: $gz-text-sub;
}
.tags {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}
.tag {
  font-size: 20rpx;
  color: $gz-primary;
  background: $gz-primary-light;
  border-radius: 8rpx;
  padding: 4rpx 12rpx;
}
.hint {
  margin-top: 30rpx;
  text-align: center;
  font-size: 24rpx;
  color: $gz-text-weak;
}
.hint.error {
  color: #d4380d;
}
</style>
