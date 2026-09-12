<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchQaList, submitQuestion, likeQa } from '@/api/qa'
import { getUniversityBySchoolId } from '@/api/university'
import { showToast, showSuccessToast } from 'vant'
import { MessageCircle, ThumbsUp, Send, ChevronDown, ShieldAlert } from 'lucide-vue-next'

const props = defineProps<{ schoolId: string }>()

interface QaItem {
  id: number
  content: string
  authorName: string
  authorType: string
  likeCount: number
  createdAt: string
}
interface QaThread {
  question: QaItem
  answers: QaItem[]
  answerCount: number
  totalPages: number
}

const threads = ref<QaThread[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)

// 提问
const showAskDialog = ref(false)
const askContent = ref('')
const askName = ref('')
const askSubmitting = ref(false)

const qaDisabled = ref(false)
const qaDisabledReason = ref('')
const qaDisabledUntil = ref('')

async function loadQa(reset = false) {
  if (reset) { page.value = 1; threads.value = []; hasMore.value = true }
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const res = await fetchQaList(props.schoolId, page.value, 10)
    const list: QaThread[] = res.data?.data || []
    if (list.length === 0) {
      hasMore.value = false
    } else {
      threads.value.push(...list)
      if (list.length > 0 && list[0].totalPages <= page.value) {
        hasMore.value = false
      }
      page.value++
    }
  } catch { /* silent */ }
  loading.value = false
}

async function doAsk() {
  if (qaDisabled.value) {
    showToast(qaDisabledReason.value || '该学校问答功能暂时关闭')
    return
  }
  if (askContent.value.trim().length < 5) { showToast('问题至少5个字'); return }
  askSubmitting.value = true
  try {
    await submitQuestion({ schoolId: props.schoolId, content: askContent.value, authorName: askName.value || undefined })
    showSuccessToast('提交成功，AI审核后将自动发布或进入校友管理员复核')
    askContent.value = ''
    askName.value = ''
    showAskDialog.value = false
  } catch { showToast('提交失败') }
  askSubmitting.value = false
}

async function doLike(id: number, item: QaItem) {
  try {
    await likeQa(id)
    item.likeCount++
  } catch { /* silent */ }
}

function formatTime(dt: string) {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 2592000000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString()
}

function authorTag(type: string) {
  if (type === 'alumni') return '校友'
  if (type === 'student') return '考生'
  return ''
}

async function loadSchoolQaStatus() {
  try {
    const res = await getUniversityBySchoolId(props.schoolId)
    const uni = res.data?.data
    qaDisabled.value = !!uni?.qaDisabled
    qaDisabledReason.value = uni?.qaDisabledReason || ''
    qaDisabledUntil.value = uni?.qaDisabledUntil || ''
  } catch {
    qaDisabled.value = false
  }
}

onMounted(() => {
  loadQa()
  loadSchoolQaStatus()
})
</script>

<template>
  <div class="qa-section">
    <div class="qa-header">
      <h3 class="qa-title">
        <MessageCircle :size="18" />
        <span>问答专区</span>
        <span v-if="threads.length" class="qa-count">{{ threads.length }}条讨论</span>
      </h3>
      <button class="ask-btn" :disabled="qaDisabled" @click="showAskDialog = true">{{ qaDisabled ? '暂不可提问' : '我要提问' }}</button>
    </div>

    <div v-if="qaDisabled" class="qa-alert">
      <ShieldAlert :size="16" />
      <span>该学校问答功能暂时关闭{{ qaDisabledReason ? `：${qaDisabledReason}` : '' }}<template v-if="qaDisabledUntil">（截止 {{ new Date(qaDisabledUntil).toLocaleString('zh-CN') }}）</template></span>
    </div>

    <!-- 问答列表 -->
    <div v-if="threads.length" class="qa-list">
      <div v-for="t in threads" :key="t.question.id" class="qa-thread">
        <!-- 问题 -->
        <div class="qa-question">
          <div class="qa-meta">
            <span class="qa-author">{{ t.question.authorName }}</span>
            <span v-if="authorTag(t.question.authorType)" class="qa-badge">{{ authorTag(t.question.authorType) }}</span>
            <span class="qa-time">{{ formatTime(t.question.createdAt) }}</span>
          </div>
          <p class="qa-content">{{ t.question.content }}</p>
          <div class="qa-actions">
            <button class="qa-action-btn" @click="doLike(t.question.id, t.question)">
              <ThumbsUp :size="14" /> {{ t.question.likeCount || '' }}
            </button>
            <span class="qa-answer-tip">回复由本校校友管理员维护{{ t.answerCount ? ` · 已有${t.answerCount}条回复` : '' }}</span>
          </div>
        </div>

        <!-- 回答列表 -->
        <div v-if="t.answers.length" class="qa-answers">
          <div v-for="a in t.answers" :key="a.id" class="qa-answer">
            <div class="qa-meta">
              <span class="qa-author">{{ a.authorName }}</span>
              <span v-if="authorTag(a.authorType)" class="qa-badge qa-badge--alumni">{{ authorTag(a.authorType) }}</span>
              <span class="qa-time">{{ formatTime(a.createdAt) }}</span>
            </div>
            <p class="qa-content">{{ a.content }}</p>
            <div class="qa-actions">
              <button class="qa-action-btn" @click="doLike(a.id, a)">
                <ThumbsUp :size="14" /> {{ a.likeCount || '' }}
              </button>
            </div>
          </div>
        </div>

      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!loading" class="qa-empty">
      <p>暂无问答，成为第一个提问者吧</p>
    </div>

    <!-- 加载更多 -->
    <button v-if="hasMore && threads.length" class="qa-load-more" :disabled="loading" @click="loadQa()">
      <ChevronDown :size="16" /> {{ loading ? '加载中...' : '加载更多' }}
    </button>

    <!-- 提问弹窗 -->
    <van-overlay :show="showAskDialog" @click="showAskDialog = false">
      <div class="qa-dialog" @click.stop>
        <h4 class="qa-dialog-title">提个问题</h4>
        <input v-model="askName" placeholder="你的昵称（选填）" class="qa-input qa-input--name" />
        <textarea v-model="askContent" placeholder="关于这所大学，你想了解什么？" class="qa-input qa-input--text" rows="4" maxlength="500" />
        <div class="qa-dialog-hint">审核通过后将公开展示，请勿发布违规内容</div>
        <div class="qa-dialog-actions">
          <button class="qa-cancel-btn" @click="showAskDialog = false">取消</button>
          <button class="qa-submit-btn" :disabled="askSubmitting" @click="doAsk">
            <Send :size="14" /> {{ askSubmitting ? '提交中...' : '提交问题' }}
          </button>
        </div>
      </div>
    </van-overlay>
  </div>
</template>

<style scoped>
.qa-section {
  margin-top: 16px;
}

.qa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.qa-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 700;
  color: #17181c;
}

.qa-count {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
}

.ask-btn {
  padding: 6px 14px;
  border: 1px solid #17181c;
  border-radius: 20px;
  background: #fff;
  color: #17181c;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.ask-btn:active {
  background: #17181c;
  color: #fff;
}

.ask-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  color: #97999e;
  border-color: #cdccc7;
}

.qa-alert {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  border-radius: 10px;
  background: #faf7ef;
  border: 1px solid #e6dcbd;
  color: #7c5f33;
  font-size: 12px;
  line-height: 1.6;
}

.qa-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.qa-thread {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f3f4f6;
  overflow: hidden;
}

.qa-question {
  padding: 14px 16px;
}

.qa-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.qa-author {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.qa-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  background: #f4f4f2;
  color: #17181c;
}

.qa-badge--alumni {
  background: #f0fdf4;
  color: #16a34a;
}

.qa-time {
  font-size: 12px;
  color: #9ca3af;
  margin-left: auto;
}

.qa-content {
  font-size: 14px;
  line-height: 1.6;
  color: #1f2937;
  margin: 0;
  word-break: break-word;
}

.qa-actions {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.qa-action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.15s;
}

.qa-action-btn:active {
  color: #17181c;
}

.qa-answer-tip {
  font-size: 12px;
  color: #97999e;
}

.qa-answers {
  border-top: 1px solid #f3f4f6;
  background: #fafbfc;
  padding: 0 16px;
}

.qa-answer {
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}

.qa-answer:last-child {
  border-bottom: none;
}

.qa-reply-box {
  padding: 12px 16px;
  border-top: 1px solid #f3f4f6;
  background: #f9fafb;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qa-input {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  color: #17181c;
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.qa-input:focus {
  border-color: #17181c;
}

.qa-input--name {
  max-width: 200px;
}

.qa-input--text {
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
}

.qa-reply-actions, .qa-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.qa-cancel-btn {
  padding: 6px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
}

.qa-submit-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: #17181c;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.qa-submit-btn:disabled {
  opacity: 0.5;
}

.qa-empty {
  text-align: center;
  padding: 32px 16px;
  color: #9ca3af;
  font-size: 14px;
}

.qa-load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  padding: 10px;
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  margin-top: 8px;
}

/* ── 提问弹窗 ── */
.qa-dialog {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  border-radius: 16px 16px 0 0;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 70vh;
  overflow-y: auto;
}

.qa-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: #17181c;
  margin: 0;
}

.qa-dialog-hint {
  font-size: 12px;
  color: #9ca3af;
}

@media (min-width: 1024px) {
  .qa-dialog {
    position: fixed;
    bottom: auto;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    max-width: 500px;
    border-radius: 16px;
  }
}
</style>
