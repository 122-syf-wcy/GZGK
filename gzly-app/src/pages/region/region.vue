<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBatchSupportByProvince, type BatchSupportResponse } from '@/api/volunteer'
import { PROVINCES, normalizeProvinceCode, getProvinceName, type ProvinceCode } from '@/constants/provinces'
import { setPrefProvince } from '@/utils/storage'

const code = ref<ProvinceCode>('GZ')
const name = ref('贵州')
const mode = ref<'full' | 'query-only'>('full')
const support = ref<BatchSupportResponse | null>(null)
const loading = ref(false)

interface Entry {
  key: string
  label: string
  desc: string
  kind: 'tab' | 'page'
  url: string
}
const entries: Entry[] = [
  { key: 'volunteer', label: '智能填报', desc: '按本省口径生成平行志愿参考', kind: 'page', url: '/pages/volunteer/volunteer' },
  { key: 'scoreline', label: '历年分数线', desc: '本省投档线 / 一分一段 / 位次', kind: 'tab', url: '/pages/scoreline/scoreline' },
  { key: 'university', label: '院校查询', desc: '全国院校信息与官方核验', kind: 'tab', url: '/pages/university/university' },
  { key: 'special', label: '特殊招生', desc: '强基 / 综评 / 专项政策线索', kind: 'page', url: '/pages/special-admissions/special-admissions' },
]

async function loadSupport() {
  loading.value = true
  try {
    support.value = await getBatchSupportByProvince(code.value)
  } catch {
    support.value = null
  } finally {
    loading.value = false
  }
}

function go(e: Entry) {
  setPrefProvince(code.value)
  if (e.kind === 'tab') {
    uni.switchTab({ url: e.url })
  } else if (e.key === 'volunteer') {
    uni.navigateTo({ url: `/pages/volunteer/volunteer?provinceCode=${code.value}` })
  } else {
    uni.navigateTo({ url: e.url })
  }
}

onLoad((options) => {
  code.value = normalizeProvinceCode(options?.provinceCode)
  name.value = getProvinceName(code.value)
  mode.value = PROVINCES.find((p) => p.code === code.value)?.workspaceMode || 'full'
  setPrefProvince(code.value)
  loadSupport()
})
</script>

<template>
  <view class="page">
    <view class="hero">
      <view class="hero-prov">{{ name }}专区</view>
      <view class="hero-mode">{{ mode === 'full' ? '完整志愿生成已开放' : '规则查询 + 历史估算' }}</view>
    </view>

    <view class="card status">
      <view class="st-title">数据就绪度</view>
      <view v-if="loading" class="st-loading">读取中…</view>
      <template v-else-if="support">
        <view class="st-row"><text class="st-k">阶段</text><text class="st-v">{{ support.recommendationPhase || '-' }}</text></view>
        <view class="st-row"><text class="st-k">目标年份</text><text class="st-v">{{ support.targetYear || '-' }}</text></view>
        <view class="st-row"><text class="st-k">官方数据</text><text class="st-v">{{ support.officialDataReady ? '已就绪' : '未发布（历史估算）' }}</text></view>
        <view class="st-row"><text class="st-k">可用批次</text><text class="st-v">{{ (support.items || []).length }} 个</text></view>
      </template>
      <view v-else class="st-loading">暂无就绪度信息</view>
    </view>

    <view class="grid">
      <view v-for="e in entries" :key="e.key" class="card entry" hover-class="entry-hover" @click="go(e)">
        <view class="e-label">{{ e.label }}</view>
        <view class="e-desc">{{ e.desc }}</view>
        <text class="e-go">进入 ›</text>
      </view>
    </view>

    <view class="notice">本专区数据仅供公益参考，请以 {{ name }} 省招生考试院与高校官方材料为准。</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 24rpx;
}
.hero {
  background: linear-gradient(135deg, $gz-primary, #3b82f6);
  border-radius: $gz-radius;
  padding: 40rpx 32rpx;
  color: #fff;
  margin-bottom: 20rpx;
}
.hero-prov {
  font-size: 44rpx;
  font-weight: 800;
}
.hero-mode {
  margin-top: 12rpx;
  font-size: 24rpx;
  opacity: 0.92;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
}
.status {
  padding: 26rpx;
  margin-bottom: 20rpx;
}
.st-title {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.st-loading {
  font-size: 24rpx;
  color: $gz-text-weak;
}
.st-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 64rpx;
  border-bottom: 1rpx solid $gz-border;
}
.st-row:last-child {
  border-bottom: none;
}
.st-k {
  font-size: 25rpx;
  color: $gz-text-sub;
}
.st-v {
  font-size: 25rpx;
  color: $gz-text;
  font-weight: 600;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}
.entry {
  width: calc(50% - 10rpx);
  padding: 26rpx 24rpx;
}
.entry-hover {
  background: #f0f4ff;
}
.e-label {
  font-size: 30rpx;
  font-weight: 700;
  color: $gz-text;
}
.e-desc {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $gz-text-sub;
  line-height: 1.6;
  min-height: 64rpx;
}
.e-go {
  margin-top: 12rpx;
  font-size: 23rpx;
  color: $gz-primary;
  font-weight: 600;
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
