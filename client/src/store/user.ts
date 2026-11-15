import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, ApiError, getToken, setToken } from '@/api/client'
import type { AuthUser, Dashboard } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const user = ref<AuthUser | null>(null)
  const dashboard = ref<Dashboard | null>(null)
  const loading = ref(false)
  /** 登录态已失效（服务端明确拒绝），需要用户重新登录 */
  const sessionExpired = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value && user.value))

  async function loadDashboard(): Promise<void> {
    try {
      dashboard.value = await api.dashboard()
    } catch {
      dashboard.value = null
    }
  }

  /**
   * 恢复登录态。
   *
   * 这里踩过一个真实的坑：原实现 catch 到任何异常都 setToken(null)，
   * 于是只要 /user/current 因为**网络问题**失败一次，用户的 token 就被
   * 悄悄清掉、变成游客。表现是「我明明是登录的，刷完题刷新一下记录全没了」
   * ——其实不是记录没了，是这个人已经不被当成登录用户了，
   * 后续答题全走了游客分支（服务端照常返回成绩，但不写库）。
   *
   * 现在按错误类型区分：
   *   - 401：服务端明确说 token 无效/过期 → 清掉，并标记 sessionExpired
   *     供界面提示「登录已过期，请重新登录」，而不是无声无息变游客；
   *   - 其它（网络异常、超时、5xx）→ **保留 token**，重试一次，
   *     仍失败就先按未知处理，绝不因为一次抖动就把人踢下线。
   */
  async function restore(): Promise<void> {
    if (!token.value) return

    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        user.value = await api.currentUser()
        sessionExpired.value = false
        return
      } catch (e) {
        const code = e instanceof ApiError ? e.code : 0

        if (code === 401) {
          // 服务端明确拒绝：token 确实失效了
          setToken(null)
          token.value = null
          user.value = null
          sessionExpired.value = true
          return
        }

        // 网络类错误：等一会儿再试，第二次仍失败就保留 token 继续用
        if (attempt === 0) {
          await new Promise((r) => setTimeout(r, 800))
          continue
        }
        // 保留 token 与本地状态：请求仍会带上 Authorization，
        // 写操作依然按登录用户处理，只是这次没拿到用户信息。
        return
      }
    }
  }

  function login(username: string, password: string): Promise<void> {
    return doAuth(() => api.login(username, password))
  }

  async function doAuth(call: () => Promise<{ token: string; user: AuthUser }>): Promise<void> {
    loading.value = true
    try {
      const result = await call()
      setToken(result.token)
      token.value = result.token
      user.value = result.user
      sessionExpired.value = false
      await loadDashboard()
    } finally {
      loading.value = false
    }
  }

  async function register(
    username: string,
    email: string,
    password: string,
    code: string,
  ): Promise<void> {
    await doAuth(() => api.register(username, email, password, code))
  }

  function logout(): void {
    setToken(null)
    token.value = null
    user.value = null
    sessionExpired.value = false
    void loadDashboard()
  }

  return {
    token,
    user,
    dashboard,
    loading,
    isLoggedIn,
    sessionExpired,
    loadDashboard,
    restore,
    login,
    register,
    logout,
  }
})