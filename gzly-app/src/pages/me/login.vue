<script setup lang="ts">
import { computed, ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import GzButton from '@/components/GzButton.vue'
import GzField from '@/components/GzField.vue'
import GzNavBar from '@/components/GzNavBar.vue'
import { sendEmailCode } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { usePlanStore } from '@/stores/plan'

const auth = useAuthStore()
const planStore = usePlanStore()

const email = ref('')
const code = ref('')
const sending = ref(false)
const submitting = ref(false)
const errorText = ref('')

const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
const emailValid = computed(() => EMAIL_RE.test(email.value.trim()))
const canSubmit = computed(() => emailValid.value && /^\d{6}$/.test(code.value.trim()))

// ---- 验证码倒计时 ----
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

function startCountdown(seconds: number) {
  countdown.value = seconds
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) clearInterval(timer)
  }, 1000)
}

async function onSendCode() {
  if (!emailValid.value || countdown.value > 0 || sending.value) return
  sending.value = true
  errorText.value = ''
  try {
    const res = await sendEmailCode(email.value.trim())
    startCountdown(res.cooldownSeconds || 60)
    uni.showToast({ title: '验证码已发送，请查收邮箱', icon: 'none' })
  } catch (e) {
    errorText.value = (e as Error).message || '发送失败'
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  errorText.value = ''
  try {
    const { newUser } = await auth.login(email.value.trim(), code.value.trim())

    // 登录后把本机匿名方案绑定到账号，跨设备可找回
    let claimed = 0
    if (planStore.archive.length) {
      uni.showLoading({ title: '正在同步本机方案', mask: true })
      claimed = await auth.claimArchive(planStore.archive)
      uni.hideLoading()
    }

    const parts = [newUser ? '账号已创建' : '欢迎回来']
    if (claimed > 0) parts.push(`${claimed} 份方案已同步到账号`)
    uni.showToast({ title: parts.join('，'), icon: 'none', duration: 2400 })
    setTimeout(() => {
      // 直接进入本页（deeplink/刷新）时没有页面栈可退，回落到「我的」tab
      if (getCurrentPages().length > 1) uni.navigateBack()
      else uni.switchTab({ url: '/pages/me/index' })
    }, 800)
  } catch (e) {
    errorText.value = (e as Error).message || '登录失败'
  } finally {
    submitting.value = false
  }
}

onUnload(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <view class="page">
    <GzNavBar title="登录 / 注册" />

    <view class="pad">
      <view class="head">
        <text class="head__title">登录同步方案</text>
        <text class="head__desc">
          邮箱验证码登录，未注册的邮箱会自动创建账号。登录后本机方案自动绑定到账号，换机也能找回。
        </text>
      </view>

      <view class="form">
        <GzField
          v-model="email"
          label="邮箱"
          placeholder="you@example.com"
          :maxlength="128"
          :error="email && !emailValid ? '邮箱格式不正确' : ''"
        />

        <view class="code">
          <view class="code__field">
            <GzField
              v-model="code"
              label="验证码"
              type="number"
              placeholder="6 位数字"
              :maxlength="6"
              :error="errorText"
              hint="验证码 5 分钟内有效"
            />
          </view>
          <view
            class="code__send"
            :class="{ 'code__send--disabled': !emailValid || countdown > 0 || sending }"
            @tap="onSendCode"
          >
            <text class="code__send-text">
              {{ countdown > 0 ? `${countdown}s 后重发` : sending ? '发送中…' : '获取验证码' }}
            </text>
          </view>
        </view>

        <GzButton block :disabled="!canSubmit" :loading="submitting" class="submit" @tap="onSubmit">
          登录 / 注册
        </GzButton>
      </view>

      <view class="notes">
        <text class="notes__item">· 邮箱只用于登录与方案同步，不会对外展示，也不会用于任何推送。</text>
        <text class="notes__item">· 不登录也可以正常使用全部功能，方案凭安全码保存在本机。</text>
        <text class="notes__item">· 可随时在「我的」中退出登录或注销账号，注销后账号信息立即匿名化。</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--gz-bg);
  padding-bottom: calc(80rpx + env(safe-area-inset-bottom));
}

.pad {
  padding: 0 $gz-page-x;
}

.head {
  padding: $gz-space-3 0 $gz-space-5;

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

.form {
  @include gz-card;
  padding: $gz-space-4 $gz-space-3;
}

.code {
  display: flex;
  align-items: flex-start;
  margin-top: $gz-space-3;

  &__field {
    flex: 1;
    min-width: 0;
  }

  &__send {
    flex-shrink: 0;
    margin-left: $gz-space-2;
    /* 与 GzField 的 label(高约 46rpx) 对齐后垂直居中输入框 */
    margin-top: 46rpx;
    height: 96rpx;
    padding: 0 28rpx;
    display: flex;
    align-items: center;
    border-radius: var(--gz-radius-md);
    background: var(--gz-ink);
    transition: opacity 0.15s ease, transform 0.12s ease;

    &:active {
      transform: scale(0.97);
    }

    &--disabled {
      opacity: 0.4;
    }
  }

  &__send-text {
    font-size: $gz-text-sm;
    color: var(--gz-text-inverse);
    white-space: nowrap;
  }
}

.submit {
  margin-top: $gz-space-5;
}

.notes {
  margin-top: $gz-space-4;

  &__item {
    display: block;
    font-size: 20rpx;
    color: var(--gz-text-3);
    line-height: 1.9;
  }
}
</style>
