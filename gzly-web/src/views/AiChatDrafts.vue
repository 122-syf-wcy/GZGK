<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { ArrowLeft, KeyRound, ShieldAlert } from 'lucide-vue-next'
import { restoreAiQaSession } from '@/api/aiQa'

defineOptions({ name: 'AiChatDrafts' })

const ACTIVE_KEY = 'gz_ai_qa_active'
const router = useRouter()

const code = ref('')
const restoring = ref(false)
const errorMsg = ref('')

async function onRestore(): Promise<void> {
  const value = code.value.trim()
  if (!value) {
    showToast('请输入对话码')
    return
  }
  restoring.value = true
  errorMsg.value = ''
  try {
    const result = await restoreAiQaSession(value)
    try {
      sessionStorage.setItem(ACTIVE_KEY, JSON.stringify({
        sessionUid: result.session.sessionUid,
        code: value,
      }))
    } catch {
      // 忽略存储异常，AiChat 页仍会用内存中的会话
    }
    router.push('/ai-chat')
  } catch (error: unknown) {
    errorMsg.value = error instanceof Error ? error.message : '找回失败，请确认对话码是否正确'
  } finally {
    restoring.value = false
  }
}
</script>

<template>
  <div class="aiqa-drafts gz-shell-page">
    <header class="aiqa-header">
      <button class="aiqa-header__back" type="button" aria-label="返回" @click="router.push('/ai-chat')">
        <ArrowLeft :size="18" />
      </button>
      <div class="aiqa-header__title">
        <KeyRound :size="16" />
        找回 AI 问答对话
      </div>
      <span class="aiqa-header__placeholder" />
    </header>

    <main class="page-container aiqa-drafts__main">
      <section class="gz-card aiqa-drafts__card">
        <h2 class="aiqa-drafts__title">输入对话码，找回历史对话</h2>
        <p class="aiqa-drafts__hint">
          对话码在创建会话时展示，仅展示一次。我们只保存它的不可逆校验信息，无法帮你找回原始对话码，请妥善保管。
        </p>

        <label class="aiqa-drafts__field">
          <span>对话码</span>
          <input
            v-model="code"
            type="text"
            maxlength="24"
            autocomplete="off"
            placeholder="请输入 8-24 位对话码"
            @keydown.enter.prevent="onRestore"
          />
        </label>

        <p v-if="errorMsg" class="aiqa-drafts__error">{{ errorMsg }}</p>

        <button class="aiqa-drafts__btn" type="button" :disabled="restoring" @click="onRestore">
          {{ restoring ? '找回中…' : '找回对话' }}
        </button>
        <button class="aiqa-drafts__text" type="button" @click="router.push('/ai-chat')">
          没有对话码？去创建新的 AI 问答会话
        </button>
      </section>

      <section class="gz-card aiqa-drafts__notice">
        <ShieldAlert :size="16" />
        <p>多次输入错误的对话码会被临时限制访问，请确认后再试。本功能仅提供方向参考，最终以官方信息为准。</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.aiqa-drafts {
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

.aiqa-header__placeholder {
  width: 36px;
}

.aiqa-drafts__main {
  padding-top: 16px;
  padding-bottom: 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aiqa-drafts__title {
  margin: 0;
  font-size: 18px;
  font-weight: 850;
  color: #0f172a;
}

.aiqa-drafts__hint {
  margin: 10px 0 18px;
  font-size: 13px;
  line-height: 1.75;
  color: #64748b;
}

.aiqa-drafts__field > span {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 800;
  color: #334155;
}

.aiqa-drafts__field input {
  width: 100%;
  min-height: 48px;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: 12px;
  background: #fff;
  font-size: 16px;
  letter-spacing: 0.12em;
  color: #0f172a;
  outline: none;
  box-sizing: border-box;
  text-transform: uppercase;
}

.aiqa-drafts__field input:focus {
  border-color: rgba(29, 78, 216, 0.4);
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.08);
}

.aiqa-drafts__error {
  margin: 12px 0 0;
  padding: 9px 11px;
  border-radius: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  font-size: 13px;
  line-height: 1.6;
}

.aiqa-drafts__btn {
  width: 100%;
  min-height: 48px;
  margin-top: 18px;
  border: none;
  border-radius: 14px;
  background: #0f172a;
  color: #fff;
  font-size: 15px;
  font-weight: 850;
}

.aiqa-drafts__btn:disabled {
  opacity: 0.5;
}

.aiqa-drafts__text {
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

.aiqa-drafts__notice {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
}

.aiqa-drafts__notice p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}
</style>
