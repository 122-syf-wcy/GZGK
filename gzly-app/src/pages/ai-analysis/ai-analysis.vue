<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { generateAiAnalysis, fetchVolunteerPlan, chatZxfSkill, type ZxfSkillChatMessage } from '@/api/volunteer'
import { getSafetyCode, saveSafetyCode } from '@/utils/storage'
import { mdToBlocks } from '@/utils/markdown'
import type { AiAnalysisResponse, VolunteerItem } from '@/types'

const planId = ref(0)
const safety = ref('')
const data = ref<AiAnalysisResponse | null>(null)
const items = ref<VolunteerItem[]>([])
const loading = ref(false)
const errorMsg = ref('')
const needCode = ref(false)
const inputCode = ref('')

const tab = ref<'overview' | 'report' | 'chat'>('overview')
const open = reactive<Record<string, boolean>>({ keep: true, risk: false, diag: false, act: false, reorder: false })
function toggle(k: string) {
  open[k] = !open[k]
}

function clean(t?: string): string {
  if (!t) return ''
  return t
    .replace(/本系统基于[\s\S]*?为准。/g, '')
    .replace(/本内容由\s*AI\s*生成[^\n]*/g, '')
    .replace(/实际录取结果以[\s\S]*?为准。/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function snip(t?: string, n = 48): string {
  const s = clean(t)
  return s.length > n ? s.slice(0, n) + '…' : s
}

const conclusion = computed(() => clean(data.value?.conclusion))
const keep = computed(() => (data.value?.topKeepDirections || []).map(clean).filter(Boolean))
const risks = computed(() => (data.value?.topRiskPoints || []).map(clean).filter(Boolean))
const reorder = computed(() => (data.value?.reorderAdvice || []).map(clean).filter(Boolean))
const diagnosis = computed(() =>
  (data.value?.diagnosisSections || []).map((s) => ({ title: s.title, content: clean(s.content) })).filter((s) => s.content),
)
const actions = computed(() =>
  (data.value?.actionSteps || []).map((s) => ({ title: s.title, content: clean(s.content) })).filter((s) => s.content),
)
const reportCount = computed(
  () => (keep.value.length ? 1 : 0) + (risks.value.length ? 1 : 0) + (diagnosis.value.length ? 1 : 0) + (actions.value.length ? 1 : 0) + (reorder.value.length ? 1 : 0),
)

const chanceOf = (it: VolunteerItem) => it.chanceScore || 0
const overviewMetrics = computed(() => {
  const it = items.value
  const g = (x: string) => it.filter((i) => i.gradient === x).length
  return [
    { label: '梯度分布', value: `冲${g('冲')}·稳${g('稳')}·保${g('保')}·垫${g('垫')}` },
    { label: '专业级数据', value: `${it.filter((i) => i.dataSourceType === '专业级').length} 个` },
    { label: '高匹配项', value: `${it.filter((i) => (i.matchScore || 0) >= 60).length} 个` },
    { label: '高风险项', value: `${it.filter((i) => i.riskColor === 'red').length} 个` },
  ]
})
const keepTop = computed(() =>
  [...items.value].sort((a, b) => (b.matchScore || 0) + chanceOf(b) - ((a.matchScore || 0) + chanceOf(a)))[0],
)
const riskTop = computed(() =>
  [...items.value].sort((a, b) => {
    const r = (x: VolunteerItem) => (x.riskColor === 'red' ? 100 : x.riskColor === 'yellow' ? 60 : 20) + (40 - chanceOf(x))
    return r(b) - r(a)
  })[0],
)

async function load(code: string, force = false) {
  loading.value = true
  errorMsg.value = ''
  try {
    const [ai, plan] = await Promise.all([
      generateAiAnalysis(planId.value, code, force),
      fetchVolunteerPlan(planId.value, code).catch(() => null),
    ])
    data.value = ai
    if (plan) items.value = plan.items || []
    safety.value = code
    saveSafetyCode(planId.value, code)
    needCode.value = false
  } catch (e) {
    errorMsg.value = (e as Error).message || 'AI 解读失败'
    if (!data.value) needCode.value = true
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
function refresh() {
  if (safety.value) load(safety.value, true)
}

/* Skills 追问 */
const skillInput = ref('')
const skillMessages = ref<ZxfSkillChatMessage[]>([])
const skillLoading = ref(false)
const suggestions = [
  '按就业优先，帮我筛掉最不值得保留的冲档项',
  '这些志愿里哪些更值得保专业，哪些更值得保学校',
  '结合扩招和招生指数，重排前 15 个志愿',
]
function fillSuggestion(t: string) {
  skillInput.value = t
}
async function sendSkill() {
  const m = skillInput.value.trim()
  if (!m || skillLoading.value) return
  if (!safety.value) {
    uni.showToast({ title: '缺少安全码', icon: 'none' })
    return
  }
  skillInput.value = ''
  skillMessages.value.push({ role: 'user', content: m })
  skillLoading.value = true
  try {
    const res = await chatZxfSkill({
      planId: planId.value,
      safetyCode: safety.value,
      message: m,
      aiReport: conclusion.value,
      messages: skillMessages.value.slice(-8),
    })
    skillMessages.value.push({ role: 'assistant', content: res.answer || res.reply || '暂时没有有效回复，请稍后再试。' })
  } catch (e) {
    skillMessages.value.push({ role: 'assistant', content: '追问失败：' + (e as Error).message })
  } finally {
    skillLoading.value = false
  }
}

function goPoster() {
  uni.navigateTo({ url: `/pages/poster/poster?id=${planId.value}` })
}

onLoad((options) => {
  planId.value = Number(options?.id || 0)
  const code = getSafetyCode(planId.value)
  if (code) load(code)
  else needCode.value = true
})
</script>

<template>
  <view class="page" :class="{ 'pb-chat': tab === 'chat' && data && !loading }">
    <view v-if="needCode && !data" class="card center-card">
      <view class="sec"><text class="bar" />输入安全码生成 AI 解读</view>
      <input v-model="inputCode" class="ri" placeholder="生成方案时设置的安全码" />
      <view class="btn" hover-class="btn-hover" @click="submitCode">生成解读</view>
      <view v-if="errorMsg" class="err">{{ errorMsg }}</view>
    </view>

    <view v-if="loading" class="loading">
      <view class="spinner" />
      <text class="loading-t">AI 正在生成志愿方案复盘报告…</text>
      <text class="loading-s">需要数十秒，请稍候。</text>
    </view>

    <template v-if="data && !loading">
      <!-- 顶部分段切换 -->
      <view class="seg">
        <view class="seg-i" :class="{ on: tab === 'overview' }" @click="tab = 'overview'">概览</view>
        <view class="seg-i" :class="{ on: tab === 'report' }" @click="tab = 'report'">
          复盘报告<text v-if="reportCount" class="seg-badge">{{ reportCount }}</text>
        </view>
        <view class="seg-i" :class="{ on: tab === 'chat' }" @click="tab = 'chat'">AI 追问</view>
      </view>

      <!-- ============ 概览 ============ -->
      <view v-show="tab === 'overview'">
        <view class="hero">
          <text class="hero-eyebrow">志愿方案复盘报告</text>
          <text class="hero-concl">{{ conclusion || '已生成方案复盘，下面是关键指标与重点提示。' }}</text>
        </view>

        <view v-if="data.aiFallbackUsed" class="fallback">当前为规则模板兜底解读（AI 暂不可用）。</view>

        <view v-if="items.length" class="metrics">
          <view v-for="m in overviewMetrics" :key="m.label" class="metric">
            <text class="metric-v">{{ m.value }}</text>
            <text class="metric-l">{{ m.label }}</text>
          </view>
        </view>

        <view v-if="keepTop" class="hl keep">
          <view class="hl-head"><text class="hl-tag keep">值得保留</text><text class="hl-u">{{ keepTop.universityName }}</text></view>
          <text class="hl-m">{{ keepTop.majorName }}</text>
          <text class="hl-note">{{ snip(keepTop.recommendReason) || '专业级数据与当前偏好相对更贴近。' }}</text>
        </view>
        <view v-if="riskTop" class="hl risk">
          <view class="hl-head"><text class="hl-tag risk">最大风险</text><text class="hl-u">{{ riskTop.universityName }}</text></view>
          <text class="hl-m">{{ riskTop.majorName }}</text>
          <text class="hl-note">{{ snip(riskTop.riskReason) || '建议优先核对章程、位次波动和专业限制。' }}</text>
        </view>

        <view class="ov-actions">
          <view class="ba-btn" hover-class="btn-hover" @click="tab = 'report'">查看完整报告</view>
          <view class="ba-btn ghost" hover-class="btn-hover" @click="goPoster">导出长图</view>
        </view>
        <view class="ov-actions">
          <view class="ba-btn ghost" hover-class="btn-hover" @click="refresh">重新分析</view>
          <view class="ba-btn ghost" hover-class="btn-hover" @click="tab = 'chat'">向 AI 追问</view>
        </view>
      </view>

      <!-- ============ 复盘报告（折叠） ============ -->
      <view v-show="tab === 'report'">
        <view v-if="keep.length" class="acc">
          <view class="acc-h" @click="toggle('keep')">
            <text class="bar keepbar" /><text class="acc-t">建议保留方向</text>
            <text class="acc-x">{{ open.keep ? '收起' : '展开' }}</text>
          </view>
          <view v-show="open.keep" class="acc-b">
            <view v-for="(d, i) in keep" :key="i" class="li"><text class="dot keep-dot">·</text><text class="li-t">{{ d }}</text></view>
          </view>
        </view>

        <view v-if="risks.length" class="acc">
          <view class="acc-h" @click="toggle('risk')">
            <text class="bar riskbar" /><text class="acc-t">主要风险点</text>
            <text class="acc-x">{{ open.risk ? '收起' : '展开' }}</text>
          </view>
          <view v-show="open.risk" class="acc-b">
            <view v-for="(d, i) in risks" :key="i" class="li"><text class="dot risk-dot">·</text><text class="li-t">{{ d }}</text></view>
          </view>
        </view>

        <view v-if="diagnosis.length" class="acc">
          <view class="acc-h" @click="toggle('diag')">
            <text class="bar" /><text class="acc-t">分项诊断</text>
            <text class="acc-x">{{ open.diag ? '收起' : '展开' }}</text>
          </view>
          <view v-show="open.diag" class="acc-b">
            <view v-for="(s, i) in diagnosis" :key="i" class="block">
              <text class="b-title">{{ s.title }}</text>
              <text class="b-content">{{ s.content }}</text>
            </view>
          </view>
        </view>

        <view v-if="actions.length" class="acc">
          <view class="acc-h" @click="toggle('act')">
            <text class="bar" /><text class="acc-t">行动建议</text>
            <text class="acc-x">{{ open.act ? '收起' : '展开' }}</text>
          </view>
          <view v-show="open.act" class="acc-b">
            <view v-for="(s, i) in actions" :key="i" class="block">
              <text class="b-title">{{ i + 1 }}. {{ s.title }}</text>
              <text class="b-content">{{ s.content }}</text>
            </view>
          </view>
        </view>

        <view v-if="reorder.length" class="acc">
          <view class="acc-h" @click="toggle('reorder')">
            <text class="bar" /><text class="acc-t">志愿排序建议</text>
            <text class="acc-x">{{ open.reorder ? '收起' : '展开' }}</text>
          </view>
          <view v-show="open.reorder" class="acc-b">
            <view v-for="(d, i) in reorder" :key="i" class="li"><text class="dot">·</text><text class="li-t">{{ d }}</text></view>
          </view>
        </view>

        <view class="notice">本解读由 AI 生成，仅供参考，不构成录取预测；请结合各省招生考试院与高校官方材料自主决策。</view>
      </view>

      <!-- ============ AI 追问 ============ -->
      <view v-show="tab === 'chat'" class="chat">
        <view v-if="!skillMessages.length" class="chat-empty">
          <text class="chat-empty-t">向 AI 追问填报思路</text>
          <text class="chat-empty-s">基于你的方案与复盘报告继续提问（仅供参考）。试试：</text>
          <view class="sugs">
            <text v-for="(s, i) in suggestions" :key="i" class="sug" @click="fillSuggestion(s)">{{ s }}</text>
          </view>
        </view>

        <view v-else class="chat-msgs">
          <view v-for="(m, i) in skillMessages" :key="i" class="row" :class="m.role">
            <view class="bubble" :class="m.role">
              <template v-if="m.role === 'assistant'">
                <view v-for="(blk, j) in mdToBlocks(m.content)" :key="j" class="mb" :class="'mb-' + blk.type">
                  <text v-if="blk.type === 'li'" class="mb-dot">·</text>
                  <text class="mb-t">{{ blk.text }}</text>
                </view>
              </template>
              <text v-else class="u-line">{{ m.content }}</text>
            </view>
          </view>
          <view v-if="skillLoading" class="thinking">AI 正在思考…</view>
        </view>
      </view>
    </template>

    <!-- 固定底部输入（仅追问页） -->
    <view v-if="tab === 'chat' && data && !loading" class="dock">
      <input v-model="skillInput" class="dock-input" placeholder="输入追问，回车发送" confirm-type="send" @confirm="sendSkill" />
      <view class="dock-send" :class="{ disabled: skillLoading || !skillInput.trim() }" hover-class="btn-hover" @click="sendSkill">发送</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: 20rpx 24rpx 60rpx;
}
.page.pb-chat {
  padding-bottom: 150rpx;
}

/* 分段切换 */
.seg {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  gap: 8rpx;
  padding: 10rpx;
  margin: -4rpx 0 18rpx;
  background: rgba(245, 243, 238, 0.94);
  border: 1rpx solid $gz-border;
  border-radius: 999rpx;
  backdrop-filter: saturate(120%) blur(8rpx);
}
.seg-i {
  flex: 1;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: $gz-text-sub;
  border-radius: 999rpx;
}
.seg-i.on {
  background: $gz-primary;
  color: #fff;
  box-shadow: 0 6rpx 16rpx rgba(29, 78, 216, 0.28);
}
.seg-badge {
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.28);
  font-size: 20rpx;
  line-height: 30rpx;
  text-align: center;
}
.seg-i:not(.on) .seg-badge {
  background: $gz-primary-50;
  color: $gz-primary;
}

/* 概览 hero */
.hero {
  background: linear-gradient(135deg, #1f2933, #334155);
  border-radius: $gz-radius;
  padding: 32rpx;
  margin-bottom: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.hero-eyebrow {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.66);
  letter-spacing: 1rpx;
}
.hero-concl {
  font-size: 30rpx;
  font-weight: 700;
  color: #fff;
  line-height: 1.7;
}

/* 指标 */
.metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-bottom: 18rpx;
}
.metric {
  width: calc(50% - 7rpx);
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: 18rpx;
  padding: 20rpx 22rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
}
.metric-v {
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
}
.metric-l {
  font-size: 22rpx;
  color: $gz-text-weak;
}

/* 高亮卡 */
.hl {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 24rpx 26rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.hl.keep {
  border-left: 8rpx solid $gz-bao;
}
.hl.risk {
  border-left: 8rpx solid $gz-chong;
}
.hl-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.hl-tag {
  flex: none;
  font-size: 20rpx;
  font-weight: 700;
  color: #fff;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
}
.hl-tag.keep {
  background: $gz-bao;
}
.hl-tag.risk {
  background: $gz-chong;
}
.hl-u {
  font-size: 27rpx;
  font-weight: 800;
  color: $gz-text;
}
.hl-m {
  font-size: 24rpx;
  color: $gz-text-sub;
}
.hl-note {
  font-size: 22rpx;
  color: $gz-text-weak;
  line-height: 1.6;
}

/* 概览操作按钮 */
.ov-actions {
  display: flex;
  gap: 14rpx;
  margin-bottom: 14rpx;
}
.ba-btn {
  flex: 1;
  height: 84rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ba-btn.ghost {
  background: $gz-primary-50;
  color: $gz-primary;
}
.btn-hover {
  opacity: 0.85;
}

/* 折叠分区 */
.acc {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  margin-bottom: 16rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
  overflow: hidden;
}
.acc-h {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 26rpx 26rpx;
}
.acc-t {
  flex: 1;
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
}
.acc-x {
  flex: none;
  font-size: 22rpx;
  color: $gz-primary;
  background: $gz-primary-50;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}
.acc-b {
  padding: 0 26rpx 24rpx;
}
.bar {
  width: 8rpx;
  height: 30rpx;
  border-radius: 6rpx;
  background: $gz-primary;
}
.bar.keepbar {
  background: $gz-bao;
}
.bar.riskbar {
  background: $gz-chong;
}
.li {
  display: flex;
  gap: 12rpx;
  margin-bottom: 14rpx;
}
.li:last-child {
  margin-bottom: 0;
}
.dot {
  flex: none;
  font-size: 26rpx;
  color: $gz-primary;
  font-weight: 800;
}
.keep-dot {
  color: $gz-bao;
}
.risk-dot {
  color: $gz-chong;
}
.li-t {
  flex: 1;
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.8;
}
.block {
  margin-bottom: 18rpx;
}
.block:last-child {
  margin-bottom: 0;
}
.b-title {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: $gz-text;
  margin-bottom: 8rpx;
}
.b-content {
  display: block;
  font-size: 25rpx;
  color: $gz-text-sub;
  line-height: 1.85;
}

/* 追问 */
.chat {
  min-height: 50vh;
}
.chat-empty {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 32rpx 28rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
}
.chat-empty-t {
  font-size: 30rpx;
  font-weight: 800;
  color: $gz-text;
}
.chat-empty-s {
  font-size: 23rpx;
  color: $gz-text-weak;
  line-height: 1.6;
}
.sugs {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 6rpx;
}
.sug {
  font-size: 24rpx;
  color: $gz-primary;
  background: $gz-primary-50;
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  line-height: 1.5;
}
.chat-msgs {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}
.row {
  display: flex;
}
.row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 86%;
  border-radius: 20rpx;
  padding: 20rpx 22rpx;
  display: flex;
  flex-direction: column;
}
.bubble.assistant {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  max-width: 92%;
  box-shadow: 0 6rpx 18rpx rgba(15, 23, 42, 0.04);
}
.bubble.user {
  background: $gz-primary;
}
.u-line {
  font-size: 25rpx;
  line-height: 1.7;
  color: #fff;
}
.mb {
  margin-bottom: 8rpx;
}
.mb:last-child {
  margin-bottom: 0;
}
.mb-h .mb-t {
  font-size: 25rpx;
  font-weight: 800;
  color: $gz-text;
}
.mb-h {
  margin-top: 6rpx;
}
.mb-p .mb-t {
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.8;
}
.mb-li {
  display: flex;
  gap: 8rpx;
}
.mb-li .mb-dot {
  flex: none;
  color: $gz-primary;
  font-weight: 800;
  font-size: 24rpx;
}
.mb-li .mb-t {
  flex: 1;
  font-size: 24rpx;
  color: $gz-text-sub;
  line-height: 1.75;
}
.thinking {
  font-size: 22rpx;
  color: $gz-text-weak;
  padding: 4rpx 6rpx;
}

/* 固定底部输入 */
.dock {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  display: flex;
  gap: 14rpx;
  padding: 16rpx 24rpx calc(16rpx + constant(safe-area-inset-bottom)) 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 253, 250, 0.96);
  border-top: 1rpx solid $gz-border;
  backdrop-filter: saturate(120%) blur(8rpx);
}
.dock-input {
  flex: 1;
  height: 80rpx;
  background: $gz-bg-subtle;
  border: 1rpx solid $gz-border;
  border-radius: 40rpx;
  padding: 0 28rpx;
  font-size: 25rpx;
}
.dock-send {
  flex: none;
  height: 80rpx;
  padding: 0 40rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
}
.dock-send.disabled {
  opacity: 0.5;
}

/* 通用 / 状态 */
.center-card {
  background: $gz-card;
  border: 1rpx solid $gz-border;
  border-radius: $gz-radius;
  padding: 28rpx;
  margin-top: 40rpx;
}
.sec {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 28rpx;
  font-weight: 800;
  color: $gz-text;
  margin-bottom: 16rpx;
}
.fallback {
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 14rpx;
  padding: 16rpx 22rpx;
  font-size: 23rpx;
  color: $gz-warn;
  margin-bottom: 18rpx;
}
.ri {
  height: 80rpx;
  border: 1rpx solid $gz-border;
  border-radius: 14rpx;
  padding: 0 22rpx;
  font-size: 26rpx;
  margin-bottom: 16rpx;
  background: $gz-bg-subtle;
}
.btn {
  height: 84rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading {
  margin-top: 90rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}
.spinner {
  width: 56rpx;
  height: 56rpx;
  border: 6rpx solid $gz-primary-50;
  border-top-color: $gz-primary;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.loading-t {
  font-size: 27rpx;
  font-weight: 700;
  color: $gz-text;
}
.loading-s {
  font-size: 23rpx;
  color: $gz-text-weak;
}
.err {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: #d4380d;
}
.notice {
  margin-top: 16rpx;
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  border: 1rpx solid $gz-warn-border;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  line-height: 1.7;
}
</style>
