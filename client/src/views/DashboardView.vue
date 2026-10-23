<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { api } from '@/api/client'
import type { PracticeEntrances } from '@/api/types'
import BackgroundFx from '@/components/layout/BackgroundFx.vue'
import ContributionHeatmap from '@/components/dashboard/ContributionHeatmap.vue'
import SubjectMasteryPanel from '@/components/dashboard/SubjectMasteryPanel.vue'

const router = useRouter()
const userStore = useUserStore()
const { user, dashboard } = storeToRefs(userStore)

const subjectMeta: Record<string, { name: string; bar: string; soft: string; text: string }> = {
  ds: { name: '数据结构', bar: 'bg-[#2d68ef]', soft: 'bg-[#eaf1ff]', text: 'text-[var(--k-accent-text)]' },
  os: { name: '操作系统', bar: 'bg-[#7c5cf0]', soft: 'bg-[#f1ecff]', text: 'text-[#5b3fd1]' },
  co: { name: '计算机组成原理', bar: 'bg-[#f0a020]', soft: 'bg-[#fff5e6]', text: 'text-[var(--k-warn)]' },
  cn: { name: '计算机网络', bar: 'bg-[#12a594]', soft: 'bg-[#e6f7f5]', text: 'text-[#0b7d70]' },
}

const subjects = computed(() => dashboard.value?.subjects ?? [])
const greeting = computed(() => {
  const hour = new Date().getHours()
  const part = hour < 6 ? '凌晨好' : hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
  return `${part}${user.value ? '，' + user.value.username : ''}`
})

/** 快捷入口：跳到知识点与刷题。 */
const shortcuts = [
  {
    title: '数据结构 · 线性表',
    desc: '顺序表与链表的插入删除、时间复杂度对比，配 KMP 与二叉树动画。',
    to: '/learn',
    query: { subject: 'ds' },
    tag: '知识点',
    accent: 'border-[var(--k-border-strong)]',
    icon: 'list',
  },
  {
    title: '操作系统 · 进程管理',
    desc: '进程状态转换、调度算法、同步互斥与银行家算法，考频最高的章节。',
    to: '/learn',
    query: { subject: 'os' },
    tag: '知识点',
    accent: 'border-[#d6c9ff]',
    icon: 'list',
  },
  {
    title: '按年份刷整卷',
    desc: '2009–2026 共 18 卷，每组 47 题。做完自动记录掌握度。',
    to: '/exams',
    query: {},
    tag: '真题卷',
    accent: 'border-[var(--k-warn-border)]',
    icon: 'paper',
  },
]

function go(shortcut: (typeof shortcuts)[number]) {
  router.push({ path: shortcut.to, query: shortcut.query })
}

const quickSubjects = computed(() =>
  subjects.value.map((item) => ({
    ...item,
    meta: subjectMeta[item.subject] ?? { name: item.subject, bar: 'bg-[#2d68ef]', soft: 'bg-[#eaf1ff]', text: 'text-[var(--k-accent-text)]' },
  })),
)

/** 刷题入口汇总（按科目 / 错题 / 未做），登录后才有个人数据。 */
const entrances = ref<PracticeEntrances | null>(null)
onMounted(async () => {
  try {
    entrances.value = await api.practiceEntrances()
  } catch {
    entrances.value = null
  }
})
</script>

<template>
  <div class="relative space-y-7">
    <!-- 鼠标跟随背景：只铺在工作台首屏区域，位于内容之下 -->
    <div class="pointer-events-none absolute inset-x-0 -top-6 -bottom-24 overflow-hidden">
      <BackgroundFx />
    </div>

    <div class="relative space-y-7">
      <!-- 欢迎 -->
      <header>
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)] md:text-[28px]">
        {{ greeting }}，今天想复习哪一块？
      </h1>
      <p class="mt-2 text-sm text-[var(--k-text-muted)]">
        <template v-if="dashboard">
          题库共 {{ dashboard.totalQuestions }} 道真题，已刷 {{ dashboard.doneQuestions }} 道；
          每道题都挂在知识点上，做完可以直接看对应正文与动画。
        </template>
        <template v-else>正在加载学习数据…</template>
      </p>
    </header>

    <!-- 主入口：直接跳刷题 -->
    <section
      class="rounded-2xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] p-2.5 shadow-[0_10px_30px_rgba(52,87,150,0.07)]"
    >
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div class="flex flex-1 items-center gap-3 px-3 py-2">
          <svg
            class="h-5 w-5 shrink-0 text-[var(--k-text-faint)]"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          >
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
          <input
            class="w-full border-0 bg-transparent text-[15px] text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
            placeholder="输入知识点或题号（如 KMP / 银行家算法 / 2026 第 1 题）"
            @keydown.enter="router.push({ path: '/learn', query: { q: ($event.target as HTMLInputElement).value } })"
          />
        </div>
        <RouterLink
          to="/practice"
          class="flex h-12 shrink-0 items-center justify-center gap-2 rounded-xl bg-[var(--k-accent)] px-7 text-sm font-bold text-white no-underline transition hover:bg-[var(--k-accent-hover)]"
        >
          <span class="text-base leading-none">＋</span>
          <span>开始刷题</span>
        </RouterLink>
      </div>
    </section>

    <!-- 刷题入口 -->
    <section v-if="entrances">
      <h2 class="mb-3 text-sm font-bold text-[var(--k-heading)]">按范围刷题</h2>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <RouterLink
          v-for="item in entrances.subjects"
          :key="item.subject"
          :to="{ path: '/practice', query: { subject: item.subject } }"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4 no-underline transition hover:-translate-y-0.5 hover:border-[var(--k-accent)]"
        >
          <p class="text-sm font-semibold text-[var(--k-text)]">{{ item.name }}</p>
          <p class="mt-1.5 text-xs text-[var(--k-text-faint)] tabular-nums">
            已做 {{ item.done }} / {{ item.total }}
          </p>
        </RouterLink>
      </div>

      <div class="mt-3 grid gap-3 sm:grid-cols-3">
        <RouterLink
          :to="{ path: '/practice', query: { scope: 'untouched' } }"
          class="flex items-center justify-between rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
        >
          <span class="text-sm font-semibold text-[var(--k-text)]">未做的题</span>
          <span class="text-sm font-bold tabular-nums text-[var(--k-accent-text)]">
            {{ entrances.untouched }}
          </span>
        </RouterLink>
        <RouterLink
          :to="{ path: '/practice', query: { scope: 'wrong' } }"
          class="flex items-center justify-between rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[#b23c3c]"
        >
          <span class="text-sm font-semibold text-[var(--k-text)]">错题本</span>
          <span
            class="text-sm font-bold tabular-nums"
            :class="entrances.wrong > 0 ? 'text-[#b23c3c]' : 'text-[var(--k-text-faint)]'"
          >
            {{ entrances.wrong }}
          </span>
        </RouterLink>
        <RouterLink
          :to="{ path: '/exams' }"
          class="flex items-center justify-between rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
        >
          <span class="text-sm font-semibold text-[var(--k-text)]">按年份刷整卷</span>
          <span class="text-sm font-bold tabular-nums text-[var(--k-accent-text)]">18 卷</span>
        </RouterLink>
      </div>
    </section>

    <!-- 四科掌握进度 + 做题贡献并排 -->
    <section v-if="dashboard" class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_320px]">
      <SubjectMasteryPanel :subjects="dashboard.subjects" :mastery-rate="dashboard.masteryRate" />
      <ContributionHeatmap
        :heatmap="dashboard.heatmap ?? null"
        :active-days="dashboard.activeDays"
        :streak-days="dashboard.streakDays"
      />
    </section>

    <!-- 快捷入口 -->
    <section>
      <div class="mb-3 flex items-center gap-2">
        <svg
          class="h-4 w-4 text-[#f0a020]"
          viewBox="0 0 24 24"
          fill="currentColor"
        >
          <path d="M13 2 3 14h7l-1 8 10-12h-7l1-8Z" />
        </svg>
        <h2 class="text-sm font-bold text-[var(--k-heading)]">快捷复习入口</h2>
      </div>
      <div class="grid gap-3 md:grid-cols-3">
        <button
          v-for="shortcut in shortcuts"
          :key="shortcut.title"
          class="group flex flex-col rounded-xl border bg-[var(--k-surface)] p-5 text-left transition hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(52,87,150,0.1)]"
          :class="shortcut.accent"
          @click="go(shortcut)"
        >
          <span
            class="mb-3 grid h-9 w-9 place-items-center rounded-lg bg-[#f0f4ff] text-[var(--k-accent)]"
          >
            <svg
              v-if="shortcut.icon === 'paper'"
              class="h-4 w-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
            >
              <path d="M6 2h9l5 5v15H6V2Zm8 1v5h5" />
            </svg>
            <svg
              v-else
              class="h-4 w-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
            >
              <path d="M4 6h16M4 12h16M4 18h10" />
            </svg>
          </span>
          <span class="mb-1 text-[11px] font-semibold text-[var(--k-text-faint)]">{{ shortcut.tag }}</span>
          <span class="text-[15px] font-bold text-[var(--k-text)]">{{ shortcut.title }}</span>
          <span class="mt-1.5 flex-1 text-xs leading-relaxed text-[var(--k-text-muted)]">{{ shortcut.desc }}</span>
          <span class="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-[var(--k-accent)]">
            立即前往
            <span class="transition-transform group-hover:translate-x-0.5">→</span>
          </span>
        </button>
      </div>
    </section>
    </div>
  </div>
</template>