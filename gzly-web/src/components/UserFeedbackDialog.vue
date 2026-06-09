<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { submitFeedback } from '@/api/feedback'
import { showSuccessToast, showToast } from 'vant'
import { MessageSquare, Send, X } from 'lucide-vue-next'

defineOptions({ name: 'UserFeedbackDialog' })

const props = withDefaults(defineProps<{
  provinceCode?: string
  resultId?: number | string | null
  buttonLabel?: string
  compact?: boolean
}>(), {
  provinceCode: '',
  resultId: null,
  buttonLabel: '结果有问题？反馈一下',
  compact: false,
})

const route = useRoute()
const showPopup = ref(false)
const submitting = ref(false)
const feedbackType = ref('数据疑问')
const description = ref('')
const contact = ref('')

const feedbackTypes = ['生成失败', '数据疑问', '院校专业问题', '页面问题', '其他']
const descriptionLength = computed(() => description.value.trim().length)
const sourcePath = computed(() => route.fullPath || route.path || '/')

function resetForm() {
  feedbackType.value = '数据疑问'
  description.value = ''
  contact.value = ''
}

function buildContent() {
  const lines = [
    `问题类型：${feedbackType.value}`,
    `问题描述：${description.value.trim()}`,
    contact.value.trim() ? `联系方式：${contact.value.trim()}` : '联系方式：未填写',
    props.provinceCode ? `省份：${props.provinceCode}` : '',
    props.resultId ? `方案编号：${props.resultId}` : '',
    `页面：${sourcePath.value}`,
  ].filter(Boolean)
  return lines.join('\n').slice(0, 500)
}

async function submit() {
  const body = description.value.trim()
  if (body.length < 10 || body.length > 320) {
    showToast('问题描述需控制在 10-320 字')
    return
  }
  submitting.value = true
  try {
    await submitFeedback({
      content: buildContent(),
      sourcePage: sourcePath.value.slice(0, 120),
    })
    showSuccessToast('反馈已收到')
    showPopup.value = false
    resetForm()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '提交失败，请稍后重试'
    showToast(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <button
    type="button"
    class="feedback-trigger"
    :class="{ 'feedback-trigger--compact': compact }"
    @click="showPopup = true"
  >
    <MessageSquare :size="15" />
    <span>{{ buttonLabel }}</span>
  </button>

  <van-popup
    v-model:show="showPopup"
    position="bottom"
    round
    :close-on-click-overlay="!submitting"
    :style="{ maxHeight: '86%', background: '#fffdfa' }"
    @closed="resetForm"
  >
    <div class="feedback-panel">
      <header class="feedback-panel__head">
        <div>
          <span class="feedback-panel__kicker">feedback</span>
          <h3>问题反馈</h3>
          <p>页面、数据或生成结果有疑问，都可以直接告诉我们。</p>
        </div>
        <button class="feedback-panel__close" type="button" :disabled="submitting" @click="showPopup = false">
          <X :size="18" />
        </button>
      </header>

      <div class="feedback-type-grid" aria-label="问题类型">
        <button
          v-for="type in feedbackTypes"
          :key="type"
          type="button"
          class="feedback-type"
          :class="{ active: feedbackType === type }"
          @click="feedbackType = type"
        >
          {{ type }}
        </button>
      </div>

      <label class="feedback-field">
        <span>问题描述</span>
        <textarea
          v-model="description"
          maxlength="320"
          placeholder="例如：某个院校代码缺失、筛选后结果不符合预期、页面按钮点不动。"
        />
      </label>

      <label class="feedback-field">
        <span>联系方式（选填）</span>
        <input v-model.trim="contact" maxlength="80" placeholder="微信 / 手机 / 邮箱，方便需要时联系你" />
      </label>

      <div class="feedback-context">
        <span v-if="provinceCode">省份 {{ provinceCode }}</span>
        <span v-if="resultId">方案 {{ resultId }}</span>
        <span>{{ sourcePath }}</span>
      </div>

      <div class="feedback-panel__actions">
        <span :class="{ 'is-danger': descriptionLength > 0 && descriptionLength < 10 }">{{ descriptionLength }}/320</span>
        <button type="button" :disabled="submitting" @click="submit">
          <Send :size="14" />
          {{ submitting ? '提交中...' : '提交反馈' }}
        </button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.feedback-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 800;
}

.feedback-trigger--compact {
  min-height: 32px;
  padding: 0 10px;
  font-size: 12px;
}

.feedback-panel {
  padding: 20px 18px calc(env(safe-area-inset-bottom, 0px) + 18px);
}

.feedback-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.feedback-panel__kicker {
  color: #2563eb;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.feedback-panel h3 {
  margin: 5px 0 0;
  color: #0f172a;
  font-size: 20px;
  font-weight: 900;
}

.feedback-panel p {
  margin: 7px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.feedback-panel__close {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  flex-shrink: 0;
}

.feedback-type-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

.feedback-type {
  min-height: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.feedback-type.active {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}

.feedback-field {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.feedback-field span {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.feedback-field textarea,
.feedback-field input {
  width: 100%;
  border: 1px solid #dbe2ea;
  border-radius: 12px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  outline: none;
}

.feedback-field textarea {
  min-height: 118px;
  padding: 12px;
  line-height: 1.6;
  resize: vertical;
}

.feedback-field input {
  height: 42px;
  padding: 0 12px;
}

.feedback-context {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.feedback-context span {
  max-width: 100%;
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-panel__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 16px;
  color: #94a3b8;
  font-size: 12px;
}

.feedback-panel__actions .is-danger {
  color: #dc2626;
}

.feedback-panel__actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 128px;
  min-height: 42px;
  border: 0;
  border-radius: 12px;
  background: #0f172a;
  color: #fff;
  font-size: 14px;
  font-weight: 900;
}

@media (min-width: 560px) {
  .feedback-panel {
    max-width: 560px;
    margin: 0 auto;
  }

  .feedback-type-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
</style>
