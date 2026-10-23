<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import ThemeToggle from './ThemeToggle.vue'

const route = useRoute()
const userStore = useUserStore()
const { user, dashboard } = storeToRefs(userStore)

interface NavItem {
  to: string
  label: string
  desc: string
  icon: string
}

/** 主功能入口。图标用内联 SVG 路径，避免额外图标依赖。 */
const primaryNav: NavItem[] = [
  {
    to: '/dashboard',
    label: '工作台',
    desc: '总览与快捷入口',
    icon: 'M3 3h7v7H3V3Zm11 0h7v5h-7V3ZM3 14h7v7H3v-7Zm11 3h7v4h-7v-4Z',
  },
  {
    to: '/learn',
    label: '知识点',
    desc: '四科知识体系',
    icon: 'M4 4h7v16H4V4Zm9 0h7v16h-7V4Z',
  },
  {
    to: '/practice',
    label: '刷题',
    desc: '逐题作答与解析',
    icon: 'M4 5h16v3H4V5Zm0 6h16v3H4v-3Zm0 6h10v3H4v-3Z',
  },
  {
    to: '/memory',
    label: '背诵',
    desc: '要点与公式记忆',
    icon: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Zm0 0v18M3 12h18',
  },
  {
    to: '/exams',
    label: '真题卷',
    desc: '2009–2026 共 18 卷',
    icon: 'M6 2h9l5 5v15H6V2Zm8 1v5h5M9 13h7M9 17h7',
  },
  {
    to: '/search',
    label: '搜索',
    desc: '知识点与题目',
    icon: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm9 16-3.5-3.5',
  },
  {
    to: '/stats',
    label: '学习看板',
    desc: '掌握度与打卡',
    icon: 'M5 20V10h3v10H5Zm6 0V4h3v16h-3Zm6 0v-7h3v7h-3Z',
  },
]

const accountNav: NavItem[] = [
  {
    to: '/me',
    label: '我的',
    desc: '账号与偏好',
    icon: 'M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-5 0-9 3-9 6v2h18v-2c0-3-4-6-9-6Z',
  },
]

const isActive = (to: string) => route.path === to || route.path.startsWith(to + '/')
const done = computed(() => dashboard.value?.doneQuestions ?? 0)
const total = computed(() => dashboard.value?.totalQuestions ?? 0)
const progress = computed(() => dashboard.value?.progress ?? 0)
</script>

<template>
  <aside
    class="hidden md:flex w-[248px] shrink-0 flex-col border-r border-[var(--k-border)] bg-[var(--k-surface)]"
  >
    <!-- 品牌 -->
    <RouterLink to="/" class="flex items-center gap-3 px-5 py-5 no-underline">
      <span
        class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--k-accent)] text-[13px] font-bold tracking-tight text-white"
        >408</span
      >
      <span class="min-w-0">
        <span class="block text-[15px] font-bold leading-tight text-[var(--k-text)]">考研 408 工作台</span>
        <span class="block truncate text-[11px] text-[var(--k-text-faint)]">计算机统考备考平台</span>
      </span>
    </RouterLink>

    <!-- 主操作 -->
    <div class="px-4 pb-4">
      <RouterLink
        to="/practice"
        class="flex h-11 items-center justify-center gap-2 rounded-xl bg-[var(--k-accent)] text-sm font-bold text-white no-underline transition hover:bg-[var(--k-accent-hover)]"
      >
        <span class="text-base leading-none">＋</span>
        <span>开始刷题</span>
      </RouterLink>
    </div>

    <!-- 主导航 -->
    <nav class="flex-1 overflow-y-auto px-3">
      <RouterLink
        v-for="item in primaryNav"
        :key="item.to"
        :to="item.to"
        class="mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 no-underline transition"
        :class="
          isActive(item.to)
            ? 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
            : 'text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)] hover:text-[var(--k-text)]'
        "
      >
        <svg
          class="h-[18px] w-[18px] shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path :d="item.icon" />
        </svg>
        <span class="min-w-0 flex-1">
          <span class="block text-sm font-semibold leading-tight">{{ item.label }}</span>
          <span
            class="block truncate text-[11px]"
            :class="isActive(item.to) ? 'text-[#5b7ac4]' : 'text-[var(--k-text-faint)]'"
            >{{ item.desc }}</span
          >
        </span>
        <span
          v-if="item.to === '/exams'"
          class="shrink-0 rounded-full bg-[var(--k-chip)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--k-text-muted)]"
          >18</span
        >
      </RouterLink>
    </nav>

    <!-- 进度卡 -->
    <div class="mx-4 mb-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-3">
      <div class="flex items-baseline justify-between">
        <span class="text-[11px] text-[var(--k-text-faint)]">复习进度</span>
        <span class="text-[11px] font-semibold text-[var(--k-accent-text)] tabular-nums">{{ progress }}%</span>
      </div>
      <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-[#e4ebf7]">
        <div
          class="h-full rounded-full bg-[var(--k-accent)] transition-all duration-500"
          :style="{ width: Math.max(progress, done > 0 ? 2 : 0) + '%' }"
        />
      </div>
      <p class="mt-2 text-[11px] text-[var(--k-text-faint)] tabular-nums">已刷 {{ done }} / {{ total }} 题</p>
    </div>

    <!-- 账号 -->
    <div class="border-t border-[var(--k-border)] px-3 py-3">
      <RouterLink
        v-for="item in accountNav"
        :key="item.to"
        :to="item.to"
        class="flex items-center gap-3 rounded-xl px-3 py-2 no-underline transition"
        :class="
          isActive(item.to) ? 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]' : 'text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
        "
      >
        <svg
          class="h-[18px] w-[18px] shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path :d="item.icon" />
        </svg>
        <span class="text-sm font-semibold">{{ item.label }}</span>
      </RouterLink>

      <!-- 主题切换（跟随时间 / 日间 / 夜间） -->
      <ThemeToggle />

      <div class="mt-2 flex items-center gap-2.5 rounded-xl bg-[var(--k-surface-soft)] px-3 py-2.5">
        <span
          class="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-[var(--k-accent)] text-xs font-bold text-white"
          >{{ user ? user.username.slice(0, 1).toUpperCase() : '游' }}</span
        >
        <span class="min-w-0 flex-1">
          <span class="block truncate text-xs font-semibold text-[var(--k-text)]">{{
            user ? user.username : '游客模式'
          }}</span>
          <span class="block truncate text-[10px] text-[var(--k-text-faint)]">{{
            user ? '已登录' : '未保存做题记录'
          }}</span>
        </span>
        <button
          v-if="user"
          class="shrink-0 text-[11px] text-[var(--k-danger)] hover:underline"
          @click="userStore.logout()"
        >
          退出
        </button>
        <RouterLink v-else to="/me" class="shrink-0 text-[11px] font-semibold text-[var(--k-accent)] no-underline hover:underline">
          登录
        </RouterLink>
      </div>
    </div>
  </aside>
</template>