<template>
  <div class="zxf-suggestions" aria-label="常用追问">
    <button v-for="suggestion in suggestions" :key="suggestion" type="button" @click="$emit('pick', suggestion)">
      {{ suggestion }}
    </button>
  </div>

  <div class="zxf-chat-window" aria-live="polite">
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
      <div class="markdown-body zxf-chat-message__body" v-html="render(message.content)"></div>
    </div>
    <div v-if="loading" class="zxf-chat-loading">
      <span class="status-dot status-dot--active"></span>
      正在读取 skills 并结合当前方案分析...
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
import { Bot, SendHorizonal } from 'lucide-vue-next'
import type { ZxfSkillChatMessage } from '@/api/volunteer'

defineProps<{
  modelValue: string
  suggestions: string[]
  messages: ZxfSkillChatMessage[]
  loading: boolean
  error: string
  render: (markdown: string) => string
}>()

defineEmits<{
  'update:modelValue': [value: string]
  pick: [value: string]
  send: []
}>()
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
  color: #64748b;
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
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
}

.zxf-chat-message__body {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fffdf7;
  border: 1px solid rgba(31, 41, 51, 0.08);
  box-shadow: 0 4px 14px rgba(31, 41, 51, 0.04);
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
  color: #92400e;
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
