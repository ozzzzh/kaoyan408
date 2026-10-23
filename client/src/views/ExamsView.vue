<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { ExamSummary } from '@/api/types'

const exams = ref<ExamSummary[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const subjectMeta: Record<string, { name: string; dot: string }> = {
  ds: { name: '数据结构', dot: 'bg-[#2d68ef]' },
  os: { name: '操作系统', dot: 'bg-[#7c5cf0]' },
  co: { name: '组成原理', dot: 'bg-[#f0a020]' },
  cn: { name: '计算机网络', dot: 'bg-[#12a594]' },
}

onMounted(async () => {
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
  <div class="space-y-6">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">历年真题</h1>
      <p class="text-sm text-[var(--k-text-muted)]">2009 – 2026 共 18 卷，每卷 47 题（选择 40 + 综合 7）。</p>
    </header>

    <p v-if="loading" class="text-sm text-[var(--k-text-faint)]">加载中…</p>
    <p
      v-else-if="error"
      class="rounded-lg border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-sm text-[var(--k-warn)]"
    >
      {{ error }}
    </p>

    <div v-else class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <article
        v-for="exam in exams"
        :key="exam.year"
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5 transition hover:-translate-y-0.5 hover:border-[var(--k-border-strong)] hover:shadow-[0_12px_28px_rgba(52,87,150,0.1)]"
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
            <span class="h-1.5 w-1.5 rounded-full" :class="subjectMeta[subject]?.dot ?? 'bg-[var(--k-text-faint)]'" />
            {{ subjectMeta[subject]?.name ?? subject }} {{ count }}
          </span>
        </div>
      </article>
    </div>

    <p class="text-xs text-[var(--k-text-faint)]">
      整卷练习页与逐题作答界面在 M1 实现；当前已可从接口读取全部题干、选项与知识点关联。
    </p>
  </div>
</template>