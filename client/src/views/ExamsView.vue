<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { ExamSummary } from '@/api/types'
import { subjectMeta } from '@/constants/subjects'

/** 每卷的个人进度：练习（question_attempt）与模考（mock_session）两种口径分开 */
type Practice = { total: number; done: number; correct: number }
type Mock = { attempts: number; bestScore: number; lastScore: number; lastAt: string | null; durationMinutes: number }
type ExamProgress = { practice?: Practice; mock?: Mock }

const exams = ref<ExamSummary[]>([])
const progress = ref<Record<number, ExamProgress>>({})
const loading = ref(true)
const error = ref<string | null>(null)

async function loadProgress() {
  try {
    const rows = await api.examProgress()
    const map: Record<number, ExamProgress> = {}
    for (const row of rows) map[row.year] = { practice: row.practice, mock: row.mock }
    progress.value = map
  } catch {
    /* 进度只是增强信息，失败不影响卷子列表 */
  }
}

onMounted(async () => {
  void loadProgress()
  try {
    exams.value = await api.exams()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-5">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">历年真题</h1>
        <p class="text-sm text-[var(--k-text-muted)]">2009 – 2026 共 18 卷，每卷 47 题（选择 40 + 综合 7）。</p>
      </div>
      <!-- 整卷阅读与限时模拟是两种用法：前者按需看题，后者还原考场 -->
      <RouterLink
        to="/mock/start"
        class="shrink-0 rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-[var(--k-on-accent)] no-underline transition hover:bg-[var(--k-accent-hover)]"
      >
        开始模拟考试 →
      </RouterLink>
    </header>

    <p v-if="loading" class="text-sm text-[var(--k-text-faint)]">加载中…</p>
    <p
      v-else-if="error"
      class="rounded-lg border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-sm text-[var(--k-warn)]"
    >
      {{ error }}
    </p>

    <div v-else class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <RouterLink
        v-for="exam in exams"
        :key="exam.year"
        :to="`/exams/${exam.year}`"
        class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5 no-underline transition hover:-translate-y-0.5 hover:border-[var(--k-accent)] hover:shadow-[0_12px_28px_rgba(52,87,150,0.1)]"
      >
        <div class="flex items-baseline justify-between">
          <h2 class="text-lg font-bold text-[var(--k-text)]">{{ exam.year }}</h2>
          <span class="text-xs text-[var(--k-text-faint)]">{{ exam.questionCount }} 题</span>
        </div>
        <p class="mt-1 line-clamp-1 text-xs text-[var(--k-text-faint)]">{{ exam.title }}</p>
        <div class="mt-3 flex flex-wrap gap-x-3 gap-y-1.5 text-xs text-[var(--k-text-muted)]">
          <span
            v-for="(count, subject) in exam.subjects"
            :key="subject"
            class="inline-flex items-center gap-1.5"
          >
            <span class="h-1.5 w-1.5 rounded-full" :class="subjectMeta(subject).bar" />
            {{ subjectMeta(subject).name }} {{ count }}
          </span>
        </div>
        <div v-if="progress[exam.year]" class="mt-3 space-y-0.5 text-[11px] tabular-nums text-[var(--k-text-faint)]">
          <!-- 练习口径 -->
          <p v-if="progress[exam.year].practice" class="flex flex-wrap items-center gap-x-2">
            <span class="font-semibold text-[var(--k-text-muted)]">练习</span>
            <template v-if="progress[exam.year].practice!.done === 0">
              <span>未开始</span>
            </template>
            <template v-else>
              <span>已做 {{ progress[exam.year].practice!.done }}/{{ progress[exam.year].practice!.total }}</span>
              <span>·</span>
              <span>
                正确率
                {{ Math.round((progress[exam.year].practice!.correct / Math.max(progress[exam.year].practice!.done, 1)) * 100) }}%
              </span>
              <span
                class="font-semibold"
                :class="progress[exam.year].practice!.done >= progress[exam.year].practice!.total ? 'text-[#0f7a4d]' : 'text-[var(--k-accent-text)]'"
              >
                {{ progress[exam.year].practice!.done >= progress[exam.year].practice!.total ? '· 已完成' : '· 进行中' }}
              </span>
            </template>
          </p>
          <!-- 模考口径：限时整卷，单独展示，不参与上面的完成度 -->
          <p v-if="progress[exam.year].mock" class="flex flex-wrap items-center gap-x-2">
            <span class="font-semibold text-[var(--k-text-muted)]">模考</span>
            <span>最好 {{ progress[exam.year].mock!.bestScore }}/150</span>
            <span>·</span>
            <span>最近 {{ progress[exam.year].mock!.lastScore }} 分</span>
            <span class="text-[var(--k-text-faint)]">
              （{{ progress[exam.year].mock!.attempts }} 次 · 限时 {{ progress[exam.year].mock!.durationMinutes }} 分钟）
            </span>
          </p>
        </div>        <p class="mt-3 text-xs font-semibold text-[var(--k-accent-text)]">查看整卷 →</p>
      </RouterLink>
    </div>

    <p class="text-xs text-[var(--k-text-faint)]">
      点任意年份可查看整卷。答案与解析需先作答该题后才会显示。
    </p>
  </div>
</template>
