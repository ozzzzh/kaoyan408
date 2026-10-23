<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const { user, dashboard } = storeToRefs(userStore)

const subjectMeta: Record<string, { name: string; bar: string; soft: string; text: string }> = {
  ds: { name: '数据结构', bar: 'bg-[#2d68ef]', soft: 'bg-[#eaf1ff]', text: 'text-[#1845ad]' },
  os: { name: '操作系统', bar: 'bg-[#7c5cf0]', soft: 'bg-[#f1ecff]', text: 'text-[#5b3fd1]' },
  co: { name: '计算机组成原理', bar: 'bg-[#f0a020]', soft: 'bg-[#fff5e6]', text: 'text-[#b8730a]' },
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
    accent: 'border-[#bcd3ff]',
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
    accent: 'border-[#ffd9a0]',
    icon: 'paper',
  },
]

function go(shortcut: (typeof shortcuts)[number]) {
  router.push({ path: shortcut.to, query: shortcut.query })
}

const quickSubjects = computed(() =>
  subjects.value.map((item) => ({
    ...item,
    meta: subjectMeta[item.subject] ?? { name: item.subject, bar: 'bg-[#2d68ef]', soft: 'bg-[#eaf1ff]', text: 'text-[#1845ad]' },
  })),
)
</script>

<template>
  <div class="space-y-7">
    <!-- 欢迎 -->
    <header>
      <h1 class="text-2xl font-bold tracking-tight text-[#0b1f45] md:text-[28px]">
        {{ greeting }}，今天想复习哪一块？
      </h1>
      <p class="mt-2 text-sm text-[#5c6f94]">
        <template v-if="dashboard">
          题库共 {{ dashboard.totalQuestions }} 道真题，已刷 {{ dashboard.doneQuestions }} 道；
          每道题都挂在知识点上，做完可以直接看对应正文与动画。
        </template>
        <template v-else>正在加载学习数据…</template>
      </p>
    </header>

    <!-- 主入口：直接跳刷题 -->
    <section
      class="rounded-2xl border border-[#dbe4ef] bg-white p-2.5 shadow-[0_10px_30px_rgba(52,87,150,0.07)]"
    >
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
        <div class="flex flex-1 items-center gap-3 px-3 py-2">
          <svg
            class="h-5 w-5 shrink-0 text-[#7c8bab]"
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
            class="w-full border-0 bg-transparent text-[15px] text-[#0b1f45] outline-0 placeholder:text-[#9aa9c3]"
            placeholder="输入知识点或题号（如 KMP / 银行家算法 / 2026 第 1 题）"
            @keydown.enter="router.push({ path: '/learn', query: { q: ($event.target as HTMLInputElement).value } })"
          />
        </div>
        <RouterLink
          to="/practice"
          class="flex h-12 shrink-0 items-center justify-center gap-2 rounded-xl bg-[#1f49ba] px-7 text-sm font-bold text-white no-underline transition hover:bg-[#1a3fa0]"
        >
          <span class="text-base leading-none">＋</span>
          <span>开始刷题</span>
        </RouterLink>
      </div>
    </section>

    <!-- 四科掌握度 -->
    <section v-if="quickSubjects.length">
      <div class="mb-3 flex items-baseline justify-between">
        <h2 class="text-sm font-bold text-[#3d4d6b]">四科掌握度</h2>
        <RouterLink to="/stats" class="text-xs text-[#1f49ba] no-underline hover:underline">
          查看完整看板 →
        </RouterLink>
      </div>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <RouterLink
          v-for="item in quickSubjects"
          :key="item.subject"
          :to="{ path: '/learn', query: { subject: item.subject } }"
          class="rounded-xl border border-[#e2e9f5] bg-white p-4 no-underline transition hover:-translate-y-0.5 hover:shadow-[0_10px_24px_rgba(52,87,150,0.1)]"
        >
          <div class="flex items-center gap-2">
            <span class="h-2 w-2 rounded-full" :class="item.meta.bar" />
            <span class="text-sm font-semibold text-[#0b1f45]">{{ item.meta.name }}</span>
          </div>
          <p class="mt-3 text-2xl font-bold tabular-nums text-[#0b1f45]">
            {{ item.done }}<span class="text-sm font-normal text-[#9aa9c3]"> / {{ item.total }}</span>
          </p>
          <div class="mt-2.5 h-1.5 overflow-hidden rounded-full bg-[#eef2fa]">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="item.meta.bar"
              :style="{ width: Math.max(item.progress, item.done > 0 ? 2 : 0) + '%' }"
            />
          </div>
          <p class="mt-2 text-[11px] text-[#9aa9c3]">
            完成 {{ item.progress }}% · 正确率 {{ item.accuracy }}%
          </p>
        </RouterLink>
      </div>
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
        <h2 class="text-sm font-bold text-[#3d4d6b]">快捷复习入口</h2>
      </div>
      <div class="grid gap-3 md:grid-cols-3">
        <button
          v-for="shortcut in shortcuts"
          :key="shortcut.title"
          class="group flex flex-col rounded-xl border bg-white p-5 text-left transition hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(52,87,150,0.1)]"
          :class="shortcut.accent"
          @click="go(shortcut)"
        >
          <span
            class="mb-3 grid h-9 w-9 place-items-center rounded-lg bg-[#f0f4ff] text-[#1f49ba]"
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
          <span class="mb-1 text-[11px] font-semibold text-[#9aa9c3]">{{ shortcut.tag }}</span>
          <span class="text-[15px] font-bold text-[#0b1f45]">{{ shortcut.title }}</span>
          <span class="mt-1.5 flex-1 text-xs leading-relaxed text-[#5c6f94]">{{ shortcut.desc }}</span>
          <span class="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-[#1f49ba]">
            立即前往
            <span class="transition-transform group-hover:translate-x-0.5">→</span>
          </span>
        </button>
      </div>
    </section>

    <!-- 最近做题曲线 -->
    <section v-if="dashboard?.recent?.length">
      <h2 class="mb-3 text-sm font-bold text-[#3d4d6b]">近 14 天做题量</h2>
      <div class="rounded-xl border border-[#e2e9f5] bg-white p-5">
        <div class="flex h-24 items-end gap-1.5">
          <div
            v-for="day in dashboard.recent"
            :key="day.date"
            class="group flex flex-1 flex-col items-center gap-1.5"
          >
            <div
              class="w-full rounded-t bg-[#2d68ef]/70 transition group-hover:bg-[#2d68ef]"
              :style="{
                height:
                  Math.max(2, (day.questions / Math.max(1, ...dashboard.recent.map((d) => d.questions))) * 72) +
                  'px',
              }"
              :title="`${day.date}：${day.questions} 题`"
            />
            <span class="text-[10px] text-[#9aa9c3]">{{ day.date.slice(5) }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>