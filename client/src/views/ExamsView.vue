<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { ExamSummary } from '@/api/types'

const exams = ref<ExamSummary[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const subjectMeta: Record<string, string> = {
  ds: '数据结构',
  os: '操作系统',
  co: '组成原理',
  cn: '计算机网络',
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
      <h1 class="text-2xl font-semibold tracking-tight">历年真题</h1>
      <p class="text-sm text-ink-600">2009 – 2026 共 18 卷，每卷 47 题，按 408os 官方参照校准。</p>
    </header>

    <p v-if="loading" class="text-sm text-ink-400">加载中…</p>
    <p v-else-if="error" class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {{ error }}
    </p>

    <div v-else class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <article
        v-for="exam in exams"
        :key="exam.year"
        class="rounded-xl border border-ink-200 bg-white p-5 transition hover:border-salt-400 hover:shadow-sm"
      >
        <div class="flex items-baseline justify-between">
          <h2 class="text-lg font-semibold">{{ exam.year }}</h2>
          <span class="text-xs text-ink-400">{{ exam.questionCount }} 题</span>
        </div>
        <p class="mt-1 line-clamp-1 text-xs text-ink-400">{{ exam.title }}</p>
        <div class="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-600">
          <span v-for="(count, subject) in exam.subjects" :key="subject">
            {{ subjectMeta[subject] ?? subject }} {{ count }}
          </span>
        </div>
      </article>
    </div>

    <p class="text-xs text-ink-400">
      整卷练习页与逐题作答界面在 M1 实现；当前已可从接口读取全部题干、选项与知识点关联。
    </p>
  </div>
</template>