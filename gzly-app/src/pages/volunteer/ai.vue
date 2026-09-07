<script setup lang="ts">
/**
 * AI 深度解读。
 *
 * 后端一次性返回全文（内部拼完 delta 才发出），因此这里的"流式感"
 * 是展示层的渐进呈现：报告分块渐显，追问回答逐字打出。内容是真实的，
 * 只是节奏是客户端控制的——这让体验对齐 ChatGPT，同时不依赖后端改造。
 *
 * 状态机：
 *   报告：thinking（阶段化思考动效）→ ready / error
 *   对话轮：thinking → answering（打字机+光标）→ done / error（可重试）
 */
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import GzEmpty from '@/components/GzEmpty.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import { askSkills, fetchAiAnalysis } from '@/api/volunteer'
import { AI_GENERATED_NOTICE } from '@/constants/compliance'
import { parseMarkdown } from '@/utils/markdown'
import { usePlanStore } from '@/stores/plan'

const store = usePlanStore()

// ---- 报告 ----
type ReportState = 'thinking' | 'ready' | 'error'
const reportState = ref<ReportState>('thinking')
const errorText = ref('')
const content = ref('')
const animateReport = ref(true)
/** 没有方案凭证时，错误态的动作应是"去生成"而不是徒劳的重试 */
const noPlan = ref(false)

const blocks = computed(() => parseMarkdown(content.value))
const plan = computed(() => store.plan)
const advisor = computed(() => plan.value?.raw.advisorAdvice as Record<string, string> | undefined)

const THINK_STAGES = ['正在读取方案数据', '正在分析梯度结构', '正在核对人工复核清单', '正在撰写解读报告']
const stageIndex = ref(0)
const elapsed = ref(0)
let stageTimer: ReturnType<typeof setInterval> | undefined
let elapsedTimer: ReturnType<typeof setInterval> | undefined

function startThinkingTimers() {
  stopThinkingTimers()
  stageIndex.value = 0
  elapsed.value = 0
  stageTimer = setInterval(() => {
    stageIndex.value = (stageIndex.value + 1) % THINK_STAGES.length
  }, 1100)
  elapsedTimer = setInterval(() => {
    elapsed.value += 1
  }, 1000)
}

function stopThinkingTimers() {
  if (stageTimer) clearInterval(stageTimer)
  if (elapsedTimer) clearInterval(elapsedTimer)
  stageTimer = undefined
  elapsedTimer = undefined
}

async function loadReport(force = false) {
  const cred = store.currentCredentials()
  if (!cred) {
    reportState.value = 'error'
    errorText.value = '没有找到方案凭证'
    return
  }
  reportState.value = 'thinking'
  animateReport.value = true
  startThinkingTimers()
  try {
    const res = await fetchAiAnalysis(cred.planId, cred.safetyCode, cred.accessKey, force)
    content.value = res.content || ''
    store.aiContent = content.value
    reportState.value = content.value ? 'ready' : 'error'
    if (!content.value) errorText.value = 'AI 返回内容为空'
  } catch (e) {
    reportState.value = 'error'
    errorText.value = (e as Error).message || 'AI 服务暂时不可用'
  } finally {
    stopThinkingTimers()
  }
}

// ---- 对话 ----
type TurnState = 'thinking' | 'answering' | 'done' | 'error'
interface ChatTurn {
  id: number
  role: 'user' | 'ai'
  /** 完整文本 */
  text: string
  /** 已展示的部分（打字机进度） */
  shown: string
  state: TurnState
  /** 出错时用于重试的原问题 */
  question?: string
}

const turns = ref<ChatTurn[]>([])
const question = ref('')
const busy = computed(() => turns.value.some(t => t.state === 'thinking' || t.state === 'answering'))
let turnSeq = 0
let typeTimer: ReturnType<typeof setInterval> | undefined

const SUGGESTED = ['稳区该怎么排序？', '哪些条目风险最高？', '医学类需要注意什么？']

let lastScrollAt = 0
function scrollToBottom(forceNow = false) {
  const now = Date.now()
  if (!forceNow && now - lastScrollAt < 240) return
  lastScrollAt = now
  uni.pageScrollTo({ scrollTop: 999999, duration: 0 })
}

function stopTypewriter() {
  if (typeTimer) clearInterval(typeTimer)
  typeTimer = undefined
}

/** 打字机：短回答逐字，长回答按比例加速，保证 8 秒内出完 */
function typewrite(turn: ChatTurn) {
  stopTypewriter()
  turn.state = 'answering'
  const full = turn.text
  const step = Math.max(2, Math.ceil(full.length / 260))
  let i = 0
  typeTimer = setInterval(() => {
    i = Math.min(full.length, i + step)
    turn.shown = full.slice(0, i)
    scrollToBottom()
    if (i >= full.length) {
      stopTypewriter()
      turn.state = 'done'
      scrollToBottom(true)
    }
  }, 30)
}

/** 停止 = 跳过剩余的逐字动画，直接给全文（请求本身已完成） */
function skipTyping() {
  const active = turns.value.find(t => t.state === 'answering')
  if (!active) return
  stopTypewriter()
  active.shown = active.text
  active.state = 'done'
  scrollToBottom(true)
}

async function ask(text: string) {
  const q = text.trim()
  if (!q || busy.value) return
  const cred = store.currentCredentials()
  if (!cred) return

  question.value = ''
  turns.value.push({ id: ++turnSeq, role: 'user', text: q, shown: q, state: 'done' })
  turns.value.push({ id: ++turnSeq, role: 'ai', text: '', shown: '', state: 'thinking', question: q })
  // 必须从数组里取回响应式代理再操作；继续改 push 前的原始对象不会触发重渲染
  const aiTurn = turns.value[turns.value.length - 1]
  scrollToBottom(true)

  try {
    const res = await askSkills(cred.planId, cred.safetyCode, cred.accessKey, q, content.value)
    aiTurn.text = res.answer || '暂时没有可用回答'
    typewrite(aiTurn)
  } catch (e) {
    aiTurn.text = (e as Error).message || '请求失败'
    aiTurn.shown = aiTurn.text
    aiTurn.state = 'error'
    scrollToBottom(true)
  }
}

function retryTurn(turn: ChatTurn) {
  if (busy.value || !turn.question) return
  // 移除失败的这一轮（连同它的提问气泡），重新发起
  const idx = turns.value.indexOf(turn)
  const removeFrom = idx > 0 && turns.value[idx - 1].role === 'user' ? idx - 1 : idx
  turns.value.splice(removeFrom)
  ask(turn.question)
}

function copyTurn(turn: ChatTurn) {
  uni.setClipboardData({
    data: turn.text,
    success: () => uni.showToast({ title: '已复制', icon: 'none' }),
  })
}

function goGenerate() {
  uni.switchTab({ url: '/pages/volunteer/form' })
}

onLoad(async () => {
  const ok = await store.restore()
  if (!ok) {
    noPlan.value = true
    reportState.value = 'error'
    errorText.value = '还没有可解读的方案。先在「志愿」里生成一份，或在「我的」里用安全码找回。'
    return
  }
  // 同一会话已生成过：直接展示，不再播放渐显动画，也不重复占用后端并发额度
  if (store.aiContent) {
    content.value = store.aiContent
    animateReport.value = false
    reportState.value = 'ready'
    return
  }
  loadReport()
})

onUnload(() => {
  stopTypewriter()
  stopThinkingTimers()
})
</script>

<template>
  <view class="page">
    <GzNavBar title="AI 深度解读" />

    <view class="pad">
      <view class="hero">
        <text class="hero__title">读懂这份方案</text>
        <text class="hero__desc">
          解读只基于系统已生成的结构化结果与人工复核清单，不会编造政策、分数线或录取结论。
        </text>
      </view>

      <!-- 报告：思考态 -->
      <view v-if="reportState === 'thinking'" class="think">
        <view class="think__head">
          <view class="dots">
            <view v-for="i in 3" :key="i" class="dots__d" :style="{ animationDelay: `${(i - 1) * 0.18}s` }" />
          </view>
          <text class="think__stage">{{ THINK_STAGES[stageIndex] }}</text>
          <text class="think__time">{{ elapsed }}s</text>
        </view>
        <view class="think__lines">
          <view v-for="i in 4" :key="i" class="think__line" :style="{ width: `${92 - i * 14}%`, animationDelay: `${i * 0.12}s` }" />
        </view>
        <text class="think__hint">通常需要十几秒，AI 会通读全部 {{ plan?.items.length || 96 }} 条志愿后再下笔</text>
      </view>

      <!-- 报告：失败态 -->
      <GzEmpty
        v-else-if="reportState === 'error'"
        :title="noPlan ? '还没有方案' : '解读暂时不可用'"
        :desc="errorText"
        :retry-text="noPlan ? '去生成方案' : '重新生成'"
        @retry="noPlan ? goGenerate() : loadReport(true)"
      />

      <!-- 报告：就绪态 -->
      <template v-else>
        <view class="report">
          <template v-for="(b, i) in blocks" :key="i">
            <view
              class="rb"
              :class="{ 'rb--animate': animateReport }"
              :style="animateReport ? { animationDelay: `${Math.min(i * 70, 1400)}ms` } : undefined"
            >
              <text v-if="b.type === 'h2'" class="report__h2">{{ b.text }}</text>
              <text v-else-if="b.type === 'h3'" class="report__h3">{{ b.text }}</text>
              <view v-else-if="b.type === 'li'" class="report__li">
                <text class="report__dot">·</text>
                <text class="report__litext">{{ b.text }}</text>
              </view>
              <text v-else class="report__p">{{ b.text }}</text>
            </view>
          </template>
        </view>

        <view class="notice">
          <text class="notice__text">{{ AI_GENERATED_NOTICE }}</text>
          <text class="notice__regen" @tap="loadReport(true)">重新生成解读</text>
        </view>

        <view v-if="advisor" class="advisor">
          <text class="advisor__title">{{ advisor.title }}</text>
          <text class="advisor__p">{{ advisor.positioning }}</text>
          <text class="advisor__p">{{ advisor.gradientAdvice }}</text>
          <text class="advisor__p">{{ advisor.majorAdvice }}</text>
          <view class="advisor__src">
            <text class="advisor__src-text">{{ advisor.sourceNote }}</text>
            <text class="advisor__src-link">{{ advisor.sourceProjectName }}</text>
          </view>
        </view>

        <!-- 对话 -->
        <view class="chat">
          <text class="chat__title">继续追问</text>

          <view v-if="!turns.length" class="chat__sug">
            <view v-for="s in SUGGESTED" :key="s" class="chat__sug-item" @tap="ask(s)">
              <text class="chat__sug-text">{{ s }}</text>
            </view>
          </view>

          <view v-for="t in turns" :key="t.id" class="turn" :class="`turn--${t.role}`">
            <image v-if="t.role === 'ai'" class="turn__avatar" src="/static/img/logo-mark.webp" mode="aspectFit" />
            <view class="turn__bubble" :class="{ 'turn__bubble--error': t.state === 'error' }">
              <!-- 思考态：三点呼吸 -->
              <view v-if="t.state === 'thinking'" class="dots dots--inbubble">
                <view v-for="i in 3" :key="i" class="dots__d" :style="{ animationDelay: `${(i - 1) * 0.18}s` }" />
              </view>

              <!-- 回答态：打字机 + 光标 -->
              <template v-else>
                <text class="turn__text">{{ t.shown }}<text v-if="t.state === 'answering'" class="cursor">▍</text></text>
                <view v-if="t.role === 'ai' && t.state === 'done'" class="turn__actions">
                  <text class="turn__action" @tap="copyTurn(t)">复制</text>
                </view>
                <view v-if="t.state === 'error'" class="turn__actions">
                  <text class="turn__action turn__action--retry" @tap="retryTurn(t)">重试</text>
                </view>
              </template>
            </view>
          </view>
        </view>
      </template>
    </view>

    <!-- 固定输入栏，ChatGPT 式 -->
    <view v-if="reportState === 'ready'" class="inputbar">
      <view class="inputbar__inner">
        <input
          v-model="question"
          class="inputbar__field"
          placeholder="围绕这份方案提问"
          placeholder-class="inputbar__ph"
          confirm-type="send"
          :disabled="busy"
          @confirm="ask(question)"
        />
        <!-- 回答中变成停止（跳过逐字动画）；思考中禁用 -->
        <view
          v-if="turns.some(t => t.state === 'answering')"
          class="inputbar__btn inputbar__btn--stop"
          @tap="skipTyping"
        >
          <view class="inputbar__stopicon" />
        </view>
        <view
          v-else
          class="inputbar__btn"
          :class="{ 'inputbar__btn--disabled': !question.trim() || busy }"
          @tap="ask(question)"
        >
          <text class="inputbar__arrow">↑</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding-bottom: calc(220rpx + env(safe-area-inset-bottom));
}

.pad {
  padding: 0 $gz-page-x;
}

.hero {
  padding: $gz-space-3 0 $gz-space-4;

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

/* ---- 思考态 ---- */

.think {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;

  &__head {
    display: flex;
    align-items: center;
  }

  &__stage {
    flex: 1;
    margin-left: $gz-space-2;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    animation: gz-breathe 1.6s ease-in-out infinite;
  }

  &__time {
    font-size: $gz-text-xs;
    color: var(--gz-text-3);
    font-variant-numeric: tabular-nums;
  }

  &__lines {
    margin-top: $gz-space-3;
  }

  &__line {
    height: 22rpx;
    border-radius: var(--gz-radius-sm);
    background: linear-gradient(90deg, var(--gz-surface-3) 25%, var(--gz-surface-2) 45%, var(--gz-surface-3) 65%);
    background-size: 300% 100%;
    margin-bottom: 14rpx;
    animation: gz-shimmer 1.5s linear infinite;
  }

  &__hint {
    display: block;
    margin-top: $gz-space-2;
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.7;
  }
}

.dots {
  display: inline-flex;
  align-items: center;

  &--inbubble {
    padding: 8rpx 4rpx;
  }

  &__d {
    width: 12rpx;
    height: 12rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-text-3);
    margin-right: 8rpx;
    animation: gz-bounce 1.1s ease-in-out infinite;
  }
}

/* ---- 报告渐显 ---- */

.rb--animate {
  opacity: 0;
  animation: gz-fade-up 0.45s ease forwards;
}

.report {
  &__h2 {
    display: block;
    margin: $gz-space-5 0 $gz-space-2;
    font-family: var(--gz-font-serif);
    font-size: $gz-text-title;
    color: var(--gz-text);
  }

  &__h3 {
    display: block;
    margin: $gz-space-4 0 $gz-space-2;
    font-size: $gz-text-section;
    color: var(--gz-text);
  }

  &__p {
    display: block;
    margin-bottom: $gz-space-2;
    font-size: $gz-text-body;
    color: var(--gz-text-2);
    line-height: 1.85;
  }

  &__li {
    display: flex;
    margin-bottom: 12rpx;
  }

  &__dot {
    color: var(--gz-text-3);
    margin-right: 12rpx;
  }

  &__litext {
    flex: 1;
    font-size: $gz-text-body;
    color: var(--gz-text-2);
    line-height: 1.8;
  }
}

.notice {
  margin: $gz-space-4 0;
  padding: $gz-space-3;
  border-radius: var(--gz-radius-md);
  background: var(--gz-surface-2);
  display: flex;
  align-items: center;
  justify-content: space-between;

  &__text {
    flex: 1;
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.8;
  }

  &__regen {
    flex-shrink: 0;
    margin-left: $gz-space-2;
    font-size: 20rpx;
    color: var(--gz-wen);
  }
}

.advisor {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;
  margin-bottom: $gz-space-4;

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: $gz-text-section;
    color: var(--gz-text);
    margin-bottom: $gz-space-2;
  }

  &__p {
    display: block;
    margin-bottom: 12rpx;
    font-size: $gz-text-sm;
    color: var(--gz-text-2);
    line-height: 1.8;
  }

  &__src {
    margin-top: $gz-space-2;
    padding-top: $gz-space-2;
    border-top: 2rpx solid var(--gz-border);
  }

  &__src-text {
    display: block;
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.7;
  }

  &__src-link {
    display: block;
    margin-top: 6rpx;
    font-size: 20rpx;
    color: var(--gz-wen);
  }
}

/* ---- 对话 ---- */

.chat {
  margin-bottom: $gz-space-4;

  &__title {
    display: block;
    font-family: var(--gz-font-serif);
    font-size: $gz-text-section;
    color: var(--gz-text);
    margin-bottom: $gz-space-3;
  }

  &__sug {
    display: flex;
    flex-wrap: wrap;
  }

  &__sug-item {
    padding: 14rpx 26rpx;
    margin: 0 12rpx 12rpx 0;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
    transition: transform 0.12s ease;

    &:active {
      transform: scale(0.96);
    }
  }

  &__sug-text {
    font-size: $gz-text-xs;
    color: var(--gz-text-2);
  }
}

.turn {
  display: flex;
  margin-bottom: $gz-space-3;

  &--user {
    justify-content: flex-end;
  }

  &__avatar {
    width: 56rpx;
    height: 56rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
    margin-right: 14rpx;
    flex-shrink: 0;
  }

  &__bubble {
    max-width: 78%;
    padding: 20rpx 26rpx;
    border-radius: var(--gz-radius-lg);
    background: var(--gz-surface-2);

    &--error {
      background: rgba(220, 38, 38, 0.07);
    }
  }

  &--user &__bubble {
    background: var(--gz-ink);
    border-bottom-right-radius: var(--gz-radius-sm);
  }

  &--ai &__bubble {
    border-top-left-radius: var(--gz-radius-sm);
  }

  &__text {
    font-size: $gz-text-sm;
    line-height: 1.8;
    color: var(--gz-text-2);
    word-break: break-word;
    white-space: pre-wrap;
  }

  &--user &__text {
    color: var(--gz-text-inverse);
  }

  &__bubble--error &__text {
    color: var(--gz-danger);
  }

  &__actions {
    display: flex;
    margin-top: 10rpx;
  }

  &__action {
    font-size: 20rpx;
    color: var(--gz-text-3);
    margin-right: 20rpx;

    &--retry {
      color: var(--gz-wen);
    }
  }
}

.cursor {
  color: var(--gz-text);
  animation: gz-blink 0.9s step-end infinite;
}

/* ---- 固定输入栏 ---- */

.inputbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 900;
  padding: $gz-space-2 $gz-page-x calc(#{$gz-space-2} + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(24rpx);
  border-top: 2rpx solid var(--gz-border);

  &__inner {
    display: flex;
    align-items: center;
  }

  &__field {
    flex: 1;
    height: 84rpx;
    padding: 0 $gz-space-3;
    margin-right: $gz-space-2;
    border-radius: var(--gz-radius-full);
    background: var(--gz-surface-2);
    font-size: $gz-text-sm;
    color: var(--gz-text);
  }

  &__ph {
    color: var(--gz-text-3);
  }

  &__btn {
    width: 84rpx;
    height: 84rpx;
    border-radius: var(--gz-radius-full);
    background: var(--gz-ink);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.12s ease, opacity 0.15s ease;
    flex-shrink: 0;

    &:active {
      transform: scale(0.94);
    }

    &--disabled {
      opacity: 0.35;
    }

    &--stop {
      background: var(--gz-surface-3);
    }
  }

  &__arrow {
    font-size: 40rpx;
    color: var(--gz-text-inverse);
    line-height: 1;
    margin-top: -4rpx;
  }

  &__stopicon {
    width: 26rpx;
    height: 26rpx;
    border-radius: 6rpx;
    background: var(--gz-text);
  }
}

/* ---- 动画 ---- */

@keyframes gz-bounce {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  50% {
    transform: translateY(-8rpx);
    opacity: 1;
  }
}

@keyframes gz-breathe {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

@keyframes gz-shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

@keyframes gz-fade-up {
  from {
    opacity: 0;
    transform: translateY(16rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes gz-blink {
  50% {
    opacity: 0;
  }
}
</style>
