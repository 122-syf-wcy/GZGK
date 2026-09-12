<template>
  <div class="zxf-suggestions" aria-label="常用追问">
    <button v-for="suggestion in suggestions" :key="suggestion" type="button" @click="$emit('pick', suggestion)">
      {{ suggestion }}
    </button>
  </div>

  <div ref="chatWindow" class="zxf-chat-window" aria-live="polite">
    <div v-if="!messages.length" class="zxf-chat-empty">
      <Bot :size="18" />
      <span>可以直接追问“哪些冲档值得留”“就业优先怎么排”“哪些专业需要避坑”。</span>
    </div>
    <div
      v-for="(message, index) in messages"
      :key="`zxf-skill-${index}`"
      class="zxf-chat-message"
      :class="`is-${message.role}`"
    >
      <span class="zxf-chat-message__role">{{ message.role === 'user' ? '我' : 'skills' }}</span>
      <div class="zxf-chat-message__stack">
        <div v-if="message.steps?.length" class="zxf-steps">
          <div v-for="(step, si) in message.steps" :key="`step-${index}-${si}`" class="zxf-steps__item">
            <span v-if="step.status === 'done'" class="zxf-steps__mark is-done"><Check :size="11" /></span>
            <span v-else class="zxf-steps__mark is-running"></span>
            <span class="zxf-steps__label">{{ step.label }}</span>
          </div>
        </div>
        <details v-if="message.thinking" class="zxf-thinking-fold" :open="message.streaming && !message.content">
          <summary>{{ message.streaming && !message.content ? '正在深度思考…' : '已深度思考，点击展开' }}</summary>
          <div class="zxf-thinking-fold__body">{{ message.thinking }}</div>
        </details>
        <div v-if="message.content || message.role === 'user' || !hasProgress(message)" class="markdown-body zxf-chat-message__body">
          <span v-html="render(message.content)"></span>
          <span
            v-if="(message.streaming || (typing && index === messages.length - 1)) && message.role === 'assistant' && message.content"
            class="typewriter-cursor"
          ></span>
          <span
            v-if="message.role === 'assistant' && message.streaming && !message.content && !hasProgress(message)"
            class="zxf-thinking-live__dots"
            aria-hidden="true"
          ><i></i><i></i><i></i></span>
        </div>
      </div>
    </div>
    <div v-if="loading" class="zxf-chat-message is-assistant">
      <span class="zxf-chat-message__role">skills</span>
      <div class="zxf-chat-message__body zxf-thinking-live">
        <span class="zxf-thinking-live__dots" aria-hidden="true"><i></i><i></i><i></i></span>
        <span>{{ thinkingPhase }} {{ thinkingSeconds }}s</span>
      </div>
    </div>
  </div>

  <p v-if="error" class="zxf-chat-error">{{ error }}</p>
  <form class="zxf-chat-form" @submit.prevent="$emit('send')">
    <textarea
      id="zxf-skill-input"
      :value="modelValue"
      rows="2"
      maxlength="800"
      placeholder="例如：这些志愿里哪些要删，哪些应该前移？"
      aria-label="向张雪峰.skills 填报服务提问"
      @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      @keydown.enter.exact.prevent="$emit('send')"
    ></textarea>
    <button type="submit" :disabled="loading || !modelValue.trim()" aria-label="发送">
      <SendHorizonal :size="18" />
    </button>
  </form>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Bot, Check, SendHorizonal } from 'lucide-vue-next'
import type { ZxfSkillChatMessage } from '@/api/volunteer'

/** 消息是否已有过程性内容（步骤/思考），有则不显示空白气泡的等待动画。 */
function hasProgress(message: ZxfSkillChatMessage): boolean {
  return Boolean(message.steps?.length || message.thinking)
}

const props = withDefaults(defineProps<{
  modelValue: string
  suggestions: string[]
  messages: ZxfSkillChatMessage[]
  loading: boolean
  /** 回复正在打字机渐显中 */
  typing?: boolean
  error: string
  render: (markdown: string) => string
}>(), {
  typing: false,
})

defineEmits<{
  'update:modelValue': [value: string]
  pick: [value: string]
  send: []
}>()

/** 思考中气泡：阶段轮播 + 计时，与 ChatGPT 的等待反馈一致。 */
const THINKING_PHASES = [
  '正在阅读你的志愿方案…',
  '正在检索 skills 知识片段…',
  '正在核对位次与计划数据…',
  '正在组织回复…',
]
const thinkingPhase = ref(THINKING_PHASES[0])
const thinkingSeconds = ref(0)
let phaseTimer: ReturnType<typeof setInterval> | null = null
let tickTimer: ReturnType<typeof setInterval> | null = null

watch(() => props.loading, (loading) => {
  if (loading) {
    let phaseIndex = 0
    thinkingPhase.value = THINKING_PHASES[0]
    thinkingSeconds.value = 0
    tickTimer = setInterval(() => { thinkingSeconds.value += 1 }, 1000)
    phaseTimer = setInterval(() => {
      phaseIndex = (phaseIndex + 1) % THINKING_PHASES.length
      thinkingPhase.value = THINKING_PHASES[phaseIndex]
    }, 2600)
  } else {
    stopTimers()
  }
}, { immediate: true })

function stopTimers() {
  if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
  if (phaseTimer) { clearInterval(phaseTimer); phaseTimer = null }
}

onBeforeUnmount(stopTimers)

/** 新消息/流式增量时自动滚底；用户主动上翻（距底 >120px）则不打扰。 */
const chatWindow = ref<HTMLDivElement | null>(null)

watch(() => props.messages, () => {
  const el = chatWindow.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  if (nearBottom) {
    requestAnimationFrame(() => {
      el.scrollTop = el.scrollHeight
    })
  }
}, { deep: true })
</script>

<style scoped>
.zxf-suggestions {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.zxf-suggestions button {
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid rgba(31, 41, 51, 0.1);
  border-radius: 999px;
  background: #f7f2e8;
  color: #374151;
  font-size: 12px;
  line-height: 1.35;
  font-weight: 800;
  cursor: pointer;
}

.zxf-chat-window {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  overflow: auto;
  padding: 12px;
  border-radius: 14px;
  background: #f7f2e8;
  border: 1px solid rgba(31, 41, 51, 0.08);
}

.zxf-chat-empty,
.zxf-chat-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #fffdf7;
  color: #6a6c72;
  font-size: 13px;
  line-height: 1.6;
}

.zxf-chat-message {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
}

.zxf-chat-message__role {
  min-height: 28px;
  padding: 5px 8px;
  border-radius: 999px;
  background: #fffdf7;
  color: #6a6c72;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
}

.zxf-chat-message__stack {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.zxf-chat-message__body {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.08);
  box-shadow: 0 4px 14px rgba(31, 41, 51, 0.04);
}

.zxf-steps {
  display: grid;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid rgba(31, 41, 51, 0.08);
  border-radius: 10px;
  background: rgba(255, 253, 247, 0.7);
}

.zxf-steps__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #4b4d54;
}

.zxf-steps__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  flex-shrink: 0;
}

.zxf-steps__mark.is-done {
  background: #eef2ee;
  color: #2f7d5d;
}

.zxf-steps__mark.is-running {
  border: 2px solid rgba(31, 41, 51, 0.14);
  border-top-color: #4b4d54;
  animation: zxf-spin 0.9s linear infinite;
}

@keyframes zxf-spin {
  to { transform: rotate(360deg); }
}

.zxf-thinking-fold {
  border: 1px dashed rgba(31, 41, 51, 0.16);
  border-radius: 10px;
  background: rgba(255, 253, 247, 0.7);
  padding: 6px 10px;
}

.zxf-thinking-fold summary {
  cursor: pointer;
  color: #8e9097;
  font-size: 12px;
  font-weight: 600;
  user-select: none;
}

.zxf-thinking-fold__body {
  max-height: 200px;
  overflow-y: auto;
  margin-top: 6px;
  color: #6a6c72;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.zxf-thinking-live {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6a6c72;
  font-size: 13px;
}

.zxf-thinking-live__dots {
  display: inline-flex;
  gap: 4px;
}

.zxf-thinking-live__dots i {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: #8e9097;
  animation: zxf-bounce 1.2s ease-in-out infinite;
}

.zxf-thinking-live__dots i:nth-child(2) {
  animation-delay: 0.18s;
}

.zxf-thinking-live__dots i:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes zxf-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.5; }
  50% { transform: translateY(-3px); opacity: 1; }
}

.zxf-chat-message.is-user .zxf-chat-message__role {
  background: #1f2933;
  color: #fffdf7;
}

.zxf-chat-message.is-user .zxf-chat-message__body {
  background: #fffaf0;
}

.zxf-chat-form {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
  align-items: stretch;
}

.zxf-chat-form textarea {
  width: 100%;
  min-height: 52px;
  max-height: 140px;
  resize: vertical;
  border: 1px solid rgba(31, 41, 51, 0.14);
  border-radius: 14px;
  padding: 11px 12px;
  background: #fffdf7;
  color: #1f2933;
  font-size: 14px;
  line-height: 1.55;
  outline: none;
  box-sizing: border-box;
  font-family: inherit;
}

.zxf-chat-form textarea:focus {
  border-color: rgba(31, 41, 51, 0.32);
  box-shadow: 0 0 0 3px rgba(31, 41, 51, 0.08);
}

.zxf-chat-form button {
  min-width: 48px;
  min-height: 52px;
  border: none;
  border-radius: 14px;
  background: #1f2933;
  color: #fffdf7;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.zxf-chat-form button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.zxf-chat-error {
  margin-top: 10px;
  padding: 9px 11px;
  border-radius: 12px;
  background: #fff7ed;
  border: 1px solid #fcd9b6;
  color: #7c5f33;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .zxf-chat-message {
    grid-template-columns: 1fr;
  }

  .zxf-chat-message__role {
    width: fit-content;
  }
}
</style>
