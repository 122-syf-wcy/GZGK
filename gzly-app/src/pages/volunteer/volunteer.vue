<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { generateVolunteerPlan, rankCheck } from '@/api/volunteer'
import { PROVINCES, normalizeProvinceCode, type ProvinceCode } from '@/constants/provinces'
import { DISCLAIMER_POINTS, VOLUNTEER_DISCLAIMER_VERSION } from '@/constants/disclaimer'
import { saveSafetyCode, setCurrentSafetyCode, getCurrentSafetyCode, setLastPlanId, addPlanRecord, getPrefProvince } from '@/utils/storage'
import type { RankCheckResponse } from '@/types'

const provinceNames = PROVINCES.map((p) => p.name)
const provinceIndex = ref(0)
const firstSubject = ref<'物理' | '历史'>('物理')
const RESUBJECTS = ['化学', '生物', '政治', '地理']
const resubjects = ref<string[]>(['化学', '生物'])
const score = ref('')
const rank = ref('')

const STRATEGY = ['保守型', '均衡型', '冲刺型']
const DECISION = ['专业优先', '学校优先']
const CAREER = ['就业优先', '升学优先', '城市机会优先']
const BUDGET = ['低预算', '均衡预算', '不限制']
const strategyIndex = ref(1)
const decisionIndex = ref(0)
const careerIndex = ref(0)
const budgetIndex = ref(1)
const acceptPrivate = ref(false)
const acceptSinoForeign = ref(false)

const safetyCode = ref(getCurrentSafetyCode())
const accepted = ref(false)
const rankInfo = ref<RankCheckResponse | null>(null)
const generating = ref(false)

onLoad((options) => {
  const code = normalizeProvinceCode(options?.provinceCode || getPrefProvince())
  const i = PROVINCES.findIndex((p) => p.code === code)
  if (i >= 0) provinceIndex.value = i
})

function toggleResub(s: string) {
  const i = resubjects.value.indexOf(s)
  if (i >= 0) resubjects.value.splice(i, 1)
  else {
    if (resubjects.value.length >= 2) {
      uni.showToast({ title: '再选科目最多选 2 个', icon: 'none' })
      return
    }
    resubjects.value.push(s)
  }
}

async function checkRank() {
  if (!score.value) return
  try {
    rankInfo.value = await rankCheck({
      provinceCode: PROVINCES[provinceIndex.value].code as ProvinceCode,
      totalScore: Number(score.value),
      provinceRank: rank.value ? Number(rank.value) : undefined,
      firstSubject: firstSubject.value,
    })
  } catch {
    rankInfo.value = null
  }
}

function validCode(c: string): boolean {
  const n = c.trim().replace(/[-\s]/g, '')
  return /^[A-Za-z0-9]{6,24}$/.test(n)
}

async function generate() {
  if (!score.value) {
    uni.showToast({ title: '请输入高考总分', icon: 'none' })
    return
  }
  if (!resubjects.value.length) {
    uni.showToast({ title: '请选择再选科目', icon: 'none' })
    return
  }
  if (!validCode(safetyCode.value)) {
    uni.showToast({ title: '安全码需 6-24 位字母或数字', icon: 'none' })
    return
  }
  if (!accepted.value) {
    uni.showToast({ title: '请阅读并确认风险告知', icon: 'none' })
    return
  }
  generating.value = true
  uni.showLoading({ title: '生成中…', mask: true })
  try {
    const plan = await generateVolunteerPlan({
      provinceCode: PROVINCES[provinceIndex.value].code as ProvinceCode,
      totalScore: Number(score.value),
      provinceRank: rank.value ? Number(rank.value) : 0,
      firstSubject: firstSubject.value,
      resubjects: resubjects.value,
      strategyMode: STRATEGY[strategyIndex.value] as '保守型' | '均衡型' | '冲刺型',
      decisionPriority: DECISION[decisionIndex.value] as '学校优先' | '专业优先',
      careerGoal: CAREER[careerIndex.value] as '就业优先' | '升学优先' | '城市机会优先',
      tuitionBudget: BUDGET[budgetIndex.value] as '低预算' | '均衡预算' | '不限制',
      acceptPrivate: acceptPrivate.value,
      acceptSinoForeign: acceptSinoForeign.value,
      safetyCode: safetyCode.value.trim(),
      agreedDisclaimer: true,
      disclaimerVersion: VOLUNTEER_DISCLAIMER_VERSION,
    })
    const code = plan.safetyCode || safetyCode.value.trim()
    saveSafetyCode(plan.id, code)
    setCurrentSafetyCode(code)
    setLastPlanId(plan.id)
    addPlanRecord({
      planId: plan.id,
      provinceName: plan.provinceName,
      totalScore: plan.totalScore,
      itemCount: (plan.items || []).length,
      createdAt: Date.now(),
    })
    uni.hideLoading()
    uni.navigateTo({ url: `/pages/volunteer-result/volunteer-result?id=${plan.id}` })
  } catch (e) {
    uni.hideLoading()
    uni.showModal({ title: '生成失败', content: (e as Error).message, showCancel: false })
  } finally {
    generating.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="sec">基本信息</view>
      <picker mode="selector" :range="provinceNames" :value="provinceIndex" @change="(e: any) => (provinceIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">省份</text><text class="rv">{{ provinceNames[provinceIndex] }}</text></view>
      </picker>
      <view class="row">
        <text class="rk">首选科目</text>
        <view class="seg">
          <text class="seg-i" :class="{ on: firstSubject === '物理' }" @click="firstSubject = '物理'">物理</text>
          <text class="seg-i" :class="{ on: firstSubject === '历史' }" @click="firstSubject = '历史'">历史</text>
        </view>
      </view>
      <view class="resub">
        <text class="rk">再选科目</text>
        <view class="chips">
          <text v-for="s in RESUBJECTS" :key="s" class="chip" :class="{ on: resubjects.indexOf(s) >= 0 }" @click="toggleResub(s)">{{ s }}</text>
        </view>
      </view>
      <view class="row"><text class="rk">高考总分</text><input v-model="score" class="ri" type="number" placeholder="必填" @blur="checkRank" /></view>
      <view class="row"><text class="rk">省内位次</text><input v-model="rank" class="ri" type="number" placeholder="可选，留空将保守估算" /></view>
    </view>

    <view v-if="rankInfo" class="rankinfo">
      <text class="ri-t">{{ rankInfo.subjectType }} · {{ rankInfo.referenceYear }} 年参考</text>
      <text class="ri-v">{{ score }} 分位次约 {{ rankInfo.rankLow }}-{{ rankInfo.rankHigh }} 名</text>
      <text class="ri-note">{{ rankInfo.reminder }}</text>
    </view>

    <view class="card">
      <view class="sec">填报偏好</view>
      <picker mode="selector" :range="STRATEGY" :value="strategyIndex" @change="(e: any) => (strategyIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">策略</text><text class="rv">{{ STRATEGY[strategyIndex] }}</text></view>
      </picker>
      <picker mode="selector" :range="DECISION" :value="decisionIndex" @change="(e: any) => (decisionIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">优先</text><text class="rv">{{ DECISION[decisionIndex] }}</text></view>
      </picker>
      <picker mode="selector" :range="CAREER" :value="careerIndex" @change="(e: any) => (careerIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">目标</text><text class="rv">{{ CAREER[careerIndex] }}</text></view>
      </picker>
      <picker mode="selector" :range="BUDGET" :value="budgetIndex" @change="(e: any) => (budgetIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">预算</text><text class="rv">{{ BUDGET[budgetIndex] }}</text></view>
      </picker>
      <view class="sw"><text class="swk">接受民办院校</text><switch :checked="acceptPrivate" @change="(e: any) => (acceptPrivate = e.detail.value)" /></view>
      <view class="sw"><text class="swk">接受中外合作</text><switch :checked="acceptSinoForeign" @change="(e: any) => (acceptSinoForeign = e.detail.value)" /></view>
    </view>

    <view class="card">
      <view class="sec">安全码</view>
      <input v-model="safetyCode" class="ri full" placeholder="6-24 位字母或数字，用于以后找回方案" />
      <view class="code-tip">请牢记安全码：方案找回、AI 解读、导出都需要它，系统不保存明文。</view>
    </view>

    <view class="card risk">
      <view class="sec">风险告知</view>
      <view v-for="(p, i) in DISCLAIMER_POINTS" :key="i" class="risk-p">· {{ p }}</view>
      <view class="accept" @click="accepted = !accepted">
        <view class="cbox" :class="{ on: accepted }">{{ accepted ? '✓' : '' }}</view>
        <text class="accept-text">我已阅读并理解上述风险告知，知悉结果仅为参考。</text>
      </view>
    </view>

    <view class="submit" :class="{ disabled: generating }" hover-class="btn-hover" @click="generate">
      {{ generating ? '生成中…' : '生成志愿参考方案' }}
    </view>
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
.row {
  display: flex;
  align-items: center;
  height: 80rpx;
  border-bottom: 1rpx solid $gz-border;
}
.rk {
  flex: none;
  width: 150rpx;
  font-size: 26rpx;
  color: $gz-text-sub;
}
.rv {
  flex: 1;
  font-size: 26rpx;
  color: $gz-text;
  font-weight: 600;
}
.ri {
  flex: 1;
  font-size: 26rpx;
}
.ri.full {
  width: 100%;
  height: 76rpx;
  border: 1rpx solid $gz-border;
  border-radius: 12rpx;
  padding: 0 20rpx;
}
.seg {
  display: flex;
  gap: 12rpx;
}
.seg-i {
  font-size: 25rpx;
  color: $gz-text-sub;
  background: #f1f4f9;
  border-radius: 10rpx;
  padding: 10rpx 28rpx;
}
.seg-i.on {
  color: #fff;
  background: $gz-primary;
}
.resub {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $gz-border;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  flex: 1;
}
.chip {
  font-size: 24rpx;
  color: $gz-text-sub;
  background: #f1f4f9;
  border: 1rpx solid transparent;
  border-radius: 30rpx;
  padding: 10rpx 24rpx;
}
.chip.on {
  color: $gz-primary;
  background: $gz-primary-light;
  border-color: $gz-primary;
}
.rankinfo {
  background: $gz-primary-light;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.ri-t {
  font-size: 22rpx;
  color: $gz-primary;
}
.ri-v {
  font-size: 27rpx;
  font-weight: 700;
  color: $gz-text;
}
.ri-note {
  font-size: 21rpx;
  color: $gz-text-weak;
  line-height: 1.6;
}
.sw {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 80rpx;
}
.swk {
  font-size: 26rpx;
  color: $gz-text-sub;
}
.code-tip {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: $gz-warn;
  line-height: 1.6;
}
.risk-p {
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.8;
}
.accept {
  margin-top: 16rpx;
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}
.cbox {
  flex: none;
  width: 40rpx;
  height: 40rpx;
  border: 1rpx solid $gz-border;
  border-radius: 8rpx;
  color: #fff;
  font-size: 26rpx;
  text-align: center;
  line-height: 40rpx;
}
.cbox.on {
  background: $gz-primary;
  border-color: $gz-primary;
}
.accept-text {
  flex: 1;
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}
.submit {
  height: 88rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.submit.disabled {
  opacity: 0.6;
}
.btn-hover {
  opacity: 0.85;
}
</style>
