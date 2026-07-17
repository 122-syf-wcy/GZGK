<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  fetchAiQaRegions,
  createAiQaSession,
  restoreAiQaSession,
  sendAiQaMessage,
  listAiQaMessages,
  type AiQaRegion,
  type AiQaMessage,
} from '@/api/aiQa'
import { setAiQaActive, getAiQaActive, clearAiQaActive } from '@/utils/storage'
import { mdToBlocks } from '@/utils/markdown'
import { SHORT_DISCLAIMER } from '@/constants/disclaimer'

const SUBJECTS = ['物理', '历史', '化学', '生物', '政治', '地理']

const step = ref<'form' | 'chat'>('form')
const regions = ref<AiQaRegion[]>([])
const regionIndex = ref(0)
const score = ref('')
const rank = ref('')
const subjects = ref<string[]>([])
const majorPreference = ref('')
const regionPreference = ref('')
const accepted = ref(false)

const restoreCode = ref('')

const sessionUid = ref('')
const code = ref('')
const regionName = ref('')
const notice = ref('')
const codeBannerVisible = ref(false)

const messages = ref<AiQaMessage[]>([])
const input = ref('')
const sending = ref(false)
const creating = ref(false)

function toggleSubject(s: string) {
  const i = subjects.value.indexOf(s)
  if (i >= 0) subjects.value.splice(i, 1)
  else subjects.value.push(s)
}

async function loadRegions() {
  try {
    regions.value = await fetchAiQaRegions()
  } catch (e) {
    uni.showToast({ title: (e as Error).message, icon: 'none' })
  }
}

async function createSession() {
  if (!accepted.value) {
    uni.showToast({ title: '请先阅读并同意参考声明', icon: 'none' })
    return
  }
  const region = regions.value[regionIndex.value]
  if (!region) {
    uni.showToast({ title: '请选择地区', icon: 'none' })
    return
  }
  creating.value = true
  try {
    const res = await createAiQaSession({
      regionCode: region.code,
      score: score.value ? Number(score.value) : null,
      rank: rank.value ? Number(rank.value) : null,
      subjects: subjects.value,
      majorPreference: majorPreference.value || undefined,
      regionPreference: regionPreference.value || undefined,
    })
    sessionUid.value = res.sessionUid
    code.value = res.conversationCode
    regionName.value = res.regionName
    notice.value = res.notice || ''
    codeBannerVisible.value = true
    setAiQaActive({ sessionUid: res.sessionUid, code: res.conversationCode })
    messages.value = []
    step.value = 'chat'
  } catch (e) {
    uni.showModal({ title: '创建失败', content: (e as Error).message, showCancel: false })
  } finally {
    creating.value = false
  }
}

async function doRestore() {
  const c = restoreCode.value.trim()
  if (!c) {
    uni.showToast({ title: '请输入对话码', icon: 'none' })
    return
  }
  try {
    const res = await restoreAiQaSession(c)
    sessionUid.value = res.session.sessionUid
    code.value = c
    regionName.value = res.session.regionName
    notice.value = res.session.notice || ''
    messages.value = res.messages || []
    setAiQaActive({ sessionUid: res.session.sessionUid, code: c })
    step.value = 'chat'
  } catch (e) {
    uni.showModal({ title: '找回失败', content: (e as Error).message, showCancel: false })
  }
}

async function send() {
  const content = input.value.trim()
  if (!content) return
  if (sending.value) return
  sending.value = true
  messages.value.push({ role: 'user', content })
  input.value = ''
  try {
    const res = await sendAiQaMessage(sessionUid.value, code.value, content)
    messages.value.push(res.assistantMessage)
    if (res.compacted) uni.showToast({ title: '已自动压缩较早对话', icon: 'none' })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: 'AI 暂时没有返回有效回复：' + (e as Error).message })
  } finally {
    sending.value = false
  }
}

function copyCode() {
  if (!code.value) return
  uni.setClipboardData({ data: code.value, success: () => uni.showToast({ title: '对话码已复制', icon: 'none' }) })
}

function newSession() {
  clearAiQaActive()
  sessionUid.value = ''
  code.value = ''
  messages.value = []
  step.value = 'form'
}

function openEvidence(url?: string) {
  if (!url) return
  uni.setClipboardData({ data: url, success: () => uni.showToast({ title: '链接已复制', icon: 'none' }) })
}

onMounted(async () => {
  await loadRegions()
  const active = getAiQaActive()
  if (active) {
    try {
      const res = await listAiQaMessages(active.sessionUid, active.code)
      sessionUid.value = active.sessionUid
      code.value = active.code
      regionName.value = res.session.regionName
      notice.value = res.session.notice || ''
      messages.value = res.messages || []
      step.value = 'chat'
    } catch {
      clearAiQaActive()
    }
  }
})
</script>

<template>
  <!-- 第一步：创建 / 找回 -->
  <view v-if="step === 'form'" class="page">
    <view class="card restore">
      <input v-model="restoreCode" class="ri" placeholder="已有对话码？输入找回" />
      <view class="rb" hover-class="btn-hover" @click="doRestore">找回</view>
    </view>

    <view class="card">
      <view class="sec">未上线地区 AI 志愿问答</view>
      <view class="tip">面向尚未接入完整志愿推荐的地区，提供政策方向参考问答；不生成志愿表、不承诺录取。</view>
      <picker mode="selector" :range="regions.map((r) => r.name)" :value="regionIndex" @change="(e: any) => (regionIndex = Number(e.detail.value))">
        <view class="row"><text class="rk">地区</text><text class="rv">{{ regions[regionIndex]?.name || '请选择' }}</text></view>
      </picker>
      <view class="row"><text class="rk">分数</text><input v-model="score" class="ri2" type="number" placeholder="可选" /></view>
      <view class="row"><text class="rk">位次</text><input v-model="rank" class="ri2" type="number" placeholder="可选" /></view>
      <view class="sub-label">选科（可选）</view>
      <view class="chips">
        <text v-for="s in SUBJECTS" :key="s" class="chip" :class="{ on: subjects.indexOf(s) >= 0 }" @click="toggleSubject(s)">{{ s }}</text>
      </view>
      <view class="row"><text class="rk">专业偏好</text><input v-model="majorPreference" class="ri2" placeholder="可选" /></view>
      <view class="row"><text class="rk">地区偏好</text><input v-model="regionPreference" class="ri2" placeholder="可选" /></view>
    </view>

    <view class="card accept" @click="accepted = !accepted">
      <view class="cbox" :class="{ on: accepted }">{{ accepted ? '✓' : '' }}</view>
      <text class="accept-text">我已阅读并理解：{{ SHORT_DISCLAIMER }}</text>
    </view>

    <view class="submit" :class="{ disabled: creating }" hover-class="btn-hover" @click="createSession">
      {{ creating ? '创建中…' : '开始问答' }}
    </view>
  </view>

  <!-- 第二步：对话 -->
  <view v-else class="chat-page">
    <view v-if="codeBannerVisible" class="code-banner">
      <view class="cb-line">
        <text class="cb-title">对话码（请保存，用于换设备找回）</text>
        <text class="cb-close" @click="codeBannerVisible = false">×</text>
      </view>
      <view class="cb-code">
        <text class="cb-val">{{ code }}</text>
        <text class="cb-copy" @click="copyCode">复制</text>
      </view>
    </view>

    <view class="chat-head">
      <text class="ch-region">{{ regionName }}</text>
      <text class="ch-new" @click="newSession">新建会话</text>
    </view>
    <view v-if="notice" class="chat-notice">{{ notice }}</view>

    <scroll-view scroll-y class="msgs">
      <view v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <view class="bubble" :class="m.role">
          <template v-if="m.role === 'assistant'">
            <view v-for="(blk, j) in mdToBlocks(m.content)" :key="j" class="mb" :class="'mb-' + blk.type">
              <text v-if="blk.type === 'li'" class="mb-dot">·</text>
              <text class="mb-t">{{ blk.text }}</text>
            </view>
          </template>
          <text v-else class="b-line">{{ m.content }}</text>
        </view>
        <view v-if="m.evidence && m.evidence.length" class="evi">
          <view v-for="(ev, k) in m.evidence" :key="k" class="evi-card" hover-class="evi-hover" @click="openEvidence(ev.url)">
            <text class="evi-title">{{ ev.title }}</text>
            <text v-if="ev.sourceName" class="evi-src">{{ ev.sourceName }}</text>
            <text v-if="ev.summary" class="evi-sum">{{ ev.summary }}</text>
          </view>
        </view>
      </view>
      <view v-if="sending" class="thinking">正在检索官方信息并生成参考回答…</view>
    </scroll-view>

    <view class="input-bar">
      <input v-model="input" class="chat-input" placeholder="输入你的问题" confirm-type="send" @confirm="send" />
      <view class="send-btn" :class="{ disabled: sending }" hover-class="btn-hover" @click="send">发送</view>
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
}
.tip {
  margin: 12rpx 0 16rpx;
  font-size: 23rpx;
  color: $gz-text-sub;
  line-height: 1.7;
}
.row {
  display: flex;
  align-items: center;
  height: 76rpx;
  border-bottom: 1rpx solid $gz-border;
}
.rk {
  flex: none;
  width: 130rpx;
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
.sub-label {
  margin: 18rpx 0 12rpx;
  font-size: 24rpx;
  color: $gz-text-sub;
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
.accept {
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

/* chat */
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.code-banner {
  background: #fff7e6;
  border-bottom: 1rpx solid #ffe2b0;
  padding: 16rpx 24rpx;
}
.cb-line {
  display: flex;
  justify-content: space-between;
}
.cb-title {
  font-size: 22rpx;
  color: $gz-warn;
}
.cb-close {
  font-size: 30rpx;
  color: $gz-warn;
}
.cb-code {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.cb-val {
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  color: $gz-text;
}
.cb-copy {
  font-size: 24rpx;
  color: $gz-primary;
}
.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 24rpx;
  background: $gz-card;
  border-bottom: 1rpx solid $gz-border;
}
.ch-region {
  font-size: 28rpx;
  font-weight: 700;
  color: $gz-text;
}
.ch-new {
  font-size: 24rpx;
  color: $gz-primary;
}
.chat-notice {
  font-size: 22rpx;
  color: $gz-warn;
  background: $gz-warn-bg;
  padding: 14rpx 24rpx;
  line-height: 1.6;
}
.msgs {
  flex: 1;
  padding: 20rpx 24rpx;
}
.msg {
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
}
.msg.user {
  align-items: flex-end;
}
.msg.assistant {
  align-items: flex-start;
}
.bubble {
  max-width: 80%;
  border-radius: 18rpx;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
}
.bubble.user {
  background: $gz-primary;
}
.bubble.user .b-line {
  color: #fff;
}
.bubble.assistant {
  background: $gz-card;
  border: 1rpx solid $gz-border;
}
.b-line {
  font-size: 26rpx;
  line-height: 1.7;
  color: $gz-text;
}
.mb {
  margin-bottom: 8rpx;
}
.mb-h .mb-t {
  font-size: 26rpx;
  font-weight: 800;
  color: $gz-text;
}
.mb-h {
  margin-top: 6rpx;
}
.mb-p .mb-t {
  font-size: 25rpx;
  color: $gz-text;
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
}
.mb-li .mb-t {
  flex: 1;
  font-size: 25rpx;
  color: $gz-text;
  line-height: 1.75;
}
.evi {
  margin-top: 12rpx;
  max-width: 80%;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.evi-card {
  background: $gz-primary-light;
  border-radius: 12rpx;
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.evi-hover {
  opacity: 0.85;
}
.evi-title {
  font-size: 24rpx;
  font-weight: 700;
  color: $gz-primary;
}
.evi-src {
  font-size: 21rpx;
  color: $gz-text-weak;
}
.evi-sum {
  font-size: 22rpx;
  color: $gz-text-sub;
  line-height: 1.6;
}
.thinking {
  font-size: 23rpx;
  color: $gz-text-weak;
  padding: 10rpx 0;
}
.input-bar {
  display: flex;
  gap: 14rpx;
  padding: 16rpx 24rpx;
  background: $gz-card;
  border-top: 1rpx solid $gz-border;
}
.chat-input {
  flex: 1;
  height: 72rpx;
  background: $gz-bg;
  border-radius: 36rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
}
.send-btn {
  flex: none;
  height: 72rpx;
  padding: 0 36rpx;
  background: $gz-primary;
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.send-btn.disabled {
  opacity: 0.6;
}
</style>
