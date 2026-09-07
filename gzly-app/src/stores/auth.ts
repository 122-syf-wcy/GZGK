/**
 * 登录会话状态。
 *
 * 账号是**可选增强**而不是使用门槛：匿名生成 + 安全码是产品底色，
 * 登录的价值是把本机方案绑定到账号、跨设备找回。所以这里绝不阻塞任何主流程。
 *
 * token 是 7 天有效的无状态 JWT；请求层在收到 401 时会广播
 * `gzly:unauthorized`，本 store 负责监听并清理本地会话。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { STORAGE_KEYS } from '@/constants/config'
import { claimPlan, loginWithEmail } from '@/api/auth'
import type { ArchivedPlan } from '@/types'

export interface Session {
  token: string
  userId: number
  email: string
  nickname: string
}

function readSession(): Session | null {
  try {
    const raw = uni.getStorageSync(STORAGE_KEYS.session)
    if (raw && typeof raw === 'object' && raw.token) return raw as Session
  } catch {
    // 读失败视为未登录
  }
  return null
}

export const useAuthStore = defineStore('auth', () => {
  const session = ref<Session | null>(readSession())

  const isLoggedIn = computed(() => !!session.value?.token)
  const nickname = computed(() => session.value?.nickname || '')
  const email = computed(() => session.value?.email || '')

  // 请求层广播 401 时清会话（token 过期 / 注销后残留）
  uni.$on('gzly:unauthorized', () => {
    clearSession()
  })

  function persist() {
    try {
      if (session.value) uni.setStorageSync(STORAGE_KEYS.session, session.value)
      else uni.removeStorageSync(STORAGE_KEYS.session)
    } catch {
      // 写失败只影响下次启动的登录态
    }
  }

  async function login(emailInput: string, code: string): Promise<{ newUser: boolean }> {
    const result = await loginWithEmail(emailInput, code)
    session.value = {
      token: result.token,
      userId: result.userId,
      email: result.email,
      nickname: result.nickname,
    }
    persist()
    return { newUser: !!result.newUser }
  }

  /**
   * 把本机存档的匿名方案逐份绑定到当前账号。
   * 失败的跳过（可能已被其他账号绑定），不阻断登录流程。
   */
  async function claimArchive(archive: ArchivedPlan[]): Promise<number> {
    let claimed = 0
    for (const plan of archive) {
      try {
        const res = await claimPlan(plan.planId, plan.safetyCode || plan.accessKey)
        if (res.claimed || res.alreadyOwned) claimed += 1
      } catch {
        // 单份失败不影响其余
      }
    }
    return claimed
  }

  function clearSession() {
    session.value = null
    persist()
  }

  return { session, isLoggedIn, nickname, email, login, claimArchive, clearSession }
})
