<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchMajorPlannerResult, generateMajorPlannerAiAnalysis, type MajorPlannerView } from '@/api/majorPlanner'
import { getMajorPlanCode, saveMajorPlanCode } from '@/utils/storage'
import { mdToLines } from '@/utils/markdown'

const planId = ref(0)
const view = ref<MajorPlannerView | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const needCode = ref(false)
const inputCode = ref('')

const aiLines = ref<string[]>([])
const aiLoading = ref(false)

async function load(code: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    view.value = await fetchMajorPlannerResult(planId.value, code)
    saveMajorPlanCode(planId.value, code)
    needCode.value = false
    if (view.value.aiSummary) aiLines.value = mdToLines(view.value.aiSummary)
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
    uni.showToast({ title: '请输入规划码', icon: 'none' })
    return
  }
  load(c)
}

async function genAi() {
  const code = getMajorPlanCode(planId.value)
  if (!code) {
    uni.showToast({ title: '缺少规划码', icon: 'none' })
    return
  }
  aiLoading.value = true
  try {
    const res = await generateMajorPlannerAiAnalysis(planId.value, code)
    aiLines.value = mdToLines(res.content)
    if (res.fallbackUsed) uni.showToast({ title: 'AI 暂不可用，已用规则解读', icon: 'none' })
  } catch (e) {
    uni.showModal({ title: 'AI 解读失败', content: (e as Error).message, showCancel: false })
  } finally {
    aiLoading.value = false
  }
}

function copyCode() {
  const code = getMajorPlanCode(planId.value)
  if (!code) return
  uni.setClipboardData({ data: code, success: () => uni.showToast({ title: '规划码已复制', icon: 'none' }) })
}

onLoad((options) => {
  planId.value = Number(options?.id || 0)
  const code = getMajorPlanCode(planId.value)
  if (code) load(code)
  else needCode.value = true
})
</script>

<template>
  <view class="page">
    <view v-if="needCode" class="card">
      <view class="sec">输入规划码查看结果</view>
      <input v-model="inputCode" class="ri" placeholder="规划码" />
      <view class="btn" hover-class="btn-hover" @click="submitCode">查看</view>
      <view v-if="errorMsg" class="err">{{ errorMsg }}</view>
    </view>

    <view v-if="loading" class="hint">加载中…</view>

    <template v-if="view">
      <view class="card">
        <view class="planno">规划编号 {{ view.planNo }}</view>
        <view class="code-line">
          <text class="code-masked">规划码 {{ view.planCodeMasked }}</text>
          <text class="copy" @click="copyCode">复制</text>
        </view>
        <view class="summary">{{ view.result.ruleSummary }}</view>
      </view>

      <view v-if="view.result.radar.length" class="card">
        <view class="sec">大类匹配度</view>
        <view v-for="r in view.result.radar" :key="r.category" class="bar-row">
          <text class="bar-label">{{ r.category }}</text>
          <view class="bar-track"><view class="bar-fill" :style="{ width: r.score + '%' }" /></view>
          <text class="bar-score">{{ r.score }}</text>
        </view>
      </view>

      <view class="card">
        <view class="sec">推荐专业方向 Top {{ view.result.topMajors.length }}</view>
        <view v-for="(m, i) in view.result.topMajors" :key="i" class="major">
          <view class="major-top">
            <text class="major-name">{{ i + 1 }}. {{ m.majorName }}</text>
            <text class="major-score">{{ m.matchScore }}</text>
          </view>
          <view v-if="m.reasons && m.reasons.length" class="major-reason">{{ m.reasons.join('；') }}</view>
          <view v-if="m.subjectRequirement" class="major-sub">选科：{{ m.subjectRequirement }}</view>
          <view v-if="m.constraintWarnings && m.constraintWarnings.length" class="major-warn">{{ m.constraintWarnings.join('；') }}</view>
        </view>
      </view>

      <view v-if="view.result.notRecommended.length" class="card">
        <view class="sec">暂不建议优先</view>
        <view v-for="(n, i) in view.result.notRecommended" :key="i" class="nr">
          <text class="nr-dir">{{ n.direction }}</text>
          <text class="nr-reason">{{ n.reason }}</text>
        </view>
      </view>

      <view class="card">
        <view class="sec">AI 深度解读（可选）</view>
        <view v-if="!aiLines.length" class="btn" :class="{ disabled: aiLoading }" hover-class="btn-hover" @click="genAi">
          {{ aiLoading ? '生成中…' : '生成 AI 解读' }}
        </view>
        <view v-else class="ai">
          <text v-for="(l, i) in aiLines" :key="i" class="ai-line">{{ l }}</text>
        </view>
      </view>

      <view class="notice">{{ view.disclaimer || view.result.disclaimer }}</view>
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
  margin-bottom: 18rpx;
}
.planno {
  font-size: 24rpx;
  color: $gz-text-weak;
}
.code-line {
  margin-top: 10rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.code-masked {
  font-size: 26rpx;
  color: $gz-text;
  font-weight: 700;
}
.copy {
  font-size: 24rpx;
  color: $gz-primary;
}
.summary {
  margin-top: 16rpx;
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.8;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 16rpx;
}
.bar-label {
  flex: none;
  width: 150rpx;
  font-size: 23rpx;
  color: $gz-text-sub;
}
.bar-track {
  flex: 1;
  height: 18rpx;
  background: #eef1f6;
  border-radius: 10rpx;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: $gz-primary;
  border-radius: 10rpx;
}
.bar-score {
  flex: none;
  width: 56rpx;
  text-align: right;
  font-size: 23rpx;
  color: $gz-text;
  font-weight: 600;
}
.major {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $gz-border;
}
.major-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.major-name {
  font-size: 27rpx;
  font-weight: 700;
  color: $gz-text;
  flex: 1;
}
.major-score {
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-primary;
}
.major-reason {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.6;
}
.major-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $gz-text-weak;
}
.major-warn {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $gz-warn;
}
.nr {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid $gz-border;
}
.nr-dir {
  font-size: 25rpx;
  font-weight: 600;
  color: $gz-text;
}
.nr-reason {
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.6;
}
.ai {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.ai-line {
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.75;
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
.btn.disabled {
  opacity: 0.6;
}
.btn-hover {
  opacity: 0.85;
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
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid #ffe2b0;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
