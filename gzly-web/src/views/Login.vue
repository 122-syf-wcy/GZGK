<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { ArrowLeft, MailCheck } from 'lucide-vue-next'
import { emailLogin, sendEmailCode } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

defineOptions({ name: 'Login' })

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const email = ref('')
const code = ref('')
const sending = ref(false)
const submitting = ref(false)
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

const emailValid = computed(() => /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email.value.trim()))
const canSend = computed(() => emailValid.value && cooldown.value <= 0 && !sending.value)
const canSubmit = computed(() => emailValid.value && /^\d{6}$/.test(code.value.trim()) && !submitting.value)

function startCooldown(seconds: number) {
  cooldown.value = seconds
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

async function onSendCode() {
  if (!canSend.value) return
  sending.value = true
  try {
    const res = await sendEmailCode(email.value.trim())
    showSuccessToast('验证码已发送，请查收邮箱')
    startCooldown(res.data.data?.cooldownSeconds || 60)
  } catch (e: any) {
    showToast(e?.message || '发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const res = await emailLogin(email.value.trim(), code.value.trim())
    const data = res.data.data
    authStore.setSession(data.token, {
      userId: data.userId,
      identifier: data.email,
      nickname: data.nickname,
    })
    showSuccessToast(data.newUser ? '注册成功，欢迎！' : '登录成功')
    const redirect = String(route.query.redirect || '/my-plans')
    router.replace(redirect.startsWith('/') ? redirect : '/my-plans')
  } catch (e: any) {
    showToast(e?.message || '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

onBeforeUnmount(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<template>
  <div class="login-page">
    <header class="login-header">
      <button class="login-back" type="button" aria-label="返回" @click="goBack">
        <ArrowLeft :size="18" />
      </button>
    </header>

    <main class="login-main">
      <div class="login-card">
        <span class="login-icon"><MailCheck :size="26" /></span>
        <h1 class="login-title">邮箱登录</h1>
        <p class="login-desc">
          输入邮箱获取验证码即可登录；首次登录自动创建账号，你的志愿方案将在多设备间同步。
        </p>

        <label class="login-field">
          <span>邮箱</span>
          <input
            v-model.trim="email"
            type="email"
            inputmode="email"
            placeholder="example@qq.com"
            autocomplete="email"
            :disabled="submitting"
          />
        </label>

        <label class="login-field">
          <span>验证码</span>
          <div class="login-code-row">
            <input
              v-model.trim="code"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="6 位验证码"
              autocomplete="one-time-code"
              :disabled="submitting"
              @keydown.enter="onSubmit"
            />
            <button type="button" class="login-send" :disabled="!canSend" @click="onSendCode">
              {{ cooldown > 0 ? `${cooldown}s 后重发` : sending ? '发送中…' : '获取验证码' }}
            </button>
          </div>
        </label>

        <button type="button" class="login-submit" :disabled="!canSubmit" @click="onSubmit">
          {{ submitting ? '登录中…' : '登录 / 注册' }}
        </button>

        <p class="login-hint">
          无需密码，验证码即登录。登录即表示同意
          <a @click.prevent="router.push('/disclaimer')">《免责声明》</a>。
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100dvh;
  background: var(--gz-bg);
  display: flex;
  flex-direction: column;
}

.login-header {
  padding: 14px 16px;
}

.login-back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(23, 24, 28, 0.12);
  border-radius: 999px;
  background: #fff;
  color: #17181c;
  cursor: pointer;
}

.login-main {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 8vh 16px 40px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 28px 24px;
  border: 1px solid rgba(23, 24, 28, 0.1);
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(23, 24, 28, 0.04);
  display: grid;
  gap: 14px;
}

.login-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 999px;
  background: #17181c;
  color: #fff;
}

.login-title {
  font-family: var(--gz-font-display);
  font-size: 24px;
  font-weight: 700;
  color: #17181c;
}

.login-desc {
  font-size: 13px;
  color: #4b4d54;
  line-height: 1.7;
}

.login-field {
  display: grid;
  gap: 6px;
}

.login-field > span {
  font-size: 12px;
  font-weight: 700;
  color: #6a6c72;
}

.login-field input {
  height: 46px;
  padding: 0 14px;
  border: 1px solid rgba(23, 24, 28, 0.14);
  border-radius: 12px;
  background: var(--gz-bg-subtle, #fbfaf8);
  font-size: 15px;
  color: #17181c;
  outline: none;
  width: 100%;
}

.login-field input:focus {
  border-color: rgba(23, 24, 28, 0.45);
}

.login-code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 8px;
}

.login-send {
  border: 1px solid rgba(23, 24, 28, 0.16);
  border-radius: 12px;
  background: #fff;
  color: #17181c;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.login-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-submit {
  margin-top: 4px;
  min-height: 48px;
  border: none;
  border-radius: 999px;
  background: #17181c;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}

.login-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.login-hint {
  font-size: 12px;
  color: #8e9097;
  text-align: center;
  line-height: 1.6;
}

.login-hint a {
  color: #17181c;
  text-decoration: underline;
  cursor: pointer;
}
</style>
