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
      <h1 class="text-2xl font-bold tracking-tight text-[#0b1f45]">我的</h1>
      <p class="text-sm text-[#5c6f94]">登录后做题记录、掌握度与打卡才会保存。</p>
    </header>

    <section v-if="user" class="space-y-4 rounded-xl border border-[#e2e9f5] bg-white p-6">
      <div class="flex items-center gap-3">
        <span
          class="grid h-12 w-12 place-items-center rounded-xl bg-[#1f49ba] text-lg font-bold text-white"
          >{{ user.username.slice(0, 1).toUpperCase() }}</span
        >
        <div class="min-w-0">
          <p class="text-lg font-semibold text-[#0b1f45]">{{ user.username }}</p>
          <p v-if="user.email" class="truncate text-xs text-[#9aa9c3]">{{ user.email }}</p>
          <p v-else class="text-xs text-[#9aa9c3]">未绑定邮箱</p>
        </div>
      </div>
      <button
        class="w-full rounded-lg border border-[#e2e9f5] px-4 py-2 text-sm text-[#c0392b] transition hover:bg-[#fff5f5]"
        @click="userStore.logout()"
      >
        退出登录
      </button>
    </section>

    <section v-else class="space-y-4 rounded-xl border border-[#e2e9f5] bg-white p-6">
      <div class="flex gap-2 rounded-lg bg-[#f0f4fa] p-1 text-sm">
        <button
          class="flex-1 rounded-md py-1.5 transition"
          :class="mode === 'login' ? 'bg-white font-semibold text-[#1845ad] shadow-sm' : 'text-[#5c6f94]'"
          @click="mode = 'login'"
        >
          登录
        </button>
        <button
          class="flex-1 rounded-md py-1.5 transition"
          :class="mode === 'register' ? 'bg-white font-semibold text-[#1845ad] shadow-sm' : 'text-[#5c6f94]'"
          @click="mode = 'register'"
        >
          注册
        </button>
      </div>

      <label class="block text-sm">
        <span class="text-[#5c6f94]">用户名</span>
        <input
          v-model="username"
          class="mt-1 w-full rounded-lg border border-[#e2e9f5] px-3 py-2 outline-none focus:border-[#1f49ba]"
          placeholder="3-32 位"
        />
      </label>

      <label v-if="mode === 'register'" class="block text-sm">
        <span class="text-[#5c6f94]">邮箱（可选）</span>
        <input
          v-model="email"
          class="mt-1 w-full rounded-lg border border-[#e2e9f5] px-3 py-2 outline-none focus:border-[#1f49ba]"
          placeholder="you@example.com"
        />
      </label>

      <label class="block text-sm">
        <span class="text-[#5c6f94]">密码</span>
        <input
          v-model="password"
          type="password"
          class="mt-1 w-full rounded-lg border border-[#e2e9f5] px-3 py-2 outline-none focus:border-[#1f49ba]"
          placeholder="至少 6 位"
          @keyup.enter="submit"
        />
      </label>

      <p v-if="error" class="rounded-lg border border-[#ffc9c9] bg-[#fff5f5] px-3 py-2 text-sm text-[#c0392b]">
        {{ error }}
      </p>

      <button
        class="w-full rounded-lg bg-[#1f49ba] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#1a3fa0] disabled:opacity-60"
        :disabled="loading"
        @click="submit"
      >
        {{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>

      <p class="text-center text-xs text-[#9aa9c3]">本地开发默认账号库为空，先注册一个即可。</p>
    </section>
  </div>
</template>