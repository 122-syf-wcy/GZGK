<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import FloatingTabBar from '@/components/FloatingTabBar.vue'
import GzButton from '@/components/GzButton.vue'
import GzChoice from '@/components/GzChoice.vue'
import GzDisclaimerSheet from '@/components/GzDisclaimerSheet.vue'
import GzField from '@/components/GzField.vue'
import GzTag from '@/components/GzTag.vue'
import { generatePlan, getBatchSupport, rankCheck } from '@/api/volunteer'
import { claimPlan } from '@/api/auth'
import { getHotMajors } from '@/api/query'
import { getProvinceText, normalizeProvinceCode, PROVINCE_LIST } from '@/constants/provinces'
import { useAuthStore } from '@/stores/auth'
import { usePlanStore } from '@/stores/plan'
import { switchValue } from '@/utils/request'
import type { BatchSupport, ProvinceCode, RawRankCheckResponse } from '@/types'

const store = usePlanStore()
const auth = useAuthStore()

const provinceCode = ref<ProvinceCode>('GZ')
const province = computed(() => getProvinceText(provinceCode.value))

const support = ref<BatchSupport>()
const supportLoading = ref(true)
const primaryBatch = computed(() =>
  support.value?.items.find(i => i.generatorReady) || support.value?.items[0],
)
const locked = computed(() => !support.value?.anyGeneratorReady)

// ---- 表单字段 ----
const scoreText = ref('')
const rankText = ref('')
const firstSubject = ref<'物理' | '历史'>('物理')
const resubjects = ref<string[]>([])
const strategyMode = ref('均衡型')
const decisionPriority = ref('专业优先')
const careerGoal = ref('就业优先')
const tuitionBudget = ref('均衡预算')
const acceptPrivate = ref(true)
const acceptSinoForeign = ref(false)
const advancedOpen = ref(false)

const RESUBJECT_OPTIONS = [
  { value: '化学', label: '化学' },
  { value: '生物', label: '生物' },
  { value: '政治', label: '政治' },
  { value: '地理', label: '地理' },
]

// ---- 意向方向（可选）----
const preferredMajors = ref<string[]>([])
const preferredRegions = ref<string[]>([])
const majorOptions = ref<Array<{ value: string; label: string }>>([])

const REGION_OPTIONS = [
  '省内', '贵阳', '成都', '重庆', '武汉', '长沙', '西安', '昆明',
  '长三角', '珠三角', '京津',
].map(r => ({ value: r, label: r }))

let majorSeq = 0

async function loadHotMajors() {
  const seq = ++majorSeq
  try {
    const majors = await getHotMajors(firstSubject.value === '历史' ? '历史类' : '物理类', 12)
    if (seq !== majorSeq) return
    majorOptions.value = majors.map(m => ({ value: m.name, label: m.name }))
  } catch {
    // 热门专业只是输入辅助，拉不到就不展示，不阻塞表单
    if (seq === majorSeq) majorOptions.value = []
  }
}

/** 重进表单时回填上次填写的内容，反复调整重新生成是高频路径 */
function restoreFromStore() {
  const f = store.form
  if (!f.totalScore) return
  scoreText.value = String(f.totalScore)
  rankText.value = f.provinceRank ? String(f.provinceRank) : ''
  firstSubject.value = f.firstSubject
  resubjects.value = [...f.resubjects]
  strategyMode.value = f.strategyMode
  decisionPriority.value = f.decisionPriority
  careerGoal.value = f.careerGoal
  tuitionBudget.value = f.tuitionBudget
  acceptPrivate.value = f.acceptPrivate
  acceptSinoForeign.value = f.acceptSinoForeign
  preferredMajors.value = [...f.preferredMajors]
  preferredRegions.value = [...f.preferredRegions]
  if (f.preferredMajors.length || f.preferredRegions.length) advancedOpen.value = true
}

const rankInfo = ref<RawRankCheckResponse>()
const rankChecking = ref(false)

const scoreError = computed(() => {
  if (!scoreText.value) return ''
  const n = Number(scoreText.value)
  if (Number.isNaN(n) || n < 1 || n > 750) return '总分应在 1 到 750 之间'
  return ''
})

const rankMismatch = computed(() => {
  const info = rankInfo.value
  const rank = Number(rankText.value)
  if (!info?.rankLow || !info?.rankHigh || !rank) return false
  return rank < info.rankLow - 3000 || rank > info.rankHigh + 3000
})

const canSubmit = computed(() =>
  !locked.value
  && !scoreError.value
  && Number(scoreText.value) > 0
  && Number(rankText.value) > 0
  && resubjects.value.length === 2,
)

// 快速切换省份时旧响应可能后到，用序号守卫丢弃过期结果
let supportSeq = 0

async function loadSupport() {
  const seq = ++supportSeq
  supportLoading.value = true
  try {
    const res = await getBatchSupport(provinceCode.value)
    if (seq !== supportSeq) return
    support.value = res
  } catch {
    if (seq !== supportSeq) return
    support.value = undefined
  } finally {
    if (seq === supportSeq) supportLoading.value = false
  }
}

let rankTimer: ReturnType<typeof setTimeout> | undefined
watch([scoreText, firstSubject], () => {
  const n = Number(scoreText.value)
  if (!n || scoreError.value) {
    rankInfo.value = undefined
    return
  }
  if (rankTimer) clearTimeout(rankTimer)
  // 防抖：用户还在输入时不打接口
  rankTimer = setTimeout(async () => {
    rankChecking.value = true
    try {
      rankInfo.value = await rankCheck({
        provinceCode: provinceCode.value,
        totalScore: n,
        firstSubject: firstSubject.value,
      })
    } catch {
      rankInfo.value = undefined
    } finally {
      rankChecking.value = false
    }
  }, 600)
})

/** 系统只提示区间，不代填。用户显式点击才写入。 */
function useEstimatedRank() {
  if (!rankInfo.value?.rankHigh) return
  rankText.value = String(rankInfo.value.rankHigh)
}

function switchProvince(code: ProvinceCode) {
  if (code === provinceCode.value) return
  provinceCode.value = code
  rankInfo.value = undefined
  loadSupport()
}

// ---- 提交 ----
const disclaimerVisible = ref(false)
const submitting = ref(false)

function onSubmit() {
  if (!canSubmit.value) return
  disclaimerVisible.value = true
}

async function onDisclaimerConfirm(version: string) {
  disclaimerVisible.value = false
  submitting.value = true
  uni.showLoading({ title: '正在生成方案', mask: true })
  try {
    store.setForm({
      provinceCode: provinceCode.value,
      totalScore: Number(scoreText.value),
      provinceRank: Number(rankText.value),
      firstSubject: firstSubject.value,
      resubjects: resubjects.value,
      preferredMajors: preferredMajors.value,
      preferredRegions: preferredRegions.value,
      strategyMode: strategyMode.value as never,
      decisionPriority: decisionPriority.value,
      careerGoal: careerGoal.value,
      tuitionBudget: tuitionBudget.value,
      acceptPrivate: acceptPrivate.value,
      acceptSinoForeign: acceptSinoForeign.value,
      agreedDisclaimer: true,
      disclaimerVersion: version,
    })
    const plan = await generatePlan(store.form)
    store.setPlan(plan)
    // 已登录时静默把新方案绑定到账号，失败不影响主流程（仍有安全码兜底）
    if (auth.isLoggedIn) {
      claimPlan(plan.id, plan.safetyCode).catch(() => undefined)
    }
    uni.hideLoading()
    uni.navigateTo({ url: `/pages/volunteer/result?planId=${plan.id}` })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: (e as Error).message || '生成失败', icon: 'none', duration: 2600 })
  } finally {
    submitting.value = false
  }
}

onLoad((query) => {
  // 路由参数优先；无参数时沿用上次填写的省份
  provinceCode.value = query?.provinceCode
    ? normalizeProvinceCode(query.provinceCode)
    : store.form.totalScore ? store.form.provinceCode : normalizeProvinceCode(undefined)
  restoreFromStore()
  loadSupport()
  loadHotMajors()
})

watch(firstSubject, () => loadHotMajors())

onShow(() => {
  if (!support.value && !supportLoading.value) loadSupport()
})
</script>

<template>
  <view class="page">
    <view class="head">
      <text class="head__title">智能志愿</text>
      <text class="head__desc">
        先确定地区与选科，位次、筛选范围与志愿单位都以此为准。
      </text>
    </view>

    <!-- 地区 -->
    <view class="block">
      <text class="block__label">当前地区</text>
      <scroll-view class="prov" scroll-x :show-scrollbar="false">
        <view class="prov__row">
          <view
            v-for="p in PROVINCE_LIST"
            :key="p.code"
            class="prov__item"
            :class="{ 'prov__item--active': p.code === provinceCode }"
            @tap="switchProvince(p.code)"
          >
            <text class="prov__text">{{ p.shortName }}</text>
          </view>
        </view>
      </scroll-view>

      <view class="batch">
        <template v-if="supportLoading">
          <text class="batch__text">批次状态读取中…</text>
        </template>
        <template v-else-if="!support">
          <text class="batch__text batch__text--warn">批次状态读取失败</text>
          <text class="batch__retry" @tap="loadSupport">重试</text>
        </template>
        <template v-else>
          <view class="batch__row">
            <text class="batch__name">{{ primaryBatch?.batchName }}</text>
            <GzTag :tone="locked ? 'warn' : 'ok'">
              {{ locked ? '暂不开放生成' : `${primaryBatch?.targetCount} 个 · ${primaryBatch?.volunteerMode}` }}
            </GzTag>
          </view>
          <text v-if="locked" class="batch__reason">{{ primaryBatch?.supportReason }}</text>
          <text v-else-if="support.warnings.length" class="batch__reason">{{ support.warnings[0] }}</text>
        </template>
      </view>
    </view>

    <!-- 成绩与选科 -->
    <view class="block">
      <text class="block__label">成绩与选科</text>

      <GzField
        v-model="scoreText"
        label="高考总分"
        type="number"
        placeholder="请输入总分"
        suffix="分"
        :error="scoreError"
        :hint="rankChecking ? '正在查询官方一分一段区间…' : ''"
      />

      <view v-if="rankInfo?.officialDataReady" class="rankhint">
        <text class="rankhint__text">
          {{ rankInfo.referenceYear }} 年{{ rankInfo.subjectType }}同分位次区间
          <text class="rankhint__strong">{{ rankInfo.rankLow }} – {{ rankInfo.rankHigh }}</text>
        </text>
        <text class="rankhint__use" @tap="useEstimatedRank">填入保守位次</text>
      </view>

      <GzField
        v-model="rankText"
        label="全省位次"
        type="number"
        placeholder="请按官方一分一段表填写"
        :error="rankMismatch ? '与官方区间偏差较大，请再次核对' : ''"
        :hint="rankInfo?.reminder || '系统不会替你写入位次，请以官方一分一段表为准。'"
        class="field-gap"
      />

      <text class="sub">首选科目</text>
      <GzChoice
        v-model="firstSubject"
        :options="[{ value: '物理', label: '物理' }, { value: '历史', label: '历史' }]"
      />

      <text class="sub">再选科目（选 2 门）</text>
      <GzChoice v-model="resubjects" :options="RESUBJECT_OPTIONS" multiple :max="2" />
    </view>

    <!-- 策略 -->
    <view class="block">
      <text class="block__label">填报策略</text>

      <text class="sub">方案风格</text>
      <GzChoice
        v-model="strategyMode"
        :options="[
          { value: '保守型', label: '保守型' },
          { value: '均衡型', label: '均衡型' },
          { value: '冲刺型', label: '冲刺型' },
        ]"
      />

      <text class="sub">优先级</text>
      <GzChoice
        v-model="decisionPriority"
        :options="[{ value: '专业优先', label: '专业优先' }, { value: '学校优先', label: '学校优先' }]"
      />

      <view class="adv" @tap="advancedOpen = !advancedOpen">
        <text class="adv__text">{{ advancedOpen ? '收起高级偏好' : '展开高级偏好（可选）' }}</text>
        <text class="adv__icon">{{ advancedOpen ? '▲' : '▼' }}</text>
      </view>

      <template v-if="advancedOpen">
        <template v-if="majorOptions.length">
          <text class="sub">意向专业（最多 5 个，参与筛选与排序）</text>
          <GzChoice v-model="preferredMajors" :options="majorOptions" multiple :max="5" />
        </template>

        <text class="sub">意向地区（最多 3 个）</text>
        <GzChoice v-model="preferredRegions" :options="REGION_OPTIONS" multiple :max="3" />

        <text class="sub">长期目标</text>
        <GzChoice
          v-model="careerGoal"
          :options="[
            { value: '就业优先', label: '就业优先' },
            { value: '升学优先', label: '升学优先' },
            { value: '城市机会优先', label: '城市机会优先' },
          ]"
        />

        <text class="sub">预算偏好</text>
        <GzChoice
          v-model="tuitionBudget"
          :options="[
            { value: '低预算', label: '低预算' },
            { value: '均衡预算', label: '均衡预算' },
            { value: '不限制', label: '不限制' },
          ]"
        />

        <view class="switch">
          <text class="switch__label">接受民办院校</text>
          <switch :checked="acceptPrivate" color="#0E1116" @change="acceptPrivate = switchValue($event)" />
        </view>
        <view class="switch">
          <text class="switch__label">接受中外合作办学</text>
          <switch :checked="acceptSinoForeign" color="#0E1116" @change="acceptSinoForeign = switchValue($event)" />
        </view>
      </template>
    </view>

    <!-- 提交 -->
    <view class="submit">
      <GzButton block :disabled="!canSubmit" :loading="submitting" @tap="onSubmit">
        {{ locked ? '该地区暂未开放生成' : '阅读风险告知并生成' }}
      </GzButton>
      <text class="submit__note">
        点击后会先展示生成前风险告知，阅读到底部并确认后才会提交。生成结果为公益辅助参考，不构成录取承诺。
      </text>
    </view>

    <GzDisclaimerSheet
      :visible="disclaimerVisible"
      @close="disclaimerVisible = false"
      @confirm="onDisclaimerConfirm"
    />

    <FloatingTabBar />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding: calc(#{$gz-space-5} + env(safe-area-inset-top)) $gz-page-x calc(220rpx + env(safe-area-inset-bottom));
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

.block {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;
  margin-bottom: $gz-space-3;

  &__label {
    display: block;
    font-size: $gz-text-body;
    color: var(--gz-text);
    margin-bottom: $gz-space-3;
  }
}

.prov {
  white-space: nowrap;

  &__row {
    display: inline-flex;
  }

  &__item {
    flex-shrink: 0;
    padding: 12rpx 28rpx;
    margin-right: 12rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);

    &--active {
      background: var(--gz-ink);
    }
  }

  &__text {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    white-space: nowrap;
  }

  &__item--active &__text {
    color: var(--gz-text-inverse);
  }
}

.batch {
  margin-top: $gz-space-3;
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: var(--gz-surface-2);

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__text {
    font-size: $gz-text-sm;
    color: var(--gz-text-3);

    &--warn {
      color: var(--gz-warn);
    }
  }

  &__retry {
    font-size: $gz-text-sm;
    color: var(--gz-text);
    margin-left: $gz-space-2;
  }

  &__reason {
    display: block;
    margin-top: 10rpx;
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    line-height: 1.7;
  }
}

.field-gap {
  margin-top: $gz-space-3;
}

.rankhint {
  margin-top: 12rpx;
  padding: 16rpx $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: rgba(37, 99, 235, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;

  &__text {
    flex: 1;
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
    line-height: 1.6;
  }

  &__strong {
    color: var(--gz-wen);
  }

  &__use {
    font-size: $gz-text-xs;
    color: var(--gz-wen);
    margin-left: $gz-space-2;
  }
}

.sub {
  display: block;
  margin: $gz-space-4 0 12rpx;
  font-size: $gz-text-sm;
  color: var(--gz-text-2);
}

.adv {
  margin-top: $gz-space-4;
  padding: 18rpx 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 2rpx solid var(--gz-border);

  &__text {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }

  &__icon {
    font-size: 18rpx;
    color: var(--gz-text-3);
    margin-left: 10rpx;
  }
}

.switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $gz-space-3;

  &__label {
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
  }
}

.submit {
  margin-top: $gz-space-5;

  &__note {
    display: block;
    margin-top: $gz-space-3;
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.8;
  }
}
</style>
