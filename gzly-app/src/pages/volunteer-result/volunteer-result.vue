<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchVolunteerPlan } from '@/api/volunteer'
import { getSafetyCode, saveSafetyCode, setLastPlanId } from '@/utils/storage'
import { LIABILITY_NOTICE } from '@/constants/disclaimer'
import type { VolunteerPlan, VolunteerItem } from '@/types'

const planId = ref(0)
const plan = ref<VolunteerPlan | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const needCode = ref(false)
const inputCode = ref('')
const activeGradient = ref('全部')

async function load(code: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    plan.value = await fetchVolunteerPlan(planId.value, code)
    saveSafetyCode(planId.value, code)
    setLastPlanId(planId.value)
    needCode.value = false
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
    needCode.value = true
  } finally {
    loading.value = false
  }
}

function submitCode() {
  const c = inputCode.value.trim()
  if (!c) {
    uni.showToast({ title: '请输入安全码', icon: 'none' })
    return
  }
  load(c)
}

const gradients = computed(() => {
  const set = new Set<string>()
  ;(plan.value?.items || []).forEach((it) => {
    if (it.gradient) set.add(it.gradient)
  })
  return ['全部', ...Array.from(set)]
})

const filteredItems = computed<VolunteerItem[]>(() => {
  const items = plan.value?.items || []
  if (activeGradient.value === '全部') return items
  return items.filter((it) => it.gradient === activeGradient.value)
})

function gradClass(g?: string): string {
  if (!g) return ''
  if (g.indexOf('冲') >= 0) return 'g-chong'
  if (g.indexOf('稳') >= 0) return 'g-wen'
  if (g.indexOf('保') >= 0 || g.indexOf('安全') >= 0) return 'g-bao'
  if (g.indexOf('垫') >= 0 || g.indexOf('兜底') >= 0) return 'g-dian'
  return 'g-wen'
}

function goAi() {
  uni.navigateTo({ url: `/pages/ai-analysis/ai-analysis?id=${planId.value}` })
}
function goPoster() {
  uni.navigateTo({ url: `/pages/poster/poster?id=${planId.value}` })
}

const selectMode = ref(false)
const selected = ref<number[]>([])
const expanded = ref<number[]>([])
function isExpanded(idx: number) {
  return expanded.value.indexOf(idx) >= 0
}
function toggleCompare() {
  selectMode.value = !selectMode.value
  if (!selectMode.value) selected.value = []
}
function onCardTap(it: VolunteerItem) {
  if (selectMode.value) {
    const i = selected.value.indexOf(it.index)
    if (i >= 0) {
      selected.value.splice(i, 1)
    } else {
      if (selected.value.length >= 3) {
        uni.showToast({ title: '最多选 3 个', icon: 'none' })
        return
      }
      selected.value.push(it.index)
    }
    return
  }
  const e = expanded.value.indexOf(it.index)
  if (e >= 0) expanded.value.splice(e, 1)
  else expanded.value.push(it.index)
}
function startCompare() {
  if (selected.value.length < 2) {
    uni.showToast({ title: '至少选 2 个志愿', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/compare/compare?id=${planId.value}&idx=${selected.value.join(',')}` })
}

onLoad((options) => {
  planId.value = Number(options?.id || 0)
  const code = getSafetyCode(planId.value)
  if (code) load(code)
  else needCode.value = true
})
</script>

<template>
  <view class="page">
    <view v-if="needCode" class="card">
      <view class="sec">输入安全码查看方案</view>
      <input v-model="inputCode" class="ri" placeholder="生成时设置的安全码" />
      <view class="btn" hover-class="btn-hover" @click="submitCode">查看</view>
      <view v-if="errorMsg" class="err">{{ errorMsg }}</view>
    </view>

    <view v-if="loading" class="hint">加载中…</view>

    <template v-if="plan">
      <view class="risk">
        <view class="risk-h"><text class="risk-ic">!</text>重要：本方案仅供参考，请勿原样照搬</view>
        <text class="risk-t">{{ LIABILITY_NOTICE }}</text>
      </view>

      <view class="card summary">
        <view class="s-title">{{ plan.provinceName }} · {{ plan.targetBatch }}</view>
        <view class="s-meta">
          <text class="sm">{{ plan.totalScore }} 分</text>
          <text v-if="plan.provinceRank" class="sm">位次 {{ plan.provinceRank }}</text>
          <text v-if="plan.firstSubject" class="sm">{{ plan.firstSubject }}</text>
          <text class="sm">{{ (plan.items || []).length }} 条志愿</text>
        </view>
        <view v-if="plan.supportLevel" class="badge-trial">{{ plan.supportLevel }} · {{ plan.engineName }}</view>
        <view v-if="plan.referenceProbabilityNotice" class="s-notice">{{ plan.referenceProbabilityNotice }}</view>
      </view>

      <view v-if="plan.manualReviewItems && plan.manualReviewItems.length" class="warn">
        有 {{ plan.manualReviewItems.length }} 条志愿需人工复核（数据参考度较低），请结合官方材料核验。
      </view>

      <view class="result-actions">
        <view class="ra-btn" hover-class="btn-hover" @click="goPoster">导出长图</view>
        <view class="ra-btn ghost" :class="{ on: selectMode }" hover-class="btn-hover" @click="toggleCompare">{{ selectMode ? '取消对比' : '对比院校' }}</view>
      </view>

      <scroll-view v-if="gradients.length > 1" scroll-x class="grad-bar">
        <text
          v-for="g in gradients"
          :key="g"
          class="grad"
          :class="{ on: activeGradient === g }"
          @click="activeGradient = g"
        >{{ g }}</text>
      </scroll-view>

      <view class="list">
        <view v-for="it in filteredItems" :key="it.index" class="vcard" :class="{ sel: selectMode && selected.indexOf(it.index) >= 0 }" @click="onCardTap(it)">
          <view class="v-top">
            <view v-if="selectMode" class="vcheck" :class="{ on: selected.indexOf(it.index) >= 0 }">{{ selected.indexOf(it.index) >= 0 ? '✓' : '' }}</view>
            <text class="v-idx">{{ it.index }}</text>
            <view class="v-mid">
              <text class="v-uni">{{ it.universityName }}</text>
              <text class="v-major">{{ it.majorName }}</text>
            </view>
            <view class="v-right">
              <text v-if="it.gradient" class="v-grad" :class="gradClass(it.gradient)">{{ it.gradient }}</text>
              <text v-if="it.chanceScore !== undefined" class="v-chance">参考 {{ it.chanceScore }}</text>
            </view>
          </view>
          <view class="v-meta">
            <text v-if="it.province" class="vm">{{ it.province }}{{ it.city ? ' · ' + it.city : '' }}</text>
            <text v-if="it.historyMinScore !== undefined" class="vm">历史最低 {{ it.historyMinScore }}分</text>
            <text v-if="it.historyMinRank !== undefined" class="vm">最低位次 {{ it.historyMinRank }}</text>
            <text v-if="it.riskLevel" class="vm risk">{{ it.riskLevel }}</text>
          </view>
          <view v-if="it.needsManualReview" class="v-review">需人工复核</view>
          <view
            v-if="!selectMode && (it.recommendReason || it.riskReason)"
            class="v-toggle"
          >{{ isExpanded(it.index) ? '收起 ▲' : '查看详情 ▼' }}</view>
          <view v-if="isExpanded(it.index)" class="v-detail">
            <view v-if="it.recommendReason" class="v-d-row">
              <text class="v-d-k">推荐理由</text>
              <text class="v-d-v">{{ it.recommendReason }}</text>
            </view>
            <view v-if="it.riskReason" class="v-d-row">
              <text class="v-d-k">风险提醒</text>
              <text class="v-d-v">{{ it.riskReason }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="ai-entry" hover-class="btn-hover" @click="goAi">查看 AI 解读</view>
      <view class="notice">数据与建议仅供参考，请以各省招生考试院与高校官方材料为准，不构成录取预测。</view>

      <view v-if="selectMode" class="cmp-bar">
        <text class="cmp-n">已选 {{ selected.length }}/3（至少 2 个）</text>
        <view class="cmp-go" hover-class="btn-hover" @click="startCompare">开始对比</view>
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 26rpx;
  margin-bottom: 18rpx;
}
.sec {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.summary .s-title {
  font-size: 32rpx;
  font-weight: 800;
  color: $gz-text;
}
.s-meta {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.sm {
  font-size: 24rpx;
  color: $gz-text-sub;
}
.badge-trial {
  margin-top: 14rpx;
  display: inline-block;
  font-size: 21rpx;
  color: $gz-primary;
  background: $gz-primary-light;
  border-radius: 8rpx;
  padding: 6rpx 14rpx;
}
.s-notice {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}
.warn {
  background: $gz-warn-bg;
  border: 1rpx solid #ffe2b0;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  font-size: 23rpx;
  color: $gz-warn;
  line-height: 1.7;
  margin-bottom: 18rpx;
}
.risk {
  background: #fef2f2;
  border: 1rpx solid #fecaca;
  border-radius: 18rpx;
  padding: 20rpx 22rpx;
  margin-bottom: 18rpx;
}
.risk-h {
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 26rpx;
  font-weight: 800;
  color: #b91c1c;
  margin-bottom: 10rpx;
}
.risk-ic {
  flex: none;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: #dc2626;
  color: #fff;
  font-size: 22rpx;
  font-weight: 800;
  text-align: center;
  line-height: 32rpx;
}
.risk-t {
  display: block;
  font-size: 23rpx;
  color: #b91c1c;
  line-height: 1.8;
}
.grad-bar {
  white-space: nowrap;
  margin-bottom: 16rpx;
}
.grad {
  display: inline-block;
  font-size: 24rpx;
  color: $gz-text-sub;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 30rpx;
  padding: 10rpx 26rpx;
  margin-right: 12rpx;
}
.grad.on {
  color: #fff;
  background: $gz-primary;
  border-color: $gz-primary;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.vcard {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 22rpx;
}
.v-top {
  display: flex;
  gap: 14rpx;
  align-items: flex-start;
}
.v-idx {
  flex: none;
  width: 48rpx;
  height: 48rpx;
  border-radius: 10rpx;
  background: $gz-primary-light;
  color: $gz-primary;
  font-size: 22rpx;
  font-weight: 700;
  text-align: center;
  line-height: 48rpx;
}
.v-mid {
  flex: 1;
  min-width: 0;
}
.v-uni {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
}
.v-major {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $gz-text-sub;
}
.v-right {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
}
.v-grad {
  font-size: 21rpx;
  color: #fff;
  border-radius: 6rpx;
  padding: 3rpx 14rpx;
  background: $gz-wen;
}
.v-grad.g-chong {
  background: linear-gradient(135deg, #b91c1c, $gz-chong);
}
.v-grad.g-wen {
  background: linear-gradient(135deg, $gz-primary, $gz-wen);
}
.v-grad.g-bao {
  background: linear-gradient(135deg, #047857, $gz-bao);
}
.v-grad.g-dian {
  background: linear-gradient(135deg, $gz-accent, $gz-dian);
}
.v-chance {
  font-size: 21rpx;
  color: $gz-text-weak;
}
.v-meta {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}
.vm {
  font-size: 22rpx;
  color: $gz-text-sub;
}
.vm.risk {
  color: $gz-warn;
}
.v-toggle {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: $gz-primary;
  font-weight: 600;
}
.v-detail {
  margin-top: 12rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid $gz-border;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.v-d-row {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.v-d-k {
  font-size: 21rpx;
  color: $gz-text-weak;
  font-weight: 600;
}
.v-d-v {
  font-size: 22rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}
.v-review {
  margin-top: 10rpx;
  font-size: 21rpx;
  color: $gz-warn;
}
.ai-entry {
  margin-top: 24rpx;
  height: 86rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 29rpx;
  font-weight: 700;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.result-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.ra-btn {
  flex: 1;
  height: 78rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ra-btn.ghost {
  background: $gz-card;
  color: $gz-primary;
  border: 1rpx solid $gz-primary;
}
.ra-btn.ghost.on {
  background: $gz-primary-50;
}
.vcheck {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 1rpx solid $gz-border;
  color: #fff;
  font-size: 24rpx;
  text-align: center;
  line-height: 40rpx;
  margin-right: 4rpx;
}
.vcheck.on {
  background: $gz-primary;
  border-color: $gz-primary;
}
.vcard.sel {
  border-color: $gz-primary;
  background: $gz-primary-50;
}
.cmp-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 30rpx;
  height: 92rpx;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 18rpx;
  box-shadow: 0 12rpx 36rpx rgba(15, 23, 42, 0.16);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  z-index: 20;
}
.cmp-n {
  font-size: 25rpx;
  color: $gz-text-sub;
}
.cmp-go {
  height: 64rpx;
  padding: 0 40rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
}
.btn-hover {
  opacity: 0.85;
}
.ri {
  height: 76rpx;
  border: 1rpx solid $gz-border;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
  margin-bottom: 16rpx;
}
.btn {
  height: 80rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.err {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: #d4380d;
}
.hint {
  margin-top: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: $gz-text-weak;
}
.notice {
  margin-top: 20rpx;
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid #ffe2b0;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
