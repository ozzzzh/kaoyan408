import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, getToken, setToken } from '@/api/client'
import type { AuthUser, Dashboard } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const user = ref<AuthUser | null>(null)
  const dashboard = ref<Dashboard | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value && user.value))

  async function loadDashboard(): Promise<void> {
    try {
      dashboard.value = await api.dashboard()
    } catch {
      dashboard.value = null
    }
  }

  async function restore(): Promise<void> {
    if (!token.value) return
    try {
      user.value = await api.currentUser()
    } catch {
      // token 失效：清掉本地状态，退回游客模式
      setToken(null)
      token.value = null
      user.value = null
    }
  }

  async function login(username: string, password: string): Promise<void> {
    loading.value = true
    try {
      const result = await api.login(username, password)
      setToken(result.token)
      token.value = result.token
      user.value = result.user
      await loadDashboard()
    } finally {
      loading.value = false
    }
  }

  async function register(username: string, email: string, password: string): Promise<void> {
    loading.value = true
    try {
      const result = await api.register(username, email, password)
      setToken(result.token)
      token.value = result.token
      user.value = result.user
      await loadDashboard()
    } finally {
      loading.value = false
    }
  }

  function logout(): void {
    setToken(null)
    token.value = null
    user.value = null
    void loadDashboard()
  }

  return { token, user, dashboard, loading, isLoggedIn, loadDashboard, restore, login, register, logout }
})