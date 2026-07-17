<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchVolunteerPlan } from '@/api/volunteer'
import { getSafetyCode } from '@/utils/storage'
import type { VolunteerItem } from '@/types'

const planId = ref(0)
const wanted = ref<number[]>([])
const cols = ref<VolunteerItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

interface Row {
  label: string
  values: string[]
}
const rows = computed<Row[]>(() => {
  const c = cols.value
  if (!c.length) return []
  const v = (fn: (it: VolunteerItem) => string | number | undefined) =>
    c.map((it) => {
      const r = fn(it)
      return r === undefined || r === null || r === '' ? '-' : String(r)
    })
  return [
    { label: '城市 / 地区', values: v((it) => [it.province, it.city].filter(Boolean).join(' · ')) },
    { label: '学校标签', values: v((it) => (it.tags && it.tags.length ? it.tags.join(' / ') : '')) },
    { label: '主推专业', values: v((it) => it.majorName) },
    { label: '梯度', values: v((it) => it.gradient) },
    { label: '参考位次', values: v((it) => it.historyMinRank) },
    { label: '历史最低分', values: v((it) => it.historyMinScore) },
    { label: '机会指数', values: v((it) => it.chanceScore) },
    { label: '风险等级', values: v((it) => it.riskLevel) },
    { label: '推荐原因', values: v((it) => it.recommendReason) },
    { label: '风险提醒', values: v((it) => it.riskReason) },
  ]
})

async function load(code: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    const plan = await fetchVolunteerPlan(planId.value, code)
    const items = plan.items || []
    cols.value = wanted.value
      .map((idx) => items.find((it) => it.index === idx))
      .filter((it): it is VolunteerItem => !!it)
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  planId.value = Number(options?.id || 0)
  wanted.value = String(options?.idx || '')
    .split(',')
    .map((s) => Number(s))
    .filter((n) => n > 0)
  const code = getSafetyCode(planId.value)
  if (code) load(code)
  else errorMsg.value = '缺少安全码，请先在方案结果页打开'
})
</script>

<template>
  <view class="page">
    <view v-if="loading" class="hint">加载中…</view>
    <view v-else-if="errorMsg" class="hint error">{{ errorMsg }}</view>
    <view v-else-if="cols.length < 2" class="hint">请至少选择 2 个志愿进行对比</view>

    <scroll-view v-else scroll-x class="table-wrap">
      <view class="table" :style="{ width: 160 + cols.length * 220 + 'rpx' }">
        <view class="trow head">
          <view class="cell label">对比项</view>
          <view v-for="(c, i) in cols" :key="i" class="cell col head-col">{{ c.universityName }}</view>
        </view>
        <view v-for="(r, ri) in rows" :key="ri" class="trow">
          <view class="cell label">{{ r.label }}</view>
          <view v-for="(val, ci) in r.values" :key="ci" class="cell col">{{ val }}</view>
        </view>
      </view>
    </scroll-view>

    <view v-if="cols.length >= 2" class="notice">对比数据均为参考估算，请结合各校官方招生计划与录取规则核验。</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 24rpx;
}
.table-wrap {
  width: 100%;
}
.table {
  display: flex;
  flex-direction: column;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  overflow: hidden;
}
.trow {
  display: flex;
  border-bottom: 1rpx solid $gz-border;
}
.trow:last-child {
  border-bottom: none;
}
.trow.head {
  background: $gz-primary-50;
}
.cell {
  padding: 18rpx 16rpx;
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.5;
}
.cell.label {
  width: 160rpx;
  flex: none;
  font-weight: 600;
  color: $gz-text;
  background: $gz-bg-subtle;
}
.cell.col {
  width: 220rpx;
  flex: none;
  border-left: 1rpx solid $gz-border;
}
.head-col {
  font-size: 24rpx;
  font-weight: 700;
  color: $gz-primary;
}
.hint {
  margin-top: 60rpx;
  text-align: center;
  font-size: 24rpx;
  color: $gz-text-weak;
}
.hint.error {
  color: #d4380d;
}
.notice {
  margin-top: 20rpx;
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
