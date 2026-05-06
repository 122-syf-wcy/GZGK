import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const identifier = ref(localStorage.getItem('gz_identifier') || '')
  const token = ref(localStorage.getItem('gz_token') || '')
  const remainCount = ref(0)

  function setAuth(id: string, tk: string, count: number) {
    identifier.value = id
    token.value = tk
    remainCount.value = count
    localStorage.setItem('gz_identifier', id)
    localStorage.setItem('gz_token', tk)
  }

  function decrementCount() {
    if (remainCount.value > 0) {
      remainCount.value--
    }
  }

  function clearAuth() {
    identifier.value = ''
    token.value = ''
    remainCount.value = 0
    localStorage.removeItem('gz_identifier')
    localStorage.removeItem('gz_token')
  }

  const isAuthenticated = computed(() => !!token.value)

  return { identifier, token, remainCount, setAuth, decrementCount, clearAuth, isAuthenticated }
})
