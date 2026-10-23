<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api/client'
import type { KnowledgePointView } from '@/api/types'

const points = ref<KnowledgePointView[]>([])
const activeSubject = ref<string>('')
const loading = ref(true)
const error = ref<string | null>(null)

const subjects = [
  { code: '', label: '全部' },
  { code: 'ds', label: '数据结构' },
  { code: 'os', label: '操作系统' },
  { code: 'co', label: '组成原理' },
  { code: 'cn', label: '计算机网络' },
]

async function load(subject: string) {
  loading.value = true
  error.value = null
  try {
    points.value = await api.knowledgePoints(subject || undefined)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load(''))

function select(subject: string) {
  activeSubject.value = subject
  void load(subject)
}
</script>

<template>
  <div class="space-y-6">
    <header class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">知识体系</h1>
      <p class="text-sm text-ink-600">
        按四科知识树组织的正文，每块都可反查关联真题；公式与动画在 M1 接入阅读页。
      </p>
    </header>

    <div class="flex flex-wrap gap-2">
      <button
        v-for="subject in subjects"
        :key="subject.code"
        class="rounded-lg border px-3 py-1.5 text-sm transition"
        :class="
          activeSubject === subject.code
            ? 'border-salt-500 bg-salt-500/10 text-salt-600 font-medium'
            : 'border-ink-200 text-ink-600 hover:bg-ink-50'
        "
        @click="select(subject.code)"
      >
        {{ subject.label }}
      </button>
    </div>

    <p v-if="loading" class="text-sm text-ink-400">加载中…</p>
    <p v-else-if="error" class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      {{ error }}
    </p>

    <div v-else class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="point in points"
        :key="point.pointId"
        class="rounded-lg border border-ink-200 bg-white px-4 py-3"
      >
        <p class="text-sm font-medium text-ink-900">{{ point.title }}</p>
        <p class="mt-1 text-xs text-ink-400">{{ point.pointId }} · {{ point.blockCount }} 个知识块</p>
      </div>
    </div>
  </div>
</template>