<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const { dashboard, user } = storeToRefs(userStore)

const subjectMeta: Record<string, { name: string; accent: string }> = {
  ds: { name: '数据结构', accent: 'from-sky-500/15 to-sky-500/0 text-sky-700' },
  os: { name: '操作系统', accent: 'from-violet-500/15 to-violet-500/0 text-violet-700' },
  co: { name: '计算机组成原理', accent: 'from-amber-500/15 to-amber-500/0 text-amber-700' },
  cn: { name: '计算机网络', accent: 'from-emerald-500/15 to-emerald-500/0 text-emerald-700' },
}

const subjects = computed(() => dashboard.value?.subjects ?? [])
</script>

<template>
  <div class="space-y-10">
    <section class="rounded-2xl border border-ink-200 bg-white p-8 shadow-sm">
      <p class="text-sm font-medium text-salt-600">2009 – 2026 · 18 卷真题 · 复习主线</p>
      <h1 class="mt-3 text-3xl font-semibold tracking-tight text-ink-900">
        把真题、知识体系和掌握度连成一条线
      </h1>
      <p class="mt-3 max-w-2xl text-sm leading-relaxed text-ink-600">
        每道题都挂到知识点上：做完题就能看到相关正文与动画，看板上按知识块显示掌握度，
        薄弱点直接回流到复习计划。
      </p>

      <div class="mt-6 flex flex-wrap gap-3">
        <RouterLink
          to="/practice"
          class="rounded-lg bg-salt-500 px-4 py-2 text-sm font-medium text-white hover:bg-salt-600 transition"
        >
          开始刷题
        </RouterLink>
        <RouterLink
          to="/exams"
          class="rounded-lg border border-ink-200 px-4 py-2 text-sm font-medium text-ink-800 hover:bg-ink-50 transition"
        >
          按年份刷整卷
        </RouterLink>
        <RouterLink
          to="/learn"
          class="rounded-lg border border-ink-200 px-4 py-2 text-sm font-medium text-ink-800 hover:bg-ink-50 transition"
        >
          先看知识正文
        </RouterLink>
      </div>

      <p v-if="!user" class="mt-4 text-xs text-ink-400">
        当前为游客模式：题库与知识正文可直接浏览，做题记录需要
        <RouterLink to="/me" class="text-salt-600 hover:underline">登录</RouterLink> 后保存。
      </p>
    </section>

    <section v-if="dashboard" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div class="rounded-xl border border-ink-200 bg-white p-5">
        <p class="text-xs text-ink-400">题库总量</p>
        <p class="mt-2 text-2xl font-semibold text-ink-900">{{ dashboard.totalQuestions }}</p>
        <p class="mt-1 text-xs text-ink-400">题（{{ subjects.length }} 个科目）</p>
      </div>
      <div class="rounded-xl border border-ink-200 bg-white p-5">
        <p class="text-xs text-ink-400">已作答</p>
        <p class="mt-2 text-2xl font-semibold text-ink-900">{{ dashboard.doneQuestions }}</p>
        <p class="mt-1 text-xs text-ink-400">完成度 {{ dashboard.progress }}%</p>
      </div>
      <div class="rounded-xl border border-ink-200 bg-white p-5">
        <p class="text-xs text-ink-400">正确率</p>
        <p class="mt-2 text-2xl font-semibold text-ink-900">{{ dashboard.accuracy }}%</p>
        <p class="mt-1 text-xs text-ink-400">答对 {{ dashboard.correctQuestions }} 题</p>
      </div>
      <div class="rounded-xl border border-ink-200 bg-white p-5">
        <p class="text-xs text-ink-400">连续打卡</p>
        <p class="mt-2 text-2xl font-semibold text-ink-900">{{ dashboard.streakDays }}</p>
        <p class="mt-1 text-xs text-ink-400">天</p>
      </div>
    </section>

    <section v-if="subjects.length" class="space-y-3">
      <h2 class="text-sm font-medium text-ink-600">四科掌握度</h2>
      <div class="grid gap-4 sm:grid-cols-2">
        <div
          v-for="item in subjects"
          :key="item.subject"
          class="rounded-xl border border-ink-200 bg-gradient-to-br p-5"
          :class="subjectMeta[item.subject]?.accent"
        >
          <div class="flex items-baseline justify-between">
            <span class="font-medium">{{ subjectMeta[item.subject]?.name ?? item.subject }}</span>
            <span class="text-xs opacity-70">{{ item.done }} / {{ item.total }}</span>
          </div>
          <div class="mt-3 h-2 overflow-hidden rounded-full bg-white/70">
            <div class="h-full rounded-full bg-current opacity-70" :style="{ width: item.progress + '%' }" />
          </div>
          <p class="mt-2 text-xs opacity-70">
            完成 {{ item.progress }}% · 正确率 {{ item.accuracy }}%
          </p>
        </div>
      </div>
    </section>

    <section v-else class="rounded-xl border border-dashed border-ink-200 bg-white p-6 text-sm text-ink-400">
      暂无统计数据。请确认后端服务已启动（默认 http://127.0.0.1:8080），并已导入内容数据。
    </section>
  </div>
</template>