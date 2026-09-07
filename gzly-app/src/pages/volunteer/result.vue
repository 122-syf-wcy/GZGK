<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPageScroll, onReachBottom } from '@dcloudio/uni-app'
import GzButton from '@/components/GzButton.vue'
import GzEmpty from '@/components/GzEmpty.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import GzSegmented from '@/components/GzSegmented.vue'
import GzSheet from '@/components/GzSheet.vue'
import GzSkeleton from '@/components/GzSkeleton.vue'
import GzTag from '@/components/GzTag.vue'
import { STORAGE_KEYS } from '@/constants/config'
import { saveExcel, saveLongImage } from '@/utils/exporter'
import { usePlanStore } from '@/stores/plan'
import type { Gradient, VolunteerItemBrief } from '@/types'

const store = usePlanStore()

const loading = ref(true)
const loadFailed = ref(false)

/** 分片渲染：96 条复杂卡片一次上屏在低端机会明显掉帧，先渲染一屏再按滚动追加。 */
const CHUNK = 16
const visibleCount = ref(CHUNK)

const activeGradient = ref<'全部' | Gradient>('全部')
const reviewVisible = ref(false)
const detailIndex = ref<number | null>(null)

type DraftStatus = 'none' | 'keep' | 'review' | 'drop'
const drafts = ref<Record<number, DraftStatus>>({})

/** 次级过滤：草稿标记与复核状态，配合草稿工作台闭环使用 */
type ListFilter = 'all' | 'keep' | 'review' | 'needsReview'
const listFilter = ref<ListFilter>('all')

const FILTER_OPTIONS: Array<{ value: ListFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'keep', label: '已标保留' },
  { value: 'review', label: '已标待查' },
  { value: 'needsReview', label: '需人工复核' },
]

const plan = computed(() => store.plan)

const filtered = computed<VolunteerItemBrief[]>(() => {
  let items = plan.value?.items || []
  if (activeGradient.value !== '全部') {
    items = items.filter(i => i.gradient === activeGradient.value)
  }
  if (listFilter.value === 'keep') items = items.filter(i => drafts.value[i.index] === 'keep')
  else if (listFilter.value === 'review') items = items.filter(i => drafts.value[i.index] === 'review')
  else if (listFilter.value === 'needsReview') items = items.filter(i => i.needsManualReview)
  return items
})

function switchFilter(v: ListFilter) {
  listFilter.value = v
  visibleCount.value = CHUNK
}

const visibleItems = computed(() => filtered.value.slice(0, visibleCount.value))
const hasMore = computed(() => visibleCount.value < filtered.value.length)

const gradientOptions = computed(() => {
  const m = plan.value?.metrics || {}
  return [
    { value: '全部', label: '全部', badge: plan.value?.items.length ?? 0 },
    { value: '冲', label: '冲', badge: Number(m.chongCount ?? 0) },
    { value: '稳', label: '稳', badge: Number(m.wenCount ?? 0) },
    { value: '保', label: '保', badge: Number(m.baoCount ?? 0) },
    { value: '垫', label: '垫', badge: Number(m.dianCount ?? 0) },
  ]
})

const detailItem = computed(() => {
  if (detailIndex.value === null) return undefined
  return plan.value?.items.find(i => i.index === detailIndex.value)
})

const detailRaw = computed(() => {
  const idx = detailIndex.value
  if (idx === null) return undefined
  return plan.value?.raw.items?.find(i => i.index === idx)
})

function draftKey(planId: number) {
  return `${STORAGE_KEYS.draftStatus}:${planId}`
}

function loadDrafts(planId: number) {
  try {
    const raw = uni.getStorageSync(draftKey(planId))
    drafts.value = raw && typeof raw === 'object' ? raw : {}
  } catch {
    drafts.value = {}
  }
}

/** 保留 → 待查 → 淘汰 → 无标记，循环切换 */
function cycleDraft(index: number) {
  const order: DraftStatus[] = ['none', 'keep', 'review', 'drop']
  const cur = drafts.value[index] || 'none'
  const next = order[(order.indexOf(cur) + 1) % order.length]
  drafts.value = { ...drafts.value, [index]: next }
  if (plan.value) {
    try {
      uni.setStorageSync(draftKey(plan.value.id), drafts.value)
    } catch {
      // 本地标记写失败不影响主流程
    }
  }
}

const DRAFT_TEXT: Record<DraftStatus, string> = {
  none: '标记',
  keep: '保留',
  review: '待查',
  drop: '淘汰',
}

const DRAFT_TONE: Record<DraftStatus, 'neutral' | 'ok' | 'warn' | 'danger'> = {
  none: 'neutral',
  keep: 'ok',
  review: 'warn',
  drop: 'danger',
}

function gradientTone(g: string) {
  return g === '冲' ? 'chong' : g === '稳' ? 'wen' : g === '保' ? 'bao' : 'dian'
}

function switchGradient(v: string) {
  activeGradient.value = v as '全部' | Gradient
  visibleCount.value = CHUNK
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

function loadMore() {
  visibleCount.value += CHUNK
}

function goAi() {
  uni.navigateTo({ url: '/pages/volunteer/ai' })
}

function goForm() {
  uni.switchTab({ url: '/pages/volunteer/form' })
}

const exporting = ref(false)

function openExportMenu() {
  if (exporting.value) return
  uni.showActionSheet({
    itemList: ['保存长图', '导出 Excel'],
    success: async (res) => {
      const cred = store.currentCredentials()
      if (!cred) {
        uni.showToast({ title: '方案凭证缺失', icon: 'none' })
        return
      }
      exporting.value = true
      uni.showLoading({ title: res.tapIndex === 0 ? '正在生成长图' : '正在生成 Excel', mask: true })
      try {
        const result = res.tapIndex === 0
          ? await saveLongImage(cred.planId, cred.safetyCode, cred.accessKey)
          : await saveExcel(cred.planId, cred.safetyCode, cred.accessKey)
        uni.hideLoading()
        uni.showToast({ title: result.message, icon: 'none', duration: 2400 })
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: (e as Error).message || '导出失败，请稍后重试', icon: 'none', duration: 2600 })
      } finally {
        exporting.value = false
      }
    },
  })
}

onReachBottom(() => {
  if (hasMore.value) loadMore()
})

// ---- 回到顶部 ----
const showBackTop = ref(false)

onPageScroll((e) => {
  const next = e.scrollTop > 900
  if (next !== showBackTop.value) showBackTop.value = next
})

function goTop() {
  uni.pageScrollTo({ scrollTop: 0, duration: 200 })
}

// ---- 首次进入的安全码引导 ----
const codeTipVisible = ref(false)

function codeTipKey(planId: number) {
  return `gzly_code_tip_done:${planId}`
}

function maybeShowCodeTip(planId: number) {
  try {
    if (!uni.getStorageSync(codeTipKey(planId))) codeTipVisible.value = true
  } catch {
    codeTipVisible.value = true
  }
}

function dismissCodeTip(copy: boolean) {
  const p = plan.value
  if (!p) return
  if (copy) {
    uni.setClipboardData({
      data: `方案编号 ${p.id} · 安全码 ${p.safetyCode}`,
      success: () => uni.showToast({ title: '已复制，请妥善保存', icon: 'none' }),
    })
  }
  try {
    uni.setStorageSync(codeTipKey(p.id), 1)
  } catch {
    // 写失败只影响下次是否再提示
  }
  codeTipVisible.value = false
}

onLoad(async (query) => {
  const planId = query?.planId ? Number(query.planId) : undefined
  const okRestore = await store.restore(planId)
  loading.value = false
  if (!okRestore || !store.plan) {
    loadFailed.value = true
    return
  }
  loadDrafts(store.plan.id)
  maybeShowCodeTip(store.plan.id)
})
</script>

<template>
  <view class="page">
    <GzNavBar title="志愿方案" />

    <template v-if="loading">
      <view class="pad"><GzSkeleton :rows="4" /></view>
    </template>

    <template v-else-if="loadFailed || !plan">
      <GzEmpty
        title="方案未找到"
        desc="没有在本机找到方案凭证，或凭证已失效。可以回到表单重新生成，或在「我的」里用安全码找回。"
        retry-text="回到表单"
        @retry="goForm"
      />
    </template>

    <template v-else>
      <!-- 概览 -->
      <view class="pad">
        <view class="overview">
          <view class="overview__row">
            <text class="overview__score">{{ plan.totalScore }}</text>
            <text class="overview__unit">分</text>
            <text class="overview__sep">·</text>
            <text class="overview__rank">位次 {{ plan.provinceRank }}</text>
          </view>
          <text class="overview__meta">
            {{ plan.provinceName }} · {{ plan.targetBatch }} · {{ plan.volunteerUnitLabel }} ·
            {{ plan.strategyMode }}
          </text>

          <view class="bars">
            <view
              v-for="g in ['冲', '稳', '保', '垫']"
              :key="g"
              class="bars__seg"
              :class="`bars__seg--${gradientTone(g)}`"
              :style="{ flexGrow: Number(plan.metrics[g === '冲' ? 'chongCount' : g === '稳' ? 'wenCount' : g === '保' ? 'baoCount' : 'dianCount'] || 1) }"
            />
          </view>
          <view class="legend">
            <view v-for="g in ['冲', '稳', '保', '垫']" :key="g" class="legend__item">
              <view class="legend__dot" :class="`legend__dot--${gradientTone(g)}`" />
              <text class="legend__text">
                {{ g }}
                {{ plan.metrics[g === '冲' ? 'chongCount' : g === '稳' ? 'wenCount' : g === '保' ? 'baoCount' : 'dianCount'] }}
              </text>
            </view>
          </view>
        </view>

        <!-- 整表安全度 -->
        <view v-if="plan.metrics.portfolioSafetyLevel" class="safety">
          <text class="safety__title">{{ plan.metrics.portfolioSafetyLevel }}</text>
          <text class="safety__note">{{ plan.metrics.portfolioSafetyNote }}</text>
        </view>

        <!-- 人工复核入口 -->
        <view v-if="plan.manualReviewItems.length" class="review" @tap="reviewVisible = true">
          <view class="review__left">
            <text class="review__title">{{ plan.manualReviewItems.length }} 条需人工复核</text>
            <text class="review__desc">选科要求、体检限制等未取得官方来源，填报前必须逐条核对</text>
          </view>
          <text class="review__go">查看 ›</text>
        </view>
      </view>

      <!-- 安全码引导：每份方案只提示一次 -->
      <view v-if="codeTipVisible" class="pad">
        <view class="codetip">
          <view class="codetip__main">
            <text class="codetip__title">先把安全码存好</text>
            <text class="codetip__desc">
              安全码 <text class="codetip__code">{{ plan.safetyCode }}</text> 是未登录时找回这份方案的唯一凭证。也可以在「我的」登录邮箱，方案会云端同步、换机不丢。
            </text>
          </view>
          <view class="codetip__actions">
            <text class="codetip__copy" @tap="dismissCodeTip(true)">复制并记住</text>
            <text class="codetip__later" @tap="dismissCodeTip(false)">我已保存</text>
          </view>
        </view>
      </view>

      <!-- 梯度筛选 -->
      <view class="pad sticky">
        <GzSegmented
          scroll
          :options="gradientOptions"
          :model-value="activeGradient"
          @update:model-value="switchGradient"
        />
        <view class="subfilter">
          <view
            v-for="f in FILTER_OPTIONS"
            :key="f.value"
            class="subfilter__item"
            :class="{ 'subfilter__item--active': listFilter === f.value }"
            @tap="switchFilter(f.value)"
          >
            <text class="subfilter__text">{{ f.label }}</text>
          </view>
        </view>
      </view>

      <!-- 列表 -->
      <view class="pad">
        <GzEmpty
          v-if="!filtered.length"
          title="没有符合条件的志愿"
          desc="换一个梯度或筛选条件试试。"
        />
        <view v-for="item in visibleItems" :key="item.index" class="vcard" @tap="detailIndex = item.index">
          <view class="vcard__top">
            <text class="vcard__idx">{{ String(item.index).padStart(2, '0') }}</text>
            <GzTag :tone="gradientTone(item.gradient)">{{ item.gradient }}</GzTag>
            <view class="vcard__spacer" />
            <view class="vcard__draft" @tap.stop="cycleDraft(item.index)">
              <GzTag :tone="DRAFT_TONE[drafts[item.index] || 'none']" plain>
                {{ DRAFT_TEXT[drafts[item.index] || 'none'] }}
              </GzTag>
            </view>
          </view>

          <text class="vcard__school">{{ item.universityName }}</text>
          <text class="vcard__major">{{ item.displayName }}</text>

          <view class="vcard__meta">
            <text class="vcard__meta-item">{{ item.city }}</text>
            <text v-for="t in item.tags.slice(0, 2)" :key="t" class="vcard__meta-item">{{ t }}</text>
            <text class="vcard__meta-item">{{ item.referenceYear }} 年位次 {{ item.historyMinRank }}</text>
          </view>

          <view class="vcard__foot">
            <view class="chance">
              <text class="chance__num">{{ item.chanceScore }}</text>
              <text class="chance__label">机会指数</text>
            </view>
            <view class="vcard__bars">
              <view class="meter">
                <view class="meter__fill" :style="{ width: `${item.chanceScore}%` }" />
              </view>
              <view class="vcard__tags">
                <GzTag :tone="item.riskColor === 'green' ? 'ok' : item.riskColor === 'yellow' ? 'warn' : 'danger'">
                  {{ item.riskLevel }}
                </GzTag>
                <GzTag>数据参考度 {{ item.confidence }}</GzTag>
                <GzTag v-if="item.needsManualReview" tone="warn">需复核</GzTag>
              </view>
            </view>
          </view>
        </view>

        <view v-if="hasMore" class="more" @tap="loadMore">
          <text class="more__btn">展开剩余 {{ filtered.length - visibleCount }} 条</text>
        </view>
        <view v-else-if="filtered.length > CHUNK" class="more">
          <text class="more__text">已显示全部 {{ filtered.length }} 条</text>
        </view>
      </view>

      <!-- 回到顶部 -->
      <view v-if="showBackTop" class="backtop" @tap="goTop">
        <text class="backtop__icon">↑</text>
      </view>

      <!-- 底部操作 -->
      <view class="actions">
        <view class="actions__inner">
          <GzButton type="ghost" size="sm" :loading="exporting" @tap="openExportMenu">导出</GzButton>
          <GzButton size="sm" @tap="goAi">AI 深度解读</GzButton>
        </view>
      </view>

      <!-- 复核清单 -->
      <GzSheet :visible="reviewVisible" title="强制人工复核清单" @close="reviewVisible = false">
        <view v-for="r in plan.manualReviewItems" :key="r.index" class="rv">
          <text class="rv__head">{{ String(r.index).padStart(2, '0') }} · {{ r.universityName }} {{ r.majorName }}</text>
          <text v-for="(reason, i) in r.reasons" :key="i" class="rv__reason">· {{ reason }}</text>
          <view v-if="r.evidenceLinks.length" class="rv__links">
            <text v-for="(l, i) in r.evidenceLinks" :key="i" class="rv__link">官方材料 {{ i + 1 }}</text>
          </view>
        </view>
        <view class="rv__tail">
          <text class="rv__tail-text">
            以上条目的选科、体检、单科成绩等要求未取得可核验的官方来源，必须在填报前对照招生章程与专业目录逐条确认。
          </text>
        </view>
      </GzSheet>

      <!-- 单条详情 -->
      <GzSheet :visible="detailIndex !== null" title="志愿详情" @close="detailIndex = null">
        <template v-if="detailItem">
          <text class="dt__school">{{ detailItem.universityName }}</text>
          <text class="dt__major">{{ detailItem.displayName }}</text>

          <view class="dt__grid">
            <view class="dt__cell">
              <text class="dt__k">机会指数</text>
              <text class="dt__v">{{ detailItem.chanceScore }}</text>
            </view>
            <view class="dt__cell">
              <text class="dt__k">数据参考度</text>
              <text class="dt__v">{{ detailItem.confidence }}</text>
            </view>
            <view class="dt__cell">
              <text class="dt__k">风险等级</text>
              <text class="dt__v">{{ detailItem.riskLevel }}</text>
            </view>
            <view class="dt__cell">
              <text class="dt__k">参考年份</text>
              <text class="dt__v">{{ detailItem.referenceYear }}</text>
            </view>
          </view>

          <template v-if="detailRaw">
            <text class="dt__st">推荐依据</text>
            <text class="dt__p">{{ detailRaw.recommendReason }}</text>
            <text class="dt__st">风险提醒</text>
            <text class="dt__p">{{ detailRaw.riskReason }}</text>
            <text class="dt__st">算法说明</text>
            <text class="dt__p">{{ detailRaw.algorithmExplanation }}</text>

            <text class="dt__st">近年录取记录</text>
            <view v-for="h in detailRaw.historyRecords || []" :key="h.year" class="hist">
              <text class="hist__y">{{ h.year }}</text>
              <text class="hist__c">最低分 {{ h.minScore }}</text>
              <text class="hist__c">位次 {{ h.minRank }}</text>
              <text class="hist__c">计划 {{ h.planCount }}</text>
            </view>

            <text class="dt__st">官方核验入口</text>
            <text class="dt__link">招生章程 {{ detailRaw.admissionBrochureUrl }}</text>
            <text class="dt__link">专业目录 {{ detailRaw.majorCatalogUrl }}</text>
          </template>

          <view class="dt__notice">
            <text class="dt__notice-text">{{ plan.referenceProbabilityNotice }}</text>
          </view>
        </template>
      </GzSheet>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding-bottom: calc(200rpx + env(safe-area-inset-bottom));
}

.pad {
  padding: 0 $gz-page-x;
}

.sticky {
  position: sticky;
  top: calc(88rpx + env(safe-area-inset-top));
  z-index: 700;
  background: var(--gz-bg);
  padding-top: $gz-space-2;
  padding-bottom: $gz-space-2;
}

/* ---- 概览 ---- */

.overview {
  padding: $gz-space-3 0 $gz-space-4;

  &__row {
    display: flex;
    align-items: baseline;
  }

  &__score {
    font-family: var(--gz-font-serif);
    font-size: 72rpx;
    line-height: 1;
    color: var(--gz-text);
  }

  &__unit {
    font-size: $gz-text-sm;
    color: var(--gz-text-3);
    margin-left: 8rpx;
  }

  &__sep {
    color: var(--gz-text-3);
    margin: 0 14rpx;
  }

  &__rank {
    font-size: $gz-text-body;
    color: var(--gz-text-2);
  }

  &__meta {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }
}

.bars {
  display: flex;
  height: 14rpx;
  margin-top: $gz-space-3;
  border-radius: var(--gz-radius-full);
  overflow: hidden;

  &__seg {
    height: 100%;

    &--chong { background: var(--gz-chong); }
    &--wen { background: var(--gz-wen); }
    &--bao { background: var(--gz-bao); }
    &--dian { background: var(--gz-dian); }
  }
}

.legend {
  display: flex;
  flex-wrap: wrap;
  margin-top: $gz-space-2;

  &__item {
    display: flex;
    align-items: center;
    margin-right: $gz-space-3;
  }

  &__dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: var(--gz-radius-full);
    margin-right: 8rpx;

    &--chong { background: var(--gz-chong); }
    &--wen { background: var(--gz-wen); }
    &--bao { background: var(--gz-bao); }
    &--dian { background: var(--gz-dian); }
  }

  &__text {
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
  }
}

.safety {
  @include gz-card;
  padding: $gz-space-3;
  margin-bottom: $gz-space-3;
  box-shadow: none;
  background: var(--gz-surface-2);

  &__title {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__note {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
    line-height: 1.7;
  }
}

.review {
  display: flex;
  align-items: center;
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: rgba(217, 119, 6, 0.08);

  &__left {
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-warn);
  }

  &__desc {
    display: block;
    margin-top: 6rpx;
    font-size: 20rpx;
    color: var(--gz-text-2);
    line-height: 1.6;
  }

  &__go {
    font-size: $gz-text-sm;
    color: var(--gz-warn);
    margin-left: $gz-space-2;
  }
}

/* ---- 安全码引导 ---- */

.codetip {
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: var(--gz-ink);

  &__title {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text-inverse);
  }

  &__desc {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-xs;
    color: rgba(255, 255, 255, 0.72);
    line-height: 1.75;
  }

  &__code {
    font-family: var(--gz-font-serif);
    color: #fff;
    letter-spacing: 2rpx;
  }

  &__actions {
    display: flex;
    align-items: center;
    margin-top: $gz-space-3;
  }

  &__copy {
    padding: 12rpx 32rpx;
    border-radius: var(--gz-radius-full);
    background: #fff;
    font-size: $gz-text-sm;
    color: var(--gz-ink);
  }

  &__later {
    margin-left: $gz-space-3;
    font-size: $gz-text-sm;
    color: rgba(255, 255, 255, 0.66);
  }
}

/* ---- 次级过滤 ---- */

.subfilter {
  display: flex;
  flex-wrap: wrap;
  margin-top: $gz-space-2;

  &__item {
    padding: 8rpx 22rpx;
    margin-right: 12rpx;
    border-radius: var(--gz-radius-full);
    border: 2rpx solid var(--gz-border);
    transition: all 0.15s ease;

    &--active {
      border-color: var(--gz-ink);
      background: var(--gz-ink);
    }
  }

  &__text {
    font-size: 20rpx;
    color: var(--gz-text-2);
  }

  &__item--active &__text {
    color: var(--gz-text-inverse);
  }
}

/* ---- 回到顶部 ---- */

.backtop {
  position: fixed;
  right: $gz-space-3;
  bottom: calc(220rpx + env(safe-area-inset-bottom));
  z-index: 840;
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--gz-radius-full);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16rpx);
  border: 2rpx solid var(--gz-border);
  box-shadow: var(--gz-shadow-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.12s ease;

  &:active {
    transform: scale(0.92);
  }

  &__icon {
    font-size: 40rpx;
    color: var(--gz-text);
  }
}

/* ---- 志愿卡 ---- */

.vcard {
  @include gz-card;
  @include gz-pressable;
  padding: $gz-space-3;
  margin-bottom: $gz-space-3;

  &__top {
    display: flex;
    align-items: center;
  }

  &__idx {
    font-family: var(--gz-font-serif);
    font-size: $gz-text-body;
    color: var(--gz-text-3);
    margin-right: 14rpx;
  }

  &__spacer {
    flex: 1;
  }

  &__draft {
    padding: 4rpx 0 4rpx 20rpx;
  }

  &__school {
    display: block;
    margin-top: 12rpx;
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__major {
    display: block;
    margin-top: 4rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    margin-top: 10rpx;
  }

  &__meta-item {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-right: 16rpx;
  }

  &__foot {
    display: flex;
    align-items: center;
    margin-top: $gz-space-3;
    padding-top: $gz-space-3;
    border-top: 2rpx solid var(--gz-border);
  }

  &__bars {
    flex: 1;
    min-width: 0;
    margin-left: $gz-space-3;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    margin-top: 10rpx;
  }
}

.chance {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 120rpx;

  &__num {
    font-family: var(--gz-font-serif);
    font-size: 48rpx;
    line-height: 1;
    color: var(--gz-text);
  }

  &__label {
    font-size: 18rpx;
    color: var(--gz-text-3);
    margin-top: 6rpx;
  }
}

.meter {
  height: 8rpx;
  border-radius: var(--gz-radius-full);
  background: var(--gz-surface-3);
  overflow: hidden;

  &__fill {
    height: 100%;
    background: var(--gz-ink);
  }
}

.more {
  padding: $gz-space-3 0 $gz-space-4;
  text-align: center;

  &__text {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
  }

  &__btn {
    display: inline-block;
    padding: 16rpx 44rpx;
    border-radius: var(--gz-radius-full);
    border: 2rpx solid var(--gz-border-strong);
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }
}

/* ---- 底部操作 ---- */

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 850;
  padding: $gz-space-2 $gz-page-x calc(#{$gz-space-2} + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20rpx);
  border-top: 2rpx solid var(--gz-border);

  &__inner {
    display: flex;
    justify-content: flex-end;
  }
}

.actions__inner > view {
  margin-left: $gz-space-2;
}

/* ---- 复核清单 ---- */

.rv {
  padding: $gz-space-3 0;
  border-bottom: 2rpx solid var(--gz-border);

  &__head {
    display: block;
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__reason {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
    line-height: 1.7;
  }

  &__links {
    display: flex;
    flex-wrap: wrap;
    margin-top: 10rpx;
  }

  &__link {
    font-size: 20rpx;
    color: var(--gz-wen);
    margin-right: 20rpx;
  }

  &__tail {
    padding: $gz-space-4 0 $gz-space-6;
  }

  &__tail-text {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    line-height: 1.8;
  }
}

/* ---- 详情 ---- */

.dt {
  &__school {
    display: block;
    font-size: $gz-text-title;
    color: var(--gz-text);
  }

  &__major {
    display: block;
    margin-top: 6rpx;
    font-size: $gz-text-body;
    color: var(--gz-text-2);
  }

  &__grid {
    display: flex;
    flex-wrap: wrap;
    margin: $gz-space-3 0;
    padding: $gz-space-3 0;
    border-top: 2rpx solid var(--gz-border);
    border-bottom: 2rpx solid var(--gz-border);
  }

  &__cell {
    width: 50%;
    margin-bottom: $gz-space-2;
  }

  &__k {
    display: block;
    font-size: 20rpx;
    color: var(--gz-text-3);
  }

  &__v {
    display: block;
    margin-top: 4rpx;
    font-family: var(--gz-font-serif);
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__st {
    display: block;
    margin-top: $gz-space-3;
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__p {
    display: block;
    margin-top: 8rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.8;
  }

  &__link {
    display: block;
    margin-top: 8rpx;
    font-size: 20rpx;
    color: var(--gz-wen);
    word-break: break-all;
  }

  &__notice {
    margin: $gz-space-4 0 $gz-space-6;
    padding: $gz-space-3;
    border-radius: var(--gz-radius-md);
    background: var(--gz-surface-2);
  }

  &__notice-text {
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.8;
  }
}

.hist {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
  padding: 12rpx $gz-space-2;
  border-radius: var(--gz-radius-sm);
  background: var(--gz-surface-2);

  &__y {
    font-size: $gz-text-sm;
    color: var(--gz-text);
    width: 90rpx;
  }

  &__c {
    font-size: 20rpx;
    color: var(--gz-text-2);
    margin-right: 20rpx;
  }
}
</style>
