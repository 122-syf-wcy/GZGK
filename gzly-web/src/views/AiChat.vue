<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { ArrowLeft, Copy, FileSearch, MapPin, SendHorizonal, ShieldAlert, Sparkles } from 'lucide-vue-next'
import {
  BATCH_OPTIONS,
  SUBJECT_OPTIONS,
  UNLAUNCHED_REGIONS,
  type UnlaunchedRegion,
} from '@/constants/unlaunched-regions'
import {
  createAiQaSession,
  fetchAiQaRegions,
  listAiQaMessages,
  sendAiQaMessage,
  type AiQaMessage,
  type AiQaSessionView,
} from '@/api/aiQa'
import { renderMarkdown, sanitizeHttpUrl } from '@/utils/markdown'
import { toOptionalInteger, type OptionalNumericInput } from '@/utils/numberInput'
import {
  ACCEPT_KEYS,
  AI_QA_CONFIRM_NOTICE,
  AI_QA_REPLY_NOTICE,
  isDisclaimerAccepted,
  setDisclaimerAccepted,
} from '@/constants/disclaimer'

defineOptions({ name: 'AiChat' })

const ACTIVE_KEY = 'gz_ai_qa_active'
const PAGE_NOTICE = '当前地区暂未接入完整志愿推荐，本功能仅提供 AI 问答和方向参考，请以省级考试院和高校官方信息为准。'
const MAX_INPUT = 1000

const router = useRouter()

const step = ref<'form' | 'chat'>('form')
const regions = ref<UnlaunchedRegion[]>(UNLAUNCHED_REGIONS)

interface AiQaFormState {
  regionCode: string
  score: OptionalNumericInput
  rank: OptionalNumericInput
  subjects: string[]
  batch: string
  majorPreference: string
  regionPreference: string
}

const form = ref<AiQaFormState>({
  regionCode: '',
  score: '',
  rank: '',
  subjects: [] as string[],
  batch: '',
  majorPreference: '',
  regionPreference: '',
})

const creating = ref(false)
const sending = ref(false)
const errorMsg = ref('')

const session = ref<AiQaSessionView | null>(null)
const conversationCode = ref('')
const showCodeBanner = ref(false)
const messages = ref<AiQaMessage[]>([])
const input = ref('')
const chatWindow = ref<HTMLElement | null>(null)

const aiQaAccepted = ref(isDisclaimerAccepted(ACCEPT_KEYS.aiQa))
const canCreate = computed(() => !!form.value.regionCode && aiQaAccepted.value && !creating.value)
const inputLength = computed(() => input.value.trim().length)

function onAcceptChange(e: Event) {
  aiQaAccepted.value = (e.target as HTMLInputElement).checked
  setDisclaimerAccepted(ACCEPT_KEYS.aiQa, aiQaAccepted.value)
}

const suggestions = [
  '我的分数和位次大概能报哪些层次的院校？',
  '这个选科组合可以报哪些专业方向？',
  '本地有哪些值得关注的高校和优势专业？',
  '官方招生计划和录取数据去哪里查最权威？',
]

onMounted(async () => {
  try {
    const list = await fetchAiQaRegions()
    if (list && list.length) regions.value = list
  } catch {
    // 接口不可用时回退到内置列表
  }
  await restoreActiveSession()
})

async function restoreActiveSession(): Promise<void> {
  let raw: string | null = null
  try {
    raw = sessionStorage.getItem(ACTIVE_KEY)
  } catch {
    raw = null
  }
  if (!raw) return
  try {
    const active = JSON.parse(raw) as { sessionUid: string; code: string }
    if (!active.sessionUid || !active.code) return
    const restored = await listAiQaMessages(active.sessionUid, active.code)
    session.value = restored.session
    conversationCode.value = active.code
    messages.value = restored.messages || []
    showCodeBanner.value = false
    step.value = 'chat'
    scrollToBottom()
  } catch {
    clearActiveSession()
  }
}

function persistActiveSession(): void {
  if (!session.value) return
  try {
    sessionStorage.setItem(ACTIVE_KEY, JSON.stringify({
      sessionUid: session.value.sessionUid,
      code: conversationCode.value,
    }))
  } catch {
    // 忽略存储异常
  }
}

function clearActiveSession(): void {
  try {
    sessionStorage.removeItem(ACTIVE_KEY)
  } catch {
    // ignore
  }
}

function selectRegion(code: string): void {
  form.value.regionCode = code
}

function toggleSubject(subject: string): void {
  const index = form.value.subjects.indexOf(subject)
  if (index >= 0) {
    form.value.subjects.splice(index, 1)
  } else if (form.value.subjects.length < 6) {
    form.value.subjects.push(subject)
  }
}

function selectBatch(batch: string): void {
  form.value.batch = form.value.batch === batch ? '' : batch
}

async function onCreate(): Promise<void> {
  if (!form.value.regionCode) {
    showToast('请选择咨询地区')
    return
  }
  if (!aiQaAccepted.value) {
    showToast('请先勾选确认风险提示')
    return
  }
  creating.value = true
  errorMsg.value = ''
  try {
    const result = await createAiQaSession({
      regionCode: form.value.regionCode,
      score: toOptionalInteger(form.value.score),
      rank: toOptionalInteger(form.value.rank),
      subjects: form.value.subjects,
      batch: form.value.batch,
      majorPreference: form.value.majorPreference.trim(),
      regionPreference: form.value.regionPreference.trim(),
    })
    session.value = {
      sessionUid: result.sessionUid,
      regionCode: result.regionCode,
      regionName: result.regionName,
      examYear: result.examYear,
      score: toOptionalInteger(form.value.score),
      rank: toOptionalInteger(form.value.rank),
      subjects: form.value.subjects.join(','),
      batch: form.value.batch,
      majorPreference: form.value.majorPreference.trim(),
      regionPreference: form.value.regionPreference.trim(),
      notice: result.notice || PAGE_NOTICE,
    }
    conversationCode.value = result.conversationCode
    messages.value = []
    showCodeBanner.value = true
    step.value = 'chat'
    persistActiveSession()
  } catch (error: unknown) {
    showToast(error instanceof Error ? error.message : '创建会话失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

function pickSuggestion(text: string): void {
  input.value = text
}

async function onSend(): Promise<void> {
  const content = input.value.trim()
  if (!content || sending.value || !session.value) return
  if (content.length > MAX_INPUT) {
    showToast(`单条消息最多 ${MAX_INPUT} 字`)
    return
  }
  sending.value = true
  errorMsg.value = ''
  messages.value.push({ role: 'user', content })
  input.value = ''
  scrollToBottom()
  try {
    const result = await sendAiQaMessage(session.value.sessionUid, conversationCode.value, content)
    messages.value.push(result.assistantMessage)
    if (result.compacted) {
      showToast('较早的对话已自动压缩为摘要，关键信息已保留')
    }
    scrollToBottom()
  } catch (error: unknown) {
    errorMsg.value = error instanceof Error ? error.message : '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function copyCode(): Promise<void> {
  try {
    await navigator.clipboard.writeText(conversationCode.value)
    showSuccessToast('对话码已复制')
  } catch {
    showToast('复制失败，请手动记录对话码')
  }
}

function startNewSession(): void {
  clearActiveSession()
  session.value = null
  conversationCode.value = ''
  messages.value = []
  showCodeBanner.value = false
  errorMsg.value = ''
  step.value = 'form'
}

function scrollToBottom(): void {
  nextTick(() => {
    const el = chatWindow.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function render(markdown: string): string {
  return renderMarkdown(markdown, { autoSectionHeadings: true })
}
</script>

<template>
  <div class="aiqa-page gz-shell-page">
    <header class="aiqa-header">
      <button class="aiqa-header__back" type="button" aria-label="返回首页" @click="router.push('/')">
        <ArrowLeft :size="18" />
      </button>
      <div class="aiqa-header__title">
        <Sparkles :size="16" />
        未上线地区 · AI 志愿问答
      </div>
      <button
        v-if="step === 'chat'"
        class="aiqa-header__drafts"
        type="button"
        @click="router.push('/ai-chat/drafts')"
      >
        找回对话
      </button>
      <span v-else class="aiqa-header__placeholder" />
    </header>

    <main class="page-container aiqa-main">
      <section class="aiqa-notice gz-card">
        <ShieldAlert :size="16" />
        <p>{{ PAGE_NOTICE }}</p>
      </section>

      <!-- 第一步：地区选择 + 信息填写 -->
      <template v-if="step === 'form'">
        <section class="gz-card aiqa-form">
          <h2 class="aiqa-form__title">
            <MapPin :size="16" />
            选择未上线地区
          </h2>
          <p class="aiqa-form__hint">以下仅展示尚未接入完整志愿推荐的地区（已上线 8 省请回首页选择对应专区）。</p>
          <div class="aiqa-region-grid">
            <button
              v-for="region in regions"
              :key="region.code"
              type="button"
              class="aiqa-region"
              :class="{ active: form.regionCode === region.code }"
              :data-testid="`aiqa-region-${region.code}`"
              @click="selectRegion(region.code)"
            >
              {{ region.name }}
            </button>
          </div>

          <div class="aiqa-field-grid">
            <label class="aiqa-field">
              <span>分数</span>
              <input v-model="form.score" type="number" inputmode="numeric" placeholder="如 580" />
            </label>
            <label class="aiqa-field">
              <span>位次</span>
              <input v-model="form.rank" type="number" inputmode="numeric" placeholder="如 30000" />
            </label>
          </div>

          <div class="aiqa-field">
            <span>选科（可多选）</span>
            <div class="aiqa-chip-row">
              <button
                v-for="subject in SUBJECT_OPTIONS"
                :key="subject"
                type="button"
                class="aiqa-chip"
                :class="{ active: form.subjects.includes(subject) }"
                @click="toggleSubject(subject)"
              >
                {{ subject }}
              </button>
            </div>
          </div>

          <div class="aiqa-field">
            <span>批次</span>
            <div class="aiqa-chip-row">
              <button
                v-for="batch in BATCH_OPTIONS"
                :key="batch"
                type="button"
                class="aiqa-chip"
                :class="{ active: form.batch === batch }"
                @click="selectBatch(batch)"
              >
                {{ batch }}
              </button>
            </div>
          </div>

          <label class="aiqa-field">
            <span>专业偏好</span>
            <input v-model="form.majorPreference" type="text" maxlength="120" placeholder="如 计算机、临床医学、师范" />
          </label>
          <label class="aiqa-field">
            <span>地区/城市偏好</span>
            <input v-model="form.regionPreference" type="text" maxlength="120" placeholder="如 省内、北上广、新一线" />
          </label>

          <label class="aiqa-confirm">
            <input type="checkbox" :checked="aiQaAccepted" @change="onAcceptChange" />
            <span>{{ AI_QA_CONFIRM_NOTICE }}</span>
          </label>

          <button class="aiqa-primary-btn" type="button" :disabled="!canCreate" @click="onCreate">
            {{ creating ? '创建中…' : '创建 AI 问答会话' }}
          </button>
          <button class="aiqa-text-btn" type="button" @click="router.push('/ai-chat/drafts')">
            已有对话码？点此找回历史对话
          </button>
        </section>
      </template>

      <!-- 第二步：对话 -->
      <template v-else>
        <section v-if="showCodeBanner" class="gz-card aiqa-code-banner">
          <div class="aiqa-code-banner__head">
            <strong>请妥善保存对话码</strong>
            <span>关闭页面后，只能凭此对话码找回历史对话。完整对话码仅展示这一次。</span>
          </div>
          <div class="aiqa-code-banner__code">
            <code>{{ conversationCode }}</code>
            <button type="button" @click="copyCode">
              <Copy :size="15" />
              复制
            </button>
          </div>
          <button class="aiqa-code-banner__ack" type="button" @click="showCodeBanner = false">
            我已保存
          </button>
        </section>

        <section v-if="session" class="gz-card aiqa-session-info">
          <div class="aiqa-session-info__title">
            <MapPin :size="15" />
            {{ session.regionName }} · AI 志愿问答
          </div>
          <div class="aiqa-session-tags">
            <span v-if="session.score != null">分数 {{ session.score }}</span>
            <span v-if="session.rank != null">位次 {{ session.rank }}</span>
            <span v-if="session.subjects">选科 {{ session.subjects }}</span>
            <span v-if="session.batch">{{ session.batch }}</span>
            <span v-if="session.majorPreference">专业 {{ session.majorPreference }}</span>
            <span v-if="session.regionPreference">地区 {{ session.regionPreference }}</span>
          </div>
          <button class="aiqa-text-btn aiqa-text-btn--inline" type="button" @click="startNewSession">
            换一个地区，重新开始
          </button>
        </section>

        <section class="gz-card aiqa-chat">
          <div v-if="!messages.length" class="aiqa-suggestions" aria-label="常用问题">
            <button v-for="s in suggestions" :key="s" type="button" @click="pickSuggestion(s)">{{ s }}</button>
          </div>

          <div ref="chatWindow" class="aiqa-chat-window" aria-live="polite">
            <div v-if="!messages.length" class="aiqa-chat-empty">
              <Sparkles :size="18" />
              <span>可以直接提问，例如「我的分数能报哪些层次的院校」「这个选科能报哪些专业」。</span>
            </div>
            <div
              v-for="(message, index) in messages"
              :key="`aiqa-msg-${index}`"
              class="aiqa-msg"
              :class="`is-${message.role}`"
            >
              <span class="aiqa-msg__role">{{ message.role === 'user' ? '我' : 'AI' }}</span>
              <div class="aiqa-msg__main">
                <div
                  v-if="message.role === 'assistant'"
                  class="markdown-body aiqa-msg__body"
                  v-html="render(message.content)"
                ></div>
                <div v-else class="aiqa-msg__body aiqa-msg__body--user">{{ message.content }}</div>

                <p v-if="message.role === 'assistant'" class="aiqa-msg__notice">{{ AI_QA_REPLY_NOTICE }}</p>

                <div v-if="message.evidence && message.evidence.length" class="aiqa-evidence">
                  <div class="aiqa-evidence__title">
                    <FileSearch :size="13" />
                    参考来源（请自行核验官方原文）
                  </div>
                  <component
                    :is="sanitizeHttpUrl(ev.url) ? 'a' : 'div'"
                    v-for="(ev, evIndex) in message.evidence"
                    :key="`ev-${index}-${evIndex}`"
                    class="aiqa-evidence__item"
                    :href="sanitizeHttpUrl(ev.url) || undefined"
                    :target="sanitizeHttpUrl(ev.url) ? '_blank' : undefined"
                    :rel="sanitizeHttpUrl(ev.url) ? 'noopener noreferrer' : undefined"
                  >
                    <span class="aiqa-evidence__item-title">{{ ev.title || ev.url }}</span>
                    <span v-if="ev.sourceName" class="aiqa-evidence__item-source">{{ ev.sourceName }}</span>
                    <span v-if="ev.summary" class="aiqa-evidence__item-summary">{{ ev.summary }}</span>
                  </component>
                </div>
              </div>
            </div>
            <div v-if="sending" class="aiqa-chat-loading">
              <span class="status-dot status-dot--active"></span>
              AI 正在检索官方信息并整理回答…
            </div>
          </div>

          <p v-if="errorMsg" class="aiqa-chat-error">{{ errorMsg }}</p>

          <form class="aiqa-chat-form" @submit.prevent="onSend">
            <textarea
              v-model="input"
              rows="2"
              :maxlength="MAX_INPUT"
              placeholder="输入你的志愿问题，例如：这个分数在本省大概什么位置？"
              aria-label="向 AI 志愿问答助手提问"
              @keydown.enter.exact.prevent="onSend"
            ></textarea>
            <button type="submit" :disabled="sending || !inputLength" aria-label="发送">
              <SendHorizonal :size="18" />
            </button>
          </form>
          <div class="aiqa-chat-form__meta">
            <span>{{ inputLength }}/{{ MAX_INPUT }}</span>
            <span>每个会话每天最多 100 条提问</span>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.aiqa-page {
  min-height: 100dvh;
  background: var(--gz-bg, #f5f3ee);
}

.aiqa-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 253, 250, 0.96);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.aiqa-header__back {
  width: 36px;
  height: 36px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.aiqa-header__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 850;
  color: #0f172a;
}

.aiqa-header__drafts {
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
  padding: 6px 12px;
}

.aiqa-header__placeholder {
  width: 36px;
}

.aiqa-main {
  padding-top: 14px;
  padding-bottom: 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aiqa-notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
}

.aiqa-notice p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}

.aiqa-form__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 18px;
  font-weight: 850;
  color: #0f172a;
}

.aiqa-form__hint {
  margin: 8px 0 16px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.aiqa-region-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.aiqa-region {
  min-height: 40px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.aiqa-region.active {
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: #fff;
}

.aiqa-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.aiqa-field {
  display: block;
  margin-top: 16px;
}

.aiqa-field > span {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 800;
  color: #334155;
}

.aiqa-field input {
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: 12px;
  background: #fff;
  font-size: 14px;
  color: #0f172a;
  outline: none;
  box-sizing: border-box;
}

.aiqa-field input:focus {
  border-color: rgba(29, 78, 216, 0.4);
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.08);
}

.aiqa-field-grid .aiqa-field {
  margin-top: 0;
}

.aiqa-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.aiqa-chip {
  min-height: 36px;
  padding: 6px 12px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.aiqa-chip.active {
  border-color: #1d4ed8;
  background: #eff6ff;
  color: #1d4ed8;
}

.aiqa-primary-btn {
  width: 100%;
  min-height: 48px;
  margin-top: 22px;
  border: none;
  border-radius: 14px;
  background: #0f172a;
  color: #fff;
  font-size: 15px;
  font-weight: 850;
}

.aiqa-primary-btn:disabled {
  opacity: 0.5;
}

.aiqa-text-btn {
  width: 100%;
  margin-top: 12px;
  border: none;
  background: transparent;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 800;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.aiqa-text-btn--inline {
  width: auto;
  margin-top: 10px;
}

.aiqa-code-banner {
  background: #ecfeff;
  border: 1px solid #a5f3fc;
}

.aiqa-code-banner__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.aiqa-code-banner__head strong {
  font-size: 15px;
  color: #155e75;
}

.aiqa-code-banner__head span {
  font-size: 12px;
  line-height: 1.6;
  color: #0e7490;
}

.aiqa-code-banner__code {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.aiqa-code-banner__code code {
  flex: 1;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px dashed #06b6d4;
  font-size: 18px;
  font-weight: 850;
  letter-spacing: 0.18em;
  color: #0f172a;
  text-align: center;
  overflow-x: auto;
}

.aiqa-code-banner__code button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: #0891b2;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.aiqa-code-banner__ack {
  width: 100%;
  min-height: 40px;
  margin-top: 12px;
  border: 1px solid #06b6d4;
  border-radius: 10px;
  background: #fff;
  color: #0e7490;
  font-size: 13px;
  font-weight: 800;
}

.aiqa-session-info__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 850;
  color: #0f172a;
}

.aiqa-session-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.aiqa-session-tags span {
  padding: 4px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.aiqa-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.aiqa-suggestions button {
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.aiqa-chat-window {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 56vh;
  overflow: auto;
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.aiqa-chat-empty,
.aiqa-chat-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.aiqa-msg {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
}

.aiqa-msg__role {
  min-height: 28px;
  padding: 5px 8px;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
}

.aiqa-msg.is-user .aiqa-msg__role {
  background: #0f172a;
  color: #fff;
}

.aiqa-msg__main {
  min-width: 0;
}

.aiqa-msg__body {
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  overflow-wrap: anywhere;
}

.aiqa-msg__body--user {
  background: #eef2ff;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
  color: #1e293b;
}

.aiqa-confirm {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 18px;
  padding: 12px;
  border-radius: 12px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  cursor: pointer;
}
.aiqa-confirm input {
  width: 18px;
  height: 18px;
  margin-top: 1px;
  flex-shrink: 0;
}
.aiqa-confirm span {
  font-size: 13px;
  line-height: 1.6;
  color: #92400e;
  font-weight: 700;
}
.aiqa-msg__notice {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: #94a3b8;
}

.aiqa-evidence {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.aiqa-evidence__title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 800;
  color: #166534;
  margin-bottom: 8px;
}

.aiqa-evidence__item {
  display: block;
  padding: 8px 10px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #d1fae5;
  text-decoration: none;
  margin-bottom: 8px;
}

.aiqa-evidence__item:last-child {
  margin-bottom: 0;
}

.aiqa-evidence__item-title {
  display: block;
  font-size: 13px;
  font-weight: 800;
  color: #1d4ed8;
  overflow-wrap: anywhere;
}

.aiqa-evidence__item-source {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  color: #166534;
}

.aiqa-evidence__item-summary {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #475569;
}

.aiqa-chat-error {
  margin: 10px 0 0;
  padding: 9px 11px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fcd9b6;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
}

.aiqa-chat-form {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
  align-items: stretch;
}

.aiqa-chat-form textarea {
  width: 100%;
  min-height: 52px;
  max-height: 160px;
  resize: vertical;
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: 14px;
  padding: 11px 12px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.55;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}

.aiqa-chat-form textarea:focus {
  border-color: rgba(29, 78, 216, 0.4);
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.08);
}

.aiqa-chat-form button {
  min-width: 48px;
  min-height: 52px;
  border: none;
  border-radius: 14px;
  background: #0f172a;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.aiqa-chat-form button:disabled {
  opacity: 0.45;
}

.aiqa-chat-form__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
  display: inline-block;
}

.status-dot--active {
  background: #10b981;
  animation: aiqa-pulse 1s ease-in-out infinite;
}

@keyframes aiqa-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

@media (max-width: 430px) {
  .aiqa-region-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .aiqa-msg {
    grid-template-columns: 1fr;
  }

  .aiqa-msg__role {
    width: fit-content;
  }
}
</style>
