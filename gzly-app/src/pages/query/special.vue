<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import GzEmpty from '@/components/GzEmpty.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import GzSegmented from '@/components/GzSegmented.vue'
import GzSkeleton from '@/components/GzSkeleton.vue'
import GzTag from '@/components/GzTag.vue'
import { getSpecialCategories, getSpecialPolicies, type SpecialCategory, type SpecialPolicy } from '@/api/query'

const categories = ref<SpecialCategory[]>([])
const policies = ref<SpecialPolicy[]>([])
const active = ref('')
const loading = ref(true)
const failed = ref(false)

const options = computed(() => [
  { value: '', label: '全部' },
  ...categories.value.map(c => ({ value: c.category, label: c.categoryName, badge: c.count })),
])

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [cats, list] = await Promise.all([
      getSpecialCategories(2026),
      getSpecialPolicies({ year: 2026, category: active.value || undefined }),
    ])
    categories.value = cats
    policies.value = list
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function switchCategory(v: string) {
  active.value = v
  loading.value = true
  try {
    policies.value = await getSpecialPolicies({ year: 2026, category: v || undefined })
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function copyUrl(url?: string) {
  if (!url || !/^https?:\/\//i.test(url)) return
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '官方链接已复制', icon: 'none' }),
  })
}

onLoad(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="page">
    <GzNavBar title="特殊类型招生" />

    <view class="pad">
      <view class="head">
        <text class="head__title">特殊类型招生</text>
        <text class="head__desc">
          强基、专项、艺体等政策条款复杂且逐年调整，这里只做汇总索引，报名与录取规则一律以官方原文为准。
        </text>
      </view>

      <GzSegmented
        v-if="options.length > 1"
        scroll
        :options="options"
        :model-value="active"
        @update:model-value="switchCategory"
      />

      <view class="list">
        <GzSkeleton v-if="loading" :rows="4" />

        <GzEmpty v-else-if="failed" title="政策读取失败" retry-text="重试" @retry="load" />

        <GzEmpty v-else-if="!policies.length" title="暂无该类别政策" desc="换个类别看看，或稍后再来。" />

        <template v-else>
          <view v-for="p in policies" :key="p.id" class="pcard" @tap="copyUrl(p.officialUrl)">
            <view class="pcard__top">
              <GzTag tone="info">{{ p.categoryName }}</GzTag>
              <text class="pcard__year">{{ p.year }}</text>
            </view>
            <text class="pcard__title">{{ p.title }}</text>
            <text class="pcard__summary">{{ p.summary }}</text>
            <view class="pcard__foot">
              <text class="pcard__src">{{ p.sourceName }}</text>
              <text v-if="p.applyStart" class="pcard__date">报名 {{ p.applyStart }} 至 {{ p.applyEnd }}</text>
            </view>
          </view>
        </template>
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
  padding: $gz-space-3 0 $gz-space-3;

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    color: var(--gz-text);
  }

  &__desc {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.7;
  }
}

.list {
  margin-top: $gz-space-3;
}

.pcard {
  @include gz-card;
  @include gz-pressable;
  padding: $gz-space-3;
  margin-bottom: $gz-space-2;

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__year {
    font-size: 20rpx;
    color: var(--gz-text-3);
  }

  &__title {
    display: block;
    margin-top: 12rpx;
    font-size: $gz-text-body;
    color: var(--gz-text);
    line-height: 1.5;
  }

  &__summary {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.7;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: $gz-space-2;
    padding-top: $gz-space-2;
    border-top: 2rpx solid var(--gz-border);
  }

  &__src,
  &__date {
    font-size: 20rpx;
    color: var(--gz-text-3);
  }
}
</style>
