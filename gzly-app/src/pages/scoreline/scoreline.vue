<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProvinceScoreLineCapability, queryProvinceScoreLines } from '@/api/scoreLine'
import { PROVINCES, normalizeProvinceCode, type ProvinceCode } from '@/constants/provinces'
import { getPrefProvince } from '@/utils/storage'
import { SHORT_DISCLAIMER } from '@/constants/disclaimer'
import { lookupControlLines } from '@/constants/controlLines'
import type { ProvinceScoreLineCapability, ProvinceScoreLineQueryResult, ScoreLineRecord } from '@/types'

const provinceNames = PROVINCES.map((p) => p.name)
const provinceIndex = ref(0)
const cap = ref<ProvinceScoreLineCapability | null>(null)
const typeIndex = ref(0)
const yearIndex = ref(0)
const subjectIndex = ref(0)
const result = ref<ProvinceScoreLineQueryResult | null>(null)
const rankAll = ref<ScoreLineRecord[]>([])
const controlLines = ref<{ label: string; score: number }[]>([])
const controlMissing = ref(false)
const controlSource = ref('')
const capLoading = ref(false)
const queryLoading = ref(false)
const errorMsg = ref('')

const currentProvince = computed<ProvinceCode>(() => PROVINCES[provinceIndex.value].code)
const typeLabels = computed(() => (cap.value?.scoreLineTypes || []).map((t) => t.label))
const yearLabels = computed(() => (cap.value?.availableYears || []).map((y) => String(y)))
const subjectLabels = computed(() => {
  const c = cap.value
  if (!c) return [] as string[]
  if (c.subjectOptions && c.subjectOptions.length) return c.subjectOptions
  return c.selectedSubjectOptions || []
})

async function loadCapability() {
  capLoading.value = true
  errorMsg.value = ''
  result.value = null
  cap.value = null
  try {
    const c = await getProvinceScoreLineCapability(currentProvince.value)
    cap.value = c
    const types = c.scoreLineTypes || []
    // 默认选一个大概率有数据的类型，避免一进页面就是「省控线 MISSING」空态
    let di = types.findIndex((t) => t.type === 'score_rank')
    if (di < 0) di = types.findIndex((t) => t.type === 'admission_line')
    if (di < 0) di = 0
    typeIndex.value = di
    yearIndex.value = 0
    subjectIndex.value = 0
    capLoading.value = false
    await doQuery()
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载省份能力失败'
  } finally {
    capLoading.value = false
  }
}

async function doQuery() {
  const c = cap.value
  if (!c) return
  const typeMeta = c.scoreLineTypes[typeIndex.value]
  if (!typeMeta) return
  queryLoading.value = true
  errorMsg.value = ''
  result.value = null
  rankAll.value = []
  selectedBin.value = -1
  controlLines.value = []
  controlMissing.value = false
  controlSource.value = ''
  const isRank = typeMeta.type === 'score_rank'
  const baseParams: Record<string, unknown> = {}
  if (yearLabels.value.length) baseParams.year = c.availableYears[yearIndex.value]
  const subject = subjectLabels.value[subjectIndex.value]
  if (subject) {
    if (c.subjectOptions && c.subjectOptions.length) baseParams.subjectType = subject
    else baseParams.selectedSubjects = subject
  }
  try {
    result.value = await queryProvinceScoreLines(currentProvince.value, typeMeta.type, {
      ...baseParams,
      page: 1,
      pageSize: isRank ? 100 : 30,
    })
    if (isRank && result.value?.dataStatus === 'AVAILABLE') {
      rankAll.value = result.value.pageResult?.items ? [...result.value.pageResult.items] : []
      // 后端 pageSize 上限 100，一分一段需逐页取全（约 7 页）才能画完整柱状图
      loadRankAll(typeMeta.type, baseParams)
      // 叠加本科线 / 特控线（省控线有数据才标，无数据如实留白，不伪造）
      loadControlLines(baseParams)
    }
  } catch (e) {
    errorMsg.value = (e as Error).message || '查询失败'
  } finally {
    queryLoading.value = false
  }
}

async function loadRankAll(type: string, baseParams: Record<string, unknown>) {
  const total = result.value?.pageResult?.total ?? rankAll.value.length
  let page = (result.value?.pageResult?.page || 1) + 1
  const all = [...rankAll.value]
  try {
    while (all.length < total && page <= 60) {
      const r = await queryProvinceScoreLines(currentProvince.value, type, { ...baseParams, page, pageSize: 100 })
      const its = r.pageResult?.items || []
      if (!its.length) break
      all.push(...its)
      page++
    }
  } catch {
    /* 局部失败：用已取到的数据画图 */
  }
  rankAll.value = all
}

/** 查询省控线，提取「本科线 / 特控线」叠加到分布图；后端无数据时回退到官方公布值（带来源） */
async function loadControlLines(baseParams: Record<string, unknown>) {
  try {
    const r = await queryProvinceScoreLines(currentProvince.value, 'control_line', { ...baseParams, page: 1, pageSize: 50 })
    if (r.dataStatus === 'AVAILABLE') {
      const its = r.pageResult?.items || []
      const found: { label: string; score: number }[] = []
      const seen = new Set<string>()
      for (const it of its) {
        const name = it.batchName || ''
        const sc = it.minScore ?? it.score
        if (sc == null) continue
        let label = ''
        if (/特殊类型|特控|特招/.test(name)) label = '特控线'
        else if (/本科/.test(name)) label = '本科线'
        if (label && !seen.has(label)) {
          seen.add(label)
          found.push({ label, score: sc })
        }
      }
      if (found.length) {
        controlLines.value = found
        controlSource.value = (cap.value?.officialSourceName || '省招生考试院') + ' · 官方'
        controlMissing.value = false
        return
      }
    }
    applyOfficialFallback(baseParams)
  } catch {
    applyOfficialFallback(baseParams)
  }
}

/** 后端省控线缺失时，用本地「官方公布值」常量兜底（带来源标注） */
function applyOfficialFallback(baseParams: Record<string, unknown>) {
  const year = Number(baseParams.year) || undefined
  const subject = (baseParams.subjectType || baseParams.selectedSubjects) as string | undefined
  const off = lookupControlLines(currentProvince.value, year, subject)
  if (off) {
    controlLines.value = off.lines
    controlSource.value = off.source + ' · ' + off.publishDate
    controlMissing.value = false
  } else {
    controlLines.value = []
    controlSource.value = ''
    controlMissing.value = true
  }
}

function onProvince(e: { detail: { value: number | string } }) {
  provinceIndex.value = Number(e.detail.value)
  loadCapability()
}
function onType(e: { detail: { value: number | string } }) {
  typeIndex.value = Number(e.detail.value)
}
function onYear(e: { detail: { value: number | string } }) {
  yearIndex.value = Number(e.detail.value)
}
function onSubject(e: { detail: { value: number | string } }) {
  subjectIndex.value = Number(e.detail.value)
}

const items = computed<ScoreLineRecord[]>(() => result.value?.pageResult?.items || [])
const available = computed(() => result.value?.dataStatus === 'AVAILABLE')

/** 是否一分一段行（有分数、无院校名） */
function isRankRow(it: ScoreLineRecord): boolean {
  return it.score !== undefined && it.schoolName === undefined
}
/** 当前结果是否「一分一段」（用于显示趋势图、列表截断） */
const isRankResult = computed(() => items.value.length > 0 && items.value.every(isRankRow))
/* ---- 一分一段：分段柱状图（点击查看分数段人数与位次） ---- */
interface ScoreBin {
  lo: number
  hi: number
  count: number
  cumTop: number
  cumBot: number
  has: boolean
}
const BIN_W = 30
const bins = computed<ScoreBin[]>(() => {
  const data = rankAll.value.filter(isRankRow)
  if (!data.length) return []
  const byScore = new Map<number, { same: number; cum: number }>()
  let maxScore = 0
  for (const d of data) {
    const s = d.score || 0
    byScore.set(s, { same: d.sameScoreCount || 0, cum: d.cumulativeCount || 0 })
    if (s > maxScore) maxScore = s
  }
  const n = Math.floor(maxScore / BIN_W) + 1
  const out: ScoreBin[] = []
  for (let i = 0; i < n; i++) {
    const lo = i * BIN_W
    const hi = Math.min(maxScore, lo + BIN_W - 1)
    let count = 0
    let cumTop = Infinity
    let cumBot = 0
    let has = false
    for (let s = lo; s <= hi; s++) {
      const e = byScore.get(s)
      if (!e) continue
      has = true
      count += e.same
      if (e.cum < cumTop) cumTop = e.cum
      if (e.cum > cumBot) cumBot = e.cum
    }
    out.push({ lo, hi, count, cumTop: has ? cumTop : 0, cumBot: has ? cumBot : 0, has })
  }
  return out
})
const maxBinCount = computed(() => Math.max(1, ...bins.value.map((b) => b.count)))
const maxScoreLabel = computed(() => {
  const rs = rankAll.value.filter(isRankRow).map((d) => d.score || 0)
  return rs.length ? Math.max(...rs) : 0
})
const totalPeople = computed(() => {
  const rs = rankAll.value.filter(isRankRow).map((d) => d.cumulativeCount || 0)
  return rs.length ? Math.max(...rs) : 0
})
const selectedBin = ref(-1)
const selBin = computed(() => (selectedBin.value >= 0 ? bins.value[selectedBin.value] || null : null))
function pct(n: number): string {
  const t = totalPeople.value
  if (!t) return '—'
  return ((n / t) * 100).toFixed(1) + '%'
}
function barH(count: number): string {
  return Math.max(2, Math.round((count / maxBinCount.value) * 100)) + '%'
}
function refLeft(score: number): string {
  const m = maxScoreLabel.value || 1
  return Math.min(100, Math.max(0, (score / m) * 100)) + '%'
}

onLoad(() => {
  const pref = normalizeProvinceCode(getPrefProvince())
  const i = PROVINCES.findIndex((p) => p.code === pref)
  if (i >= 0) provinceIndex.value = i
  loadCapability()
})
</script>

<template>
  <view class="page">
    <view class="card panel">
    <view class="filters">
      <picker mode="selector" :range="provinceNames" :value="provinceIndex" @change="onProvince">
        <view class="sel"><text class="sel-k">省份</text><text class="sel-v">{{ provinceNames[provinceIndex] }}</text></view>
      </picker>
      <picker v-if="typeLabels.length" mode="selector" :range="typeLabels" :value="typeIndex" @change="onType">
        <view class="sel"><text class="sel-k">类型</text><text class="sel-v">{{ typeLabels[typeIndex] }}</text></view>
      </picker>
      <picker v-if="yearLabels.length" mode="selector" :range="yearLabels" :value="yearIndex" @change="onYear">
        <view class="sel"><text class="sel-k">年份</text><text class="sel-v">{{ yearLabels[yearIndex] }}</text></view>
      </picker>
      <picker v-if="subjectLabels.length" mode="selector" :range="subjectLabels" :value="subjectIndex" @change="onSubject">
        <view class="sel"><text class="sel-k">科类</text><text class="sel-v">{{ subjectLabels[subjectIndex] }}</text></view>
      </picker>
    </view>

    <view class="actions">
      <view class="btn" hover-class="btn-hover" @click="doQuery">查询</view>
      <text v-if="cap" class="src">来源：{{ cap.officialSourceName }} · {{ cap.dataStatus }}</text>
    </view>
    </view>

    <view v-if="capLoading" class="hint">加载省份能力…</view>
    <view v-else-if="errorMsg" class="hint error">{{ errorMsg }}</view>

    <view v-if="queryLoading" class="hint">查询中…</view>

    <template v-else-if="result">
      <view v-if="!available" class="missing">
        <view class="missing-title">暂无可核验数据（{{ result.dataStatus }}）</view>
        <view class="missing-reason">{{ result.missingReason || '该类型暂无结构化数据，等官方发布后开放。' }}</view>
      </view>
      <template v-else>
        <!-- 一分一段：分段柱状图（点击查看该分数段人数与位次） -->
        <view v-if="isRankResult" class="card chart-card">
          <view class="chart-h">
            <text class="bar" />分数段人数分布
            <text class="chart-cnt">每 {{ BIN_W }} 分一段</text>
          </view>
          <view class="bars">
            <view v-for="(b, i) in bins" :key="i" class="bar-col" @click="selectedBin = i">
              <view class="bar-fill" :class="{ on: selectedBin === i }" :style="{ height: barH(b.count) }" />
            </view>
            <view
              v-for="ln in controlLines"
              :key="ln.label"
              class="ref-line"
              :class="ln.label === '特控线' ? 'ref-amber' : 'ref-green'"
              :style="{ left: refLeft(ln.score) }"
            >
              <text class="ref-tag">{{ ln.label }}{{ ln.score }}</text>
            </view>
          </view>
          <view class="bars-axis"><text>0 分</text><text>{{ maxScoreLabel }} 分</text></view>
          <view v-if="controlLines.length" class="ref-legend">
            <text v-for="ln in controlLines" :key="ln.label" class="rl" :class="ln.label === '特控线' ? 'rl-amber' : 'rl-green'">| {{ ln.label }} {{ ln.score }} 分</text>
          </view>
          <text v-if="controlLines.length && controlSource" class="ctrl-src">控制线来源：{{ controlSource }}（官方公布）</text>
          <text v-else-if="controlMissing" class="ctrl-note">本科线 / 特控线：该省该科类省控线暂未收录（不伪造），官方发布后补。</text>

          <view v-if="selBin" class="bin-detail">
            <view class="bd-head">
              <text class="bd-band">{{ selBin.lo }}–{{ selBin.hi }} 分</text>
              <text class="bd-close" @click="selectedBin = -1">×</text>
            </view>
            <view class="bd-row"><text class="bd-k">同分段人数</text><text class="bd-v">{{ selBin.count }} 人 · 占 {{ pct(selBin.count) }}</text></view>
            <view class="bd-row"><text class="bd-k">位次区间</text><text class="bd-v">约 {{ selBin.cumTop }}–{{ selBin.cumBot }} 名</text></view>
          </view>
          <text v-else class="chart-tip">点击柱子查看该分数段的人数与位次区间（横轴 0–{{ maxScoreLabel }} 分，全 {{ result.pageResult?.total || items.length }} 段）</text>
        </view>

        <!-- 投档线 / 专业线 / 省控线：列表 -->
        <template v-else>
          <view class="count">共 <text class="count-n">{{ result.pageResult?.total || items.length }}</text> 条</view>
          <view class="list">
            <view v-for="(it, i) in items" :key="i" class="sc-row">
              <view class="sc-main">
                <text class="sc-name">{{ it.schoolName || it.batchName || '记录' }}</text>
                <text v-if="it.majorName" class="sc-major">{{ it.majorName }}</text>
                <view v-if="(it.batchName && it.schoolName) || it.requiredSubjects" class="sc-tags">
                  <text v-if="it.batchName && it.schoolName" class="sc-tag">{{ it.batchName }}</text>
                  <text v-if="it.requiredSubjects" class="sc-tag">选科 {{ it.requiredSubjects }}</text>
                </view>
              </view>
              <view class="sc-score">
                <text class="sc-min">{{ it.minScore ?? it.score ?? '—' }}</text>
                <text class="sc-min-u">分</text>
                <text v-if="it.minRank !== undefined" class="sc-rank">位次 {{ it.minRank }}</text>
              </view>
            </view>
          </view>
        </template>
      </template>
    </template>

    <view class="notice">{{ SHORT_DISCLAIMER }}</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.panel {
  padding: 26rpx 24rpx;
  margin-bottom: 20rpx;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}
.sel {
  display: flex;
  align-items: center;
  gap: 10rpx;
  background: $gz-bg-subtle;
  border: 1rpx solid $gz-border;
  border-radius: 14rpx;
  padding: 14rpx 20rpx;
}
.sel-k {
  font-size: 22rpx;
  color: $gz-text-weak;
}
.sel-v {
  font-size: 26rpx;
  color: $gz-text;
  font-weight: 600;
}
.actions {
  margin-top: 18rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.btn {
  height: 70rpx;
  padding: 0 44rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-hover {
  opacity: 0.85;
}
.src {
  font-size: 22rpx;
  color: $gz-text-weak;
  flex: 1;
}
.count {
  margin: 20rpx 4rpx 0;
  font-size: 24rpx;
  color: $gz-text-sub;
}
.count-n {
  font-size: 26rpx;
  font-weight: 800;
  color: $gz-primary;
}
.list {
  margin-top: 14rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 22rpx;
}

/* 趋势图 */
.chart-card {
  padding: 20rpx;
  margin-top: 16rpx;
  margin-bottom: 4rpx;
}
.chart-h {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 26rpx;
  font-weight: 800;
  color: $gz-text;
  margin-bottom: 12rpx;
}
.chart-cnt {
  margin-left: auto;
  font-size: 20rpx;
  font-weight: 500;
  color: $gz-text-weak;
}
.bar {
  width: 8rpx;
  height: 28rpx;
  border-radius: 6rpx;
  background: $gz-primary;
}
.bars {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 3rpx;
  height: 300rpx;
  padding: 6rpx 0;
}
.ref-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 2rpx dashed #d97706;
  pointer-events: none;
}
.ref-line.ref-green {
  border-left-color: #059669;
}
.ref-line.ref-amber {
  border-left-color: #d97706;
}
.ref-tag {
  position: absolute;
  top: 0;
  left: 4rpx;
  font-size: 18rpx;
  color: #fff;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  white-space: nowrap;
}
.ref-line.ref-green .ref-tag {
  background: #059669;
}
.ref-line.ref-amber .ref-tag {
  background: #d97706;
}
.ref-legend {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;
}
.rl {
  font-size: 20rpx;
  font-weight: 700;
}
.rl-green {
  color: #059669;
}
.rl-amber {
  color: #d97706;
}
.ctrl-note {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $gz-warn;
  line-height: 1.5;
}
.ctrl-src {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: $gz-text-weak;
  line-height: 1.5;
}
.bar-col {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
}
.bar-fill {
  width: 100%;
  min-height: 3rpx;
  background: $gz-primary-light;
  border-radius: 5rpx 5rpx 0 0;
}
.bar-fill.on {
  background: $gz-primary;
}
.bars-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: $gz-text-weak;
}
.bin-detail {
  margin-top: 16rpx;
  background: $gz-primary-50;
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 18rpx 20rpx;
}
.bd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.bd-band {
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-primary;
}
.bd-close {
  font-size: 34rpx;
  color: $gz-text-weak;
  line-height: 1;
  padding: 0 6rpx;
}
.bd-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 8rpx;
}
.bd-k {
  font-size: 23rpx;
  color: $gz-text-weak;
}
.bd-v {
  font-size: 24rpx;
  color: $gz-text;
  font-weight: 700;
}
.chart-tip {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $gz-text-weak;
  line-height: 1.5;
}

/* 投档线 / 专业线 行 */
.sc-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 16rpx;
  padding: 20rpx 22rpx;
}
.sc-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.sc-name {
  font-size: 27rpx;
  font-weight: 700;
  color: $gz-text;
}
.sc-major {
  font-size: 23rpx;
  color: $gz-text-sub;
}
.sc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 2rpx;
}
.sc-tag {
  font-size: 20rpx;
  color: $gz-primary;
  background: $gz-primary-50;
  border-radius: 8rpx;
  padding: 3rpx 12rpx;
}
.sc-score {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.sc-min {
  font-size: 36rpx;
  font-weight: 800;
  color: $gz-primary;
  line-height: 1;
}
.sc-min-u {
  font-size: 19rpx;
  color: $gz-text-weak;
  margin-top: 4rpx;
}
.sc-rank {
  font-size: 21rpx;
  color: $gz-text-weak;
  margin-top: 6rpx;
}
.missing {
  margin-top: 20rpx;
  background: $gz-warn-bg;
  border: 1rpx solid #ffe2b0;
  border-radius: 16rpx;
  padding: 24rpx;
}
.missing-title {
  font-size: 26rpx;
  font-weight: 700;
  color: $gz-warn;
}
.missing-reason {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: $gz-warn;
  line-height: 1.7;
}
.hint {
  margin-top: 30rpx;
  text-align: center;
  font-size: 24rpx;
  color: $gz-text-weak;
}
.hint.error {
  color: #d4380d;
}
.notice {
  margin-top: 30rpx;
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid #ffe2b0;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
