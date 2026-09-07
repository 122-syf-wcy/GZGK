<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import GzPrivacyGate from '@/components/GzPrivacyGate.vue'
import { getBatchSupport } from '@/api/volunteer'
import { getAnnouncement, type Announcement } from '@/api/query'
import { PROVINCE_LIST, READINESS_LABEL } from '@/constants/provinces'
import { REFERENCE_PROBABILITY_NOTICE } from '@/constants/compliance'
import type { BatchSupport, ProvinceCode } from '@/types'

const ANNOUNCE_DISMISS_PREFIX = 'gzly_announce_dismissed'

type CardState = {
  loading: boolean
  failed: boolean
  support?: BatchSupport
}

const states = reactive<Record<string, CardState>>({})
PROVINCE_LIST.forEach((p) => {
  states[p.code] = { loading: true, failed: false }
})

const refreshing = ref(false)
const heroLoaded = ref(false)

const FEATURES = [
  { key: 'volunteer', icon: '/static/img/icon-volunteer.webp', label: '智能志愿', desc: '生成参考方案', tab: '/pages/volunteer/form' },
  { key: 'university', icon: '/static/img/icon-university.webp', label: '院校查询', desc: '2198 所院校', url: '/pages/query/university' },
  { key: 'scoreline', icon: '/static/img/icon-scoreline.webp', label: '历年分数线', desc: '投档线与位次', url: '/pages/query/score-line' },
  { key: 'special', icon: '/static/img/icon-special.webp', label: '特殊招生', desc: '专项与艺体', url: '/pages/query/special' },
  { key: 'ai', icon: '/static/img/icon-ai.webp', label: 'AI 解读', desc: '逐条讲清依据', url: '/pages/volunteer/ai' },
  { key: 'archive', icon: '/static/img/icon-archive.webp', label: '我的方案', desc: '安全码找回', tab: '/pages/me/index' },
]

function openFeature(f: { url?: string; tab?: string }) {
  if (f.tab) {
    uni.switchTab({ url: f.tab })
    return
  }
  if (f.url) uni.navigateTo({ url: f.url })
}

async function loadProvince(code: ProvinceCode) {
  const state = states[code]
  state.loading = true
  state.failed = false
  try {
    state.support = await getBatchSupport(code)
  } catch {
    // 单个省份失败不影响其他卡片，卡片内给可重试的失败态。
    state.failed = true
  } finally {
    state.loading = false
  }
}

async function loadAll() {
  refreshing.value = true
  await Promise.allSettled(PROVINCE_LIST.map(p => loadProvince(p.code)))
  refreshing.value = false
}

// ---- 公告 ----
const announcement = ref<Announcement>()

function announceKey(a: Announcement) {
  return `${ANNOUNCE_DISMISS_PREFIX}:${a.id}:${a.updatedAt || a.publishedAt || ''}`
}

async function loadAnnouncement() {
  try {
    const a = await getAnnouncement()
    if (!a?.id || !a.content) return
    // 同一版本的公告只提示一次，关闭状态按 id+更新时间记忆
    if (uni.getStorageSync(announceKey(a))) return
    announcement.value = a
  } catch {
    // 公告拉不到不影响首页
  }
}

function dismissAnnouncement() {
  const a = announcement.value
  if (!a) return
  try {
    uni.setStorageSync(announceKey(a), 1)
  } catch {
    // 写失败只影响下次是否再提示
  }
  announcement.value = undefined
}

onMounted(() => {
  loadAll()
  loadAnnouncement()
})

onPullDownRefresh(async () => {
  await Promise.allSettled([loadAll(), loadAnnouncement()])
  uni.stopPullDownRefresh()
})

/** 只有普通类本科批可能开放生成，展示也以它为准 */
function primaryBatch(support?: BatchSupport) {
  if (!support?.items.length) return undefined
  return support.items.find(item => item.generatorReady) || support.items[0]
}

function isReady(code: string) {
  return !!states[code]?.support?.anyGeneratorReady
}

function statusTone(code: string) {
  const state = states[code]
  if (state?.loading) return 'idle'
  if (state?.failed) return 'failed'
  if (state?.support?.anyGeneratorReady) return 'ready'
  if (state?.support?.readinessLevel === 'QUERY_ONLY') return 'partial'
  return 'locked'
}

function statusText(code: string) {
  const state = states[code]
  if (state?.loading) return '读取中'
  if (state?.failed) return '读取失败'
  const level = state?.support?.readinessLevel
  return level ? READINESS_LABEL[level] || level : '状态未知'
}

/** 置灰时说清缺什么，不让用户点进去才知道用不了 */
function lockedReason(support?: BatchSupport) {
  const batch = primaryBatch(support)
  if (batch?.supportReason) return batch.supportReason
  if (support?.warnings?.length) return support.warnings[0]
  return '该地区官方数据尚未导入，暂不开放智能生成。'
}

function openProvince(code: ProvinceCode) {
  const state = states[code]
  if (state?.loading) return
  if (state?.failed) {
    loadProvince(code)
    return
  }
  if (!state?.support?.anyGeneratorReady) {
    uni.showToast({ title: lockedReason(state?.support), icon: 'none', duration: 2600 })
    return
  }
  uni.navigateTo({ url: `/pages/volunteer/form?provinceCode=${code}` })
}

/** 主 CTA：优先进入已开放的地区，一个都没有时提示 */
function startPrimary() {
  const ready = PROVINCE_LIST.find(p => isReady(p.code))
  if (!ready) {
    uni.showToast({ title: '各地区官方数据准备中，可先查询院校与分数线', icon: 'none', duration: 2600 })
    return
  }
  openProvince(ready.code)
}
</script>

<template>
  <view class="page">
    <!-- hero：界面单色，颜色由照片提供 -->
    <view class="hero">
      <view class="pill">
        <image class="pill__logo" src="/static/img/logo-mark.webp" mode="aspectFit" />
        <text class="pill__text">公益 · 免费 · 非官方</text>
      </view>

      <text class="hero__l1">把志愿这件事</text>
      <text class="hero__l2">想清楚再填</text>
      <text class="hero__desc">
        基于官方一分一段表与历年投档位次整理参考方案，每一条都给得出依据。
      </text>

      <view class="cta" @tap="startPrimary">
        <text class="cta__text">开始智能填报</text>
      </view>

      <view class="shot">
        <image
          class="shot__img"
          :class="{ 'shot__img--loaded': heroLoaded }"
          src="/static/img/hero-sky.webp"
          mode="aspectFill"
          @load="heroLoaded = true"
        />
        <view class="shot__tag">
          <text class="shot__tag-text">数据来源以各省级招生考试机构公开材料为准</text>
        </view>
      </view>

      <view class="stats">
        <view class="stat">
          <text class="stat__num">2198</text>
          <text class="stat__unit">所院校</text>
        </view>
        <view class="stat__sep" />
        <view class="stat">
          <text class="stat__num">16<text class="stat__num-sm">万</text></text>
          <text class="stat__unit">条专业录取线</text>
        </view>
        <view class="stat__sep" />
        <view class="stat">
          <text class="stat__num">8</text>
          <text class="stat__unit">个地区</text>
        </view>
      </view>
    </view>

    <!-- 公告 -->
    <view v-if="announcement" class="announce">
      <view class="announce__main">
        <text class="announce__title">{{ announcement.title }}</text>
        <text class="announce__content">{{ announcement.content }}</text>
      </view>
      <view class="announce__close" @tap="dismissAnnouncement">
        <text class="announce__close-icon">×</text>
      </view>
    </view>

    <!-- 地区 -->
    <view class="section">
      <view class="section__head">
        <text class="section__title">选择地区</text>
        <text class="section__action" @tap="loadAll">{{ refreshing ? '刷新中' : '刷新' }}</text>
      </view>

      <view class="grid">
        <view
          v-for="province in PROVINCE_LIST"
          :key="province.code"
          class="pcard"
          :class="[`pcard--${statusTone(province.code)}`]"
          @tap="openProvince(province.code)"
        >
          <view class="pcard__top">
            <text class="pcard__name">{{ province.shortName }}</text>
            <view class="dot" :class="`dot--${statusTone(province.code)}`" />
          </view>

          <!-- 加载中给 shimmer 占位，避免先出"—"再跳数字 -->
          <template v-if="states[province.code]?.loading">
            <view class="pcard__shimmer pcard__shimmer--num" />
            <view class="pcard__shimmer pcard__shimmer--mode" />
          </template>
          <template v-else>
            <view class="pcard__mid">
              <text class="pcard__num">{{ primaryBatch(states[province.code]?.support)?.targetCount || '—' }}</text>
              <text class="pcard__unit">个志愿</text>
            </view>

            <text class="pcard__mode">
              {{ primaryBatch(states[province.code]?.support)?.volunteerMode || '志愿模式待确认' }}
            </text>
          </template>

          <view class="pcard__foot">
            <text class="pcard__status" :class="`pcard__status--${statusTone(province.code)}`">
              {{ statusText(province.code) }}
            </text>
            <text v-if="isReady(province.code)" class="pcard__go">进入 ›</text>
            <text v-else-if="states[province.code]?.failed" class="pcard__go">重试</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 功能入口 -->
    <view class="section">
      <view class="section__head">
        <text class="section__title">能做什么</text>
      </view>
      <view class="fgrid">
        <view v-for="f in FEATURES" :key="f.key" class="fcard" @tap="openFeature(f)">
          <image class="fcard__icon" :src="f.icon" mode="aspectFit" />
          <text class="fcard__label">{{ f.label }}</text>
          <text class="fcard__desc">{{ f.desc }}</text>
        </view>
      </view>
    </view>

    <view class="notice">
      <text class="notice__text">{{ REFERENCE_PROBABILITY_NOTICE }}</text>
    </view>

    <FloatingTabBar />
    <GzPrivacyGate />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  overflow-x: hidden;
  /* 给悬浮底栏让位：胶囊 96rpx + 下边距 24rpx + 呼吸 80rpx */
  padding-bottom: calc(200rpx + env(safe-area-inset-bottom));
}

/* ---------------- hero ---------------- */

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: calc(28rpx + env(safe-area-inset-top)) $gz-page-x 0;

  &__l1,
  &__l2 {
    font-family: var(--gz-font-serif);
    font-size: 62rpx;
    line-height: 1.22;
    letter-spacing: 2rpx;
    text-align: center;
  }

  &__l1 {
    color: var(--gz-text);
    margin-top: $gz-space-5;
  }

  /* 第二行降为灰色，参考图里 "AI Powers You Up." 的处理 */
  &__l2 {
    color: var(--gz-text-3);
  }

  &__desc {
    margin-top: $gz-space-3;
    max-width: 560rpx;
    text-align: center;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.7;
  }
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 24rpx 8rpx 8rpx;
  border-radius: var(--gz-radius-full);
  background: var(--gz-surface-2);
  border: 2rpx solid var(--gz-border);

  &__logo {
    width: 36rpx;
    height: 36rpx;
    border-radius: var(--gz-radius-full);
    margin-right: 12rpx;
  }

  &__text {
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
    letter-spacing: 1rpx;
  }
}

.cta {
  @include gz-cta;
  margin-top: $gz-space-5;

  &__text {
    color: var(--gz-text-inverse);
    font-size: $gz-text-body;
  }
}

/* 照片是页面唯一的颜色来源 */
.shot {
  position: relative;
  width: 100%;
  height: 460rpx;
  margin-top: $gz-space-6;
  border-radius: var(--gz-radius-xl);
  overflow: hidden;
  box-shadow: var(--gz-shadow-lg);

  &__img {
    width: 100%;
    height: 100%;
    opacity: 0;
    transform: scale(1.03);
    transition: opacity 0.5s ease, transform 0.8s ease;

    &--loaded {
      opacity: 1;
      transform: scale(1);
    }
  }

  &__tag {
    position: absolute;
    left: $gz-space-3;
    right: $gz-space-3;
    bottom: $gz-space-3;
    padding: 12rpx 20rpx;
    border-radius: var(--gz-radius-full);
    background: rgba(255, 255, 255, 0.82);
    backdrop-filter: blur(12rpx);
  }

  &__tag-text {
    font-size: 20rpx;
    color: var(--gz-text-2);
  }
}

.stats {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  margin-top: $gz-space-5;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;

  &__num {
    font-family: var(--gz-font-serif);
    font-size: 44rpx;
    line-height: 1.1;
    color: var(--gz-text);
  }

  &__num-sm {
    font-size: 26rpx;
  }

  &__unit {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-top: 4rpx;
  }

  &__sep {
    width: 2rpx;
    height: 48rpx;
    background: var(--gz-border);
    margin: 0 $gz-space-4;
  }
}

/* ---------------- 公告 ---------------- */

.announce {
  display: flex;
  align-items: flex-start;
  margin: $gz-space-6 $gz-page-x 0;
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: var(--gz-surface-2);
  border-left: 6rpx solid var(--gz-ink);

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__content {
    display: block;
    margin-top: 6rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
    line-height: 1.7;
  }

  &__close {
    width: 48rpx;
    height: 48rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: -8rpx -8rpx 0 8rpx;
  }

  &__close-icon {
    font-size: 32rpx;
    color: var(--gz-text-3);
  }
}

/* ---------------- 通用区块 ---------------- */

.section {
  padding: $gz-space-8 $gz-page-x 0;

  &__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: $gz-space-3;
  }

  &__title {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-title;
    color: var(--gz-text);
    letter-spacing: 1rpx;
  }

  &__action {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }
}

/* ---------------- 省份卡 ---------------- */

.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.pcard {
  @include gz-card;
  @include gz-pressable;
  width: 48.5%;
  padding: $gz-space-4 $gz-space-3 $gz-space-3;
  margin-bottom: $gz-space-3;
  display: flex;
  flex-direction: column;

  &--locked,
  &--partial {
    background: var(--gz-surface-2);
    box-shadow: none;
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-title;
    color: var(--gz-text);
  }

  &__mid {
    display: flex;
    align-items: baseline;
    margin-top: $gz-space-2;
  }

  &__num {
    font-family: var(--gz-font-serif);
    font-size: 52rpx;
    line-height: 1;
    color: var(--gz-text);
  }

  &__unit {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    margin-left: 8rpx;
  }

  &__mode {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    margin-top: $gz-space-2;
    @include gz-ellipsis;
  }

  &__shimmer {
    border-radius: var(--gz-radius-sm);
    background: linear-gradient(90deg, var(--gz-surface-3) 25%, var(--gz-surface-2) 45%, var(--gz-surface-3) 65%);
    background-size: 300% 100%;
    animation: gz-card-shimmer 1.5s linear infinite;

    &--num {
      width: 52%;
      height: 52rpx;
      margin-top: $gz-space-2;
    }

    &--mode {
      width: 78%;
      height: 22rpx;
      margin-top: $gz-space-2;
    }
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: $gz-space-3;
    padding-top: $gz-space-2;
    border-top: 2rpx solid var(--gz-border);
  }

  &__status {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);

    &--ready {
      color: var(--gz-ok);
    }

    &--partial {
      color: var(--gz-warn);
    }

    &--failed {
      color: var(--gz-danger);
    }
  }

  &__go {
    font-size: $gz-text-xs;
    color: var(--gz-text);
  }
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: var(--gz-radius-full);
  background: var(--gz-muted);

  &--ready {
    background: var(--gz-ok);
  }

  &--partial {
    background: var(--gz-warn);
  }

  &--failed {
    background: var(--gz-danger);
  }
}

/* ---------------- 功能入口 ---------------- */

.fgrid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.fcard {
  @include gz-card;
  @include gz-pressable;
  width: 31.5%;
  padding: $gz-space-3 $gz-space-2;
  margin-bottom: $gz-space-3;
  display: flex;
  flex-direction: column;
  align-items: center;

  &__icon {
    width: 92rpx;
    height: 92rpx;
    margin-bottom: $gz-space-2;
  }

  &__label {
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__desc {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-top: 4rpx;
    @include gz-ellipsis;
    max-width: 100%;
  }
}

@keyframes gz-card-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

/* ---------------- 免责 ---------------- */

.notice {
  margin: $gz-space-8 $gz-page-x 0;
  padding: $gz-space-3;
  background: var(--gz-surface-2);
  border-radius: var(--gz-radius-md);

  &__text {
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.8;
  }
}
</style>
