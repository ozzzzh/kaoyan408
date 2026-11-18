<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { ExamSummary } from '@/api/types'
import { subjectMeta } from '@/constants/subjects'

const exams = ref<ExamSummary[]>([])
/** 每卷的个人进度：来自刷题队列（含 answered/correct），纯前端聚合，不改后端 */
type ExamProgress = { done: number; total: number; correct: number }
const progress = ref<Record<number, ExamProgress>>({})

async function loadProgress(list: ExamSummary[]) {
  await Promise.all(
    list.map(async (exam) => {
      try {
        const queue = await api.practiceQueue({ year: exam.year })
        const items = queue.items ?? []
        progress.value[exam.year] = {
          done: items.filter((i) => i.answered).length,
          total: items.length,
          correct: items.filter((i) => i.correct).length,
        }
      } catch {
        /* 单卷失败不影响其它卷 */
      }
    }),
  )
}
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    exams.value = await api.exams()
  void loadProgress(exams.value)
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
        <p
          v-if="progress[exam.year]"
          class="mt-3 flex flex-wrap items-center gap-x-2 text-[11px] tabular-nums text-[var(--k-text-faint)]"
        >
          <template v-if="progress[exam.year].done === 0"><span>未开始</span></template>
          <template v-else>
            <span>已做 {{ progress[exam.year].done }}/{{ progress[exam.year].total }}</span>
            <span>·</span>
            <span>
              正确率
              {{ Math.round((progress[exam.year].correct / Math.max(progress[exam.year].done, 1)) * 100) }}%
            </span>
            <span
              class="font-semibold"
              :class="progress[exam.year].done >= progress[exam.year].total ? 'text-[#0f7a4d]' : 'text-[var(--k-accent-text)]'"
            >
              {{ progress[exam.year].done >= progress[exam.year].total ? '· 已完成' : '· 进行中' }}
            </span>
          </template>
        </p>
        <p class="mt-3 text-xs font-semibold text-[var(--k-accent-text)]">查看整卷 →</p>
      </RouterLink>
    </div>

    <p class="text-xs text-[var(--k-text-faint)]">
      点任意年份可查看整卷。答案与解析需先作答该题后才会显示。
    </p>
  </div>
</template>