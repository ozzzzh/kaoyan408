<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import BrandMark from '@/components/brand/BrandMark.vue'

const route = useRoute()
const userStore = useUserStore()
const { user, dashboard } = storeToRefs(userStore)
const open = ref(false)

const navItems = [
  { to: '/dashboard', label: '工作台' },
  { to: '/learn', label: '知识点' },
  { to: '/practice', label: '刷题' },
  { to: '/exams', label: '真题卷' },
  { to: '/stats', label: '学习看板' },
  { to: '/me', label: '我的' },
]

const isActive = (to: string) => route.path === to || route.path.startsWith(to + '/')
</script>

<template>
  <header class="md:hidden sticky top-0 z-30 border-b border-[var(--k-border)] bg-[var(--k-surface)]/95 backdrop-blur">
    <div class="flex h-14 items-center gap-3 px-4">
      <RouterLink to="/" class="flex items-center gap-2 no-underline">
        <BrandMark :size="28" />
        <span class="text-[15px] font-bold text-[var(--k-text)]">题知链 408</span>
      </RouterLink>

      <div class="ml-auto flex items-center gap-3">
        <span v-if="dashboard" class="text-[11px] text-[var(--k-text-faint)] tabular-nums">
          {{ dashboard.doneQuestions }}/{{ dashboard.totalQuestions }}
        </span>
        <button
          class="rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-xs text-[var(--k-text-muted)]"
          @click="open = !open"
        >
          {{ open ? '收起' : '菜单' }}
        </button>
      </div>
    </div>

    <nav v-if="open" class="border-t border-[var(--k-border)] px-3 pb-3 pt-1">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="block rounded-lg px-3 py-2.5 text-sm no-underline"
        :class="isActive(item.to) ? 'bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]' : 'text-[var(--k-text-muted)]'"
        @click="open = false"
      >
        {{ item.label }}
      </RouterLink>
      <div class="mt-1 flex items-center justify-between border-t border-[var(--k-border)] px-3 pt-3 text-xs">
        <span class="text-[var(--k-text-muted)]">{{ user ? user.username : '游客模式' }}</span>
        <button v-if="user" class="text-[var(--k-danger)]" @click="userStore.logout()">退出</button>
        <RouterLink v-else to="/me" class="font-semibold text-[var(--k-accent)] no-underline" @click="open = false">
          登录
        </RouterLink>
      </div>
    </nav>
  </header>
</template>