<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { alumniLogin } from '@/api/alumni'
import { showToast } from 'vant'
import { ArrowLeft, LogIn } from 'lucide-vue-next'

const router = useRouter()
const phone = ref('')
const password = ref('')
const loading = ref(false)

async function login() {
  if (!phone.value || !password.value) return
  loading.value = true
  try {
    const res = await alumniLogin(phone.value, password.value)
    if (res.data?.data?.admin) {
      const admin = res.data.data.admin
      const token = res.data.data.token
      localStorage.setItem('alumni_admin', JSON.stringify(admin))
      localStorage.setItem('alumni_token', token)
      if (admin.role === 9) {
        router.push('/admin/alumni-review')
      } else {
        router.push('/alumni/manage')
      }
    }
  } catch (e: any) {
    showToast(e.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <header class="login-header">
      <button class="back-btn" @click="router.back()"><ArrowLeft :size="20" /></button>
      <h1>管理员登录</h1>
    </header>

    <div class="login-body">
      <div class="login-icon"><LogIn :size="32" /></div>
      <van-form @submit="login">
        <van-cell-group inset>
          <van-field v-model="phone" label="手机号" type="tel" placeholder="输入手机号" />
          <van-field v-model="password" label="密码" type="password" placeholder="输入密码" />
        </van-cell-group>
        <div style="padding: 24px 16px;">
          <van-button round block type="primary" :loading="loading" native-type="submit">登录</van-button>
          <p class="login-link" @click="router.push('/alumni/apply')">还没有账号？申请成为维护员</p>
        </div>
      </van-form>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100dvh; background: var(--gz-bg); }
.login-header { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: linear-gradient(135deg, #1e3a8a, #17181c); color: #fff; }
.login-header h1 { font-size: 17px; font-weight: 700; }
.back-btn { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border: none; border-radius: 8px; background: rgba(255,255,255,0.15); color: #fff; cursor: pointer; }
.login-body { max-width: 440px; margin: 0 auto; padding-top: 48px; }
.login-icon { display: flex; align-items: center; justify-content: center; width: 64px; height: 64px; border-radius: 20px; background: linear-gradient(135deg, #17181c, #2b2d33); color: #fff; margin: 0 auto 24px; }
.login-link { text-align: center; font-size: 13px; color: var(--gz-primary); margin-top: 16px; cursor: pointer; }

@media (min-width: 768px) {
  .login-header { padding: 16px 48px; }
  .login-header h1 { font-size: 20px; }
  .login-body { max-width: 480px; padding-top: 80px; }
  .login-icon { width: 80px; height: 80px; border-radius: 24px; margin-bottom: 32px; }
  .login-body :deep(.van-cell-group--inset) { border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }
}
</style>
