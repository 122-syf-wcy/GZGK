import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 用户安全码会话登录的全局认证状态。
 *
 * 与 stores/user.ts 区分：user.ts 是早期 identifier+次数模型的兼容层，
 * 这里是 B1+B2 引入的新链路，token 单独存 gz_user_token 避免与 admin 复用。
 */
export interface AuthUserSnapshot {
  userId?: number | null
  identifier?: string
  nickname?: string
  maxPlans?: number
  usedPlans?: number
  remainPlans?: number
  expiresAt?: string | null
}

const TOKEN_KEY = 'gz_user_token'
const PROFILE_KEY = 'gz_user_profile'

function readProfile(): AuthUserSnapshot {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    return raw ? (JSON.parse(raw) as AuthUserSnapshot) : {}
  } catch {
    return {}
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const profile = ref<AuthUserSnapshot>(readProfile())

  const isAuthenticated = computed(() => !!token.value)
  const remainPlans = computed(() => profile.value.remainPlans ?? 0)

  function setSession(t: string, snapshot: AuthUserSnapshot) {
    token.value = t
    profile.value = snapshot
    if (t) localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(PROFILE_KEY, JSON.stringify(snapshot))
  }

  function updateProfile(partial: Partial<AuthUserSnapshot>) {
    profile.value = { ...profile.value, ...partial }
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile.value))
  }

  function logout() {
    token.value = ''
    profile.value = {}
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  }

  return { token, profile, isAuthenticated, remainPlans, setSession, updateProfile, logout }
})
