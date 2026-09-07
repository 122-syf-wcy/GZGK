<script setup lang="ts">
/**
 * 悬浮底部导航。
 *
 * uni-app 原生 tabBar 是贴边全宽的，观感偏 H5。这里把原生的隐藏掉，
 * 自绘一条悬浮胶囊栏，但**仍然通过 switchTab 导航**——这样 4 个 tab 页
 * 的实例会被保留，切换不丢状态、不闪白，性能与原生 tab 一致。
 *
 * 若改用 reLaunch/redirectTo 自行跳转，页面会被销毁重建，滚动位置与已加载
 * 数据全部丢失，那才是真正的"像 H5"。
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'

type TabItem = { path: string; label: string }

const TABS: TabItem[] = [
  { path: '/pages/index/index', label: '首页' },
  { path: '/pages/volunteer/form', label: '志愿' },
  { path: '/pages/query/index', label: '查询' },
  { path: '/pages/me/index', label: '我的' },
]

const currentPath = ref('')

function syncCurrent() {
  const pages = getCurrentPages()
  const route = pages.length ? pages[pages.length - 1].route : ''
  currentPath.value = route ? `/${route}` : ''
}

// 原生 tabBar 在每次 switchTab 后会重新显示，必须在 onShow 里再隐藏一次。
onShow(() => {
  uni.hideTabBar({ animation: false })
  syncCurrent()
})

const activeIndex = computed(() => TABS.findIndex(t => t.path === currentPath.value))

function go(item: TabItem, index: number) {
  if (index === activeIndex.value) return
  uni.switchTab({ url: item.path })
}
</script>

<template>
  <view class="tabbar">
    <view class="tabbar__inner">
      <view
        v-for="(tab, i) in TABS"
        :key="tab.path"
        class="tab"
        :class="{ 'tab--active': i === activeIndex }"
        @tap="go(tab, i)"
      >
        <text class="tab__label">{{ tab.label }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 900;
  padding: 0 $gz-space-4 calc(#{$gz-space-3} + env(safe-area-inset-bottom));
  /* 只让胶囊本身可点，两侧留白透传给页面 */
  pointer-events: none;

  &__inner {
    pointer-events: auto;
    display: flex;
    align-items: center;
    padding: 8rpx;
    border-radius: var(--gz-radius-full);
    background: rgba(255, 255, 255, 0.86);
    backdrop-filter: blur(24rpx);
    border: 2rpx solid var(--gz-border);
    box-shadow: 0 16rpx 48rpx rgba(16, 24, 40, 0.14);
  }
}

.tab {
  flex: 1;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--gz-radius-full);
  transition: background-color 0.18s ease;

  &__label {
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
  }

  &--active {
    background: var(--gz-ink);
  }

  &--active .tab__label {
    color: var(--gz-text-inverse);
  }
}
</style>
