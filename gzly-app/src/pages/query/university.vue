<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import GzEmpty from '@/components/GzEmpty.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import GzSkeleton from '@/components/GzSkeleton.vue'
import GzTag from '@/components/GzTag.vue'
import { searchUniversities, type UniversityBrief } from '@/api/query'
import { inputValue } from '@/utils/request'

const keyword = ref('')
const list = ref<UniversityBrief[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const failed = ref(false)
const loadingMore = ref(false)

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  failed.value = false
  try {
    const res = await searchUniversities({ keyword: keyword.value, page: page.value, pageSize: 20 })
    total.value = res.total
    list.value = reset ? res.records : [...list.value, ...res.records]
  } catch {
    failed.value = true
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

let timer: ReturnType<typeof setTimeout> | undefined
function onSearch(e: unknown) {
  keyword.value = inputValue(e)
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => load(true), 400)
}

function openDetail(u: UniversityBrief) {
  uni.navigateTo({ url: `/pages/query/university-detail?id=${u.id}` })
}

onReachBottom(() => {
  if (loadingMore.value || list.value.length >= total.value) return
  page.value += 1
  load(false)
})

onLoad(() => load(true))

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="page">
    <GzNavBar title="院校查询" />

    <view class="pad">
      <view class="search">
        <input
          class="search__input"
          :value="keyword"
          placeholder="搜索院校名称"
          placeholder-class="search__ph"
          @input="onSearch"
        />
      </view>

      <text v-if="!loading && !failed" class="count">共 {{ total }} 所</text>

      <GzSkeleton v-if="loading" :rows="5" />

      <GzEmpty
        v-else-if="failed"
        title="院校数据读取失败"
        desc="可能是网络波动，稍后再试一次。"
        retry-text="重试"
        @retry="load(true)"
      />

      <GzEmpty v-else-if="!list.length" title="没有匹配的院校" desc="换个关键词试试。" />

      <template v-else>
        <view v-for="u in list" :key="u.id" class="ucard" @tap="openDetail(u)">
          <view class="ucard__main">
            <text class="ucard__name">{{ u.name }}</text>
            <view class="ucard__meta">
              <text class="ucard__loc">{{ u.city }}</text>
              <text class="ucard__type">{{ u.typeName }}</text>
              <text class="ucard__type">{{ u.natureName }}</text>
            </view>
            <view v-if="u.tags?.length" class="ucard__tags">
              <GzTag v-for="t in u.tags" :key="t" tone="info">{{ t }}</GzTag>
            </view>
          </view>
          <text class="ucard__go">›</text>
        </view>

        <view v-if="loadingMore" class="more"><text class="more__text">加载中…</text></view>
        <view v-else-if="list.length >= total" class="more"><text class="more__text">已经到底了</text></view>
      </template>
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

.search {
  margin: $gz-space-2 0 $gz-space-3;

  &__input {
    height: 88rpx;
    padding: 0 $gz-space-3;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
    font-size: $gz-text-body;
    color: var(--gz-text);
  }

  &__ph {
    color: var(--gz-text-3);
  }
}

.count {
  display: block;
  margin-bottom: $gz-space-2;
  font-size: $gz-text-xs;
  color: var(--gz-text-3);
}

.ucard {
  @include gz-card;
  @include gz-pressable;
  display: flex;
  align-items: center;
  padding: $gz-space-3;
  margin-bottom: $gz-space-2;

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__name {
    display: block;
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    margin-top: 6rpx;
  }

  &__loc,
  &__type {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-right: 16rpx;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    margin-top: 10rpx;
  }

  &__go {
    font-size: 40rpx;
    color: var(--gz-text-3);
    margin-left: $gz-space-2;
  }
}

.more {
  padding: $gz-space-4 0;
  text-align: center;

  &__text {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }
}
</style>
