<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const { user, loading } = storeToRefs(userStore)

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const email = ref('')
const password = ref('')
const error = ref<string | null>(null)

async function submit() {
  error.value = null
  if (!username.value || !password.value) {
    error.value = '请填写用户名与密码。'
    return
  }
  try {
    if (mode.value === 'login') {
      await userStore.login(username.value, password.value)
    } else {
      await userStore.register(username.value, email.value, password.value)
    }
    password.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  }
}
</script>

<template>
  <div class="mx-auto max-w-md space-y-6">
    <header class="space-y-1 text-center">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">我的</h1>
      <p class="text-sm text-[var(--k-text-muted)]">登录后做题记录、掌握度与打卡才会保存。</p>
    </header>

    <section v-if="user" class="space-y-4 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-6">
      <div class="flex items-center gap-3">
        <span
          class="grid h-12 w-12 place-items-center rounded-xl bg-[var(--k-accent)] text-lg font-bold text-white"
          >{{ user.username.slice(0, 1).toUpperCase() }}</span
        >
        <div class="min-w-0">
          <p class="text-lg font-semibold text-[var(--k-text)]">{{ user.username }}</p>
          <p v-if="user.email" class="truncate text-xs text-[var(--k-text-faint)]">{{ user.email }}</p>
          <p v-else class="text-xs text-[var(--k-text-faint)]">未绑定邮箱</p>
        </div>
      </div>
      <button
        class="w-full rounded-lg border border-[var(--k-border)] px-4 py-2 text-sm text-[var(--k-danger)] transition hover:bg-[var(--k-warn-soft)]"
        @click="userStore.logout()"
      >
        退出登录
      </button>
    </section>

    <section v-else class="space-y-4 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-6">
      <div class="flex gap-2 rounded-lg bg-[var(--k-chip)] p-1 text-sm">
        <button
          class="flex-1 rounded-md py-1.5 transition"
          :class="mode === 'login' ? 'bg-[var(--k-surface)] font-semibold text-[var(--k-accent-text)] shadow-sm' : 'text-[var(--k-text-muted)]'"
          @click="mode = 'login'"
        >
          登录
        </button>
        <button
          class="flex-1 rounded-md py-1.5 transition"
          :class="mode === 'register' ? 'bg-[var(--k-surface)] font-semibold text-[var(--k-accent-text)] shadow-sm' : 'text-[var(--k-text-muted)]'"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <label class="block text-sm">
        <span class="text-[var(--k-text-muted)]">用户名</span>
        <input
          v-model="username"
          class="mt-1 w-full rounded-lg border border-[var(--k-border)] px-3 py-2 outline-none focus:border-[var(--k-accent)]"
          placeholder="3-32 位"
        />
      </label>

      <label v-if="mode === 'register'" class="block text-sm">
        <span class="text-[var(--k-text-muted)]">邮箱（可选）</span>
        <input
          v-model="email"
          class="mt-1 w-full rounded-lg border border-[var(--k-border)] px-3 py-2 outline-none focus:border-[var(--k-accent)]"
          placeholder="you@example.com"
        />
      </label>

      <label class="block text-sm">
        <span class="text-[var(--k-text-muted)]">密码</span>
        <input
          v-model="password"
          type="password"
          class="mt-1 w-full rounded-lg border border-[var(--k-border)] px-3 py-2 outline-none focus:border-[var(--k-accent)]"
          placeholder="至少 6 位"
          @keyup.enter="submit"
        />
      </label>

      <p v-if="error" class="rounded-lg border border-[var(--k-danger)]/30 bg-[var(--k-warn-soft)] px-3 py-2 text-sm text-[var(--k-danger)]">
        {{ error }}
      </p>

      <button
        class="w-full rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
        :disabled="loading"
        @click="submit"
      >
        {{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>

      <p class="text-center text-xs text-[var(--k-text-faint)]">本地开发默认账号库为空，先注册一个即可。</p>
    </section>
  </div>
</template>