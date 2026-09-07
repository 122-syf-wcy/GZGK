<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import { getOnlineStats } from '@/api/query'

const ENTRIES = [
  {
    key: 'university',
    icon: '/static/img/icon-university.webp',
    title: '院校查询',
    desc: '按名称检索全国院校库，查看官方核验入口',
    url: '/pages/query/university',
  },
  {
    key: 'scoreline',
    icon: '/static/img/icon-scoreline.webp',
    title: '历年分数线',
    desc: '院校投档线与一分一段位次换算',
    url: '/pages/query/score-line',
  },
  {
    key: 'special',
    icon: '/static/img/icon-special.webp',
    title: '特殊类型招生',
    desc: '强基、专项、艺体等政策汇总索引',
    url: '/pages/query/special',
  },
]

const views = ref(0)

function go(url: string) {
  uni.navigateTo({ url })
}

onShow(async () => {
  try {
    const stats = await getOnlineStats()
    views.value = stats.totalViews || 0
  } catch {
    views.value = 0
  }
})
</script>

<template>
  <view class="page">
    <view class="head">
      <text class="head__title">查询</text>
      <text class="head__desc">
        不生成方案也能用。所有数据均整理自官方公开材料，填报前请回到官方渠道核验。
      </text>
    </view>

    <view v-for="e in ENTRIES" :key="e.key" class="row" @tap="go(e.url)">
      <image class="row__icon" :src="e.icon" mode="aspectFit" />
      <view class="row__main">
        <text class="row__title">{{ e.title }}</text>
        <text class="row__desc">{{ e.desc }}</text>
      </view>
      <text class="row__go">›</text>
    </view>

    <view v-if="views" class="stat">
      <text class="stat__text">累计 {{ views.toLocaleString() }} 次访问</text>
    </view>

    <FloatingTabBar />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding: calc(#{$gz-space-5} + env(safe-area-inset-top)) $gz-page-x calc(200rpx + env(safe-area-inset-bottom));
}

.head {
  margin-bottom: $gz-space-5;

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

.row {
  @include gz-card;
  @include gz-pressable;
  display: flex;
  align-items: center;
  padding: $gz-space-4 $gz-space-3;
  margin-bottom: $gz-space-3;

  &__icon {
    width: 84rpx;
    height: 84rpx;
    margin-right: $gz-space-3;
  }

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: block;
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__desc {
    display: block;
    margin-top: 6rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    line-height: 1.6;
  }

  &__go {
    font-size: 40rpx;
    color: var(--gz-text-3);
    margin-left: $gz-space-2;
  }
}

.stat {
  margin-top: $gz-space-4;
  text-align: center;

  &__text {
    font-size: 20rpx;
    color: var(--gz-text-3);
  }
}
</style>
