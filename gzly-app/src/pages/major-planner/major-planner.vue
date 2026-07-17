<script setup lang="ts">
import { ref } from 'vue'
import { evaluateMajorPlanner, restoreMajorPlanner, type MajorPlannerEvaluateRequest } from '@/api/majorPlanner'
import { saveMajorPlanCode, setLastMajorPlanId } from '@/utils/storage'

const SUBJECT_CATEGORIES = ['物理类', '历史类']
const SUBJECTS = ['语文', '数学', '外语', '物理', '化学', '生物', '政治', '历史', '地理']
const DIRECTIONS = ['计算机与人工智能', '电子信息', '医学健康', '财经管理', '法律政务', '师范教育', '语言文学', '艺术设计', '理学研究', '农林生命', '机械制造', '土木建筑']
const CAREERS = ['就业优先', '升学优先', '考公考编', '收入优先', '稳定优先', '回家乡发展']
const TRAITS = ['逻辑分析', '动手实践', '沟通表达', '组织管理', '创意审美', '细心耐心']
const BUDGETS = ['低预算', '均衡预算', '不限制']

const subjectIndex = ref(0)
const budgetIndex = ref(1)
const score = ref('')
const rank = ref('')
const likedSubjects = ref<string[]>([])
const interestDirections = ref<string[]>([])
const careerExpectations = ref<string[]>([])
const personalityTraits = ref<string[]>([])
const avoidDirections = ref<string[]>([])
const acceptMedicine = ref(true)
const acceptTeacher = ref(true)
const acceptAgriculture = ref(true)
const acceptSinoForeign = ref(true)
const acceptPrivate = ref(true)

const submitting = ref(false)
const restoreCode = ref('')

function toggle(arr: string[], val: string) {
  const i = arr.indexOf(val)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(val)
}
function has(arr: string[], val: string) {
  return arr.indexOf(val) >= 0
}

async function submit() {
  if (!likedSubjects.value.length && !interestDirections.value.length && !careerExpectations.value.length) {
    uni.showToast({ title: '请至少选择喜欢的学科 / 兴趣方向 / 职业期待之一', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const payload: MajorPlannerEvaluateRequest = {
      subjectCategory: SUBJECT_CATEGORIES[subjectIndex.value],
      score: score.value ? Number(score.value) : null,
      rank: rank.value ? Number(rank.value) : null,
      likedSubjects: likedSubjects.value,
      dislikedSubjects: [],
      interestDirections: interestDirections.value,
      personalityTraits: personalityTraits.value,
      careerExpectations: careerExpectations.value,
      acceptMedicine: acceptMedicine.value,
      acceptTeacher: acceptTeacher.value,
      acceptAgriculture: acceptAgriculture.value,
      acceptSinoForeign: acceptSinoForeign.value,
      acceptPrivate: acceptPrivate.value,
      familyBudget: BUDGETS[budgetIndex.value],
      cityPreferences: [],
      avoidDirections: avoidDirections.value,
    }
    const view = await evaluateMajorPlanner(payload)
    if (view.planCode) saveMajorPlanCode(view.id, view.planCode)
    setLastMajorPlanId(view.id)
    uni.navigateTo({ url: `/pages/major-planner-result/major-planner-result?id=${view.id}` })
  } catch (e) {
    uni.showModal({ title: '提交失败', content: (e as Error).message, showCancel: false })
  } finally {
    submitting.value = false
  }
}

async function doRestore() {
  const code = restoreCode.value.trim()
  if (!code) {
    uni.showToast({ title: '请输入规划码', icon: 'none' })
    return
  }
  try {
    const view = await restoreMajorPlanner(code)
    saveMajorPlanCode(view.id, code)
    setLastMajorPlanId(view.id)
    uni.navigateTo({ url: `/pages/major-planner-result/major-planner-result?id=${view.id}` })
  } catch (e) {
    uni.showModal({ title: '找回失败', content: (e as Error).message, showCancel: false })
  }
}
</script>

<template>
  <view class="page">
    <view class="card restore">
      <input v-model="restoreCode" class="ri" placeholder="已有规划码？输入找回" />
      <view class="rb" hover-class="btn-hover" @click="doRestore">找回</view>
    </view>

    <view class="card">
      <view class="sec">基础信息</view>
      <picker mode="selector" :range="SUBJECT_CATEGORIES" :value="subjectIndex" @change="(e: any) => (subjectIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">科类</text><text class="rv">{{ SUBJECT_CATEGORIES[subjectIndex] }}</text></view>
      </picker>
      <view class="row"><text class="rk">分数</text><input v-model="score" class="ri2" type="number" placeholder="可选" /></view>
      <view class="row"><text class="rk">位次</text><input v-model="rank" class="ri2" type="number" placeholder="可选" /></view>
      <picker mode="selector" :range="BUDGETS" :value="budgetIndex" @change="(e: any) => (budgetIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">预算</text><text class="rv">{{ BUDGETS[budgetIndex] }}</text></view>
      </picker>
    </view>

    <view class="card">
      <view class="sec">喜欢的学科</view>
      <view class="chips">
        <text v-for="s in SUBJECTS" :key="s" class="chip" :class="{ on: has(likedSubjects, s) }" @click="toggle(likedSubjects, s)">{{ s }}</text>
      </view>
    </view>

    <view class="card">
      <view class="sec">兴趣方向</view>
      <view class="chips">
        <text v-for="d in DIRECTIONS" :key="d" class="chip" :class="{ on: has(interestDirections, d) }" @click="toggle(interestDirections, d)">{{ d }}</text>
      </view>
    </view>

    <view class="card">
      <view class="sec">职业期待</view>
      <view class="chips">
        <text v-for="c in CAREERS" :key="c" class="chip" :class="{ on: has(careerExpectations, c) }" @click="toggle(careerExpectations, c)">{{ c }}</text>
      </view>
    </view>

    <view class="card">
      <view class="sec">性格倾向</view>
      <view class="chips">
        <text v-for="t in TRAITS" :key="t" class="chip" :class="{ on: has(personalityTraits, t) }" @click="toggle(personalityTraits, t)">{{ t }}</text>
      </view>
    </view>

    <view class="card">
      <view class="sec">想避开的方向</view>
      <view class="chips">
        <text v-for="d in DIRECTIONS" :key="'a' + d" class="chip" :class="{ on: has(avoidDirections, d) }" @click="toggle(avoidDirections, d)">{{ d }}</text>
      </view>
    </view>

    <view class="card">
      <view class="sec">接受度</view>
      <view class="sw"><text class="swk">医学类</text><switch :checked="acceptMedicine" @change="(e: any) => (acceptMedicine = e.detail.value)" /></view>
      <view class="sw"><text class="swk">师范类</text><switch :checked="acceptTeacher" @change="(e: any) => (acceptTeacher = e.detail.value)" /></view>
      <view class="sw"><text class="swk">农林类</text><switch :checked="acceptAgriculture" @change="(e: any) => (acceptAgriculture = e.detail.value)" /></view>
      <view class="sw"><text class="swk">中外合作</text><switch :checked="acceptSinoForeign" @change="(e: any) => (acceptSinoForeign = e.detail.value)" /></view>
      <view class="sw"><text class="swk">民办院校</text><switch :checked="acceptPrivate" @change="(e: any) => (acceptPrivate = e.detail.value)" /></view>
    </view>

    <view class="notice">本工具仅做专业方向参考，不生成志愿表、不承诺录取或就业。结果由规则匹配生成，AI 解读为可选增强。</view>

    <view class="submit" :class="{ disabled: submitting }" hover-class="btn-hover" @click="submit">
      {{ submitting ? '匹配中…' : '生成专业方向匹配' }}
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
.restore {
  display: flex;
  gap: 14rpx;
  align-items: center;
}
.ri {
  flex: 1;
  height: 70rpx;
  border: 1rpx solid $gz-border;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}
.rb {
  flex: none;
  height: 70rpx;
  padding: 0 30rpx;
  background: $gz-primary-light;
  color: $gz-primary;
  font-weight: 700;
  font-size: 26rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
}
.sec {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 18rpx;
}
.row {
  display: flex;
  align-items: center;
  height: 72rpx;
  border-bottom: 1rpx solid $gz-border;
}
.rk {
  flex: none;
  width: 110rpx;
  font-size: 26rpx;
  color: $gz-text-sub;
}
.rv {
  flex: 1;
  font-size: 26rpx;
  color: $gz-text;
  font-weight: 600;
}
.ri2 {
  flex: 1;
  font-size: 26rpx;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}
.chip {
  font-size: 24rpx;
  color: $gz-text-sub;
  background: #f1f4f9;
  border: 1rpx solid transparent;
  border-radius: 30rpx;
  padding: 12rpx 24rpx;
}
.chip.on {
  color: $gz-primary;
  background: $gz-primary-light;
  border-color: $gz-primary;
}
.sw {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 76rpx;
  border-bottom: 1rpx solid $gz-border;
}
.swk {
  font-size: 26rpx;
  color: $gz-text-sub;
}
.notice {
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid #ffe2b0;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
  margin-bottom: 18rpx;
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
