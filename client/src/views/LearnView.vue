<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import type { KnowledgePointView } from '@/api/types'

const route = useRoute()
const points = ref<KnowledgePointView[]>([])
const activeSubject = ref<string>('')
const keyword = ref('')
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

/** 支持从工作台/首页带参数进入：#/learn?subject=ds&q=KMP */
function syncFromRoute() {
  const subject = typeof route.query.subject === 'string' ? route.query.subject : ''
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  activeSubject.value = subject
  void load(subject)
}

onMounted(syncFromRoute)
watch(() => route.query, syncFromRoute)

function select(subject: string) {
  activeSubject.value = subject
  void load(subject)
}
</script>

<template>
  <div class="space-y-6">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">知识点</h1>
      <p class="text-sm text-[var(--k-text-muted)]">
        按四科知识树组织的正文，每块都可反查关联真题；公式与动画在 M1 接入阅读页。
      </p>
    </header>

    <div class="flex flex-wrap items-center gap-2">
      <button
        v-for="subject in subjects"
        :key="subject.code"
        class="rounded-lg border px-3 py-1.5 text-sm transition"
        :class="
          activeSubject === subject.code
            ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
            : 'border-[var(--k-border)] bg-[var(--k-surface)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
        "
        @click="select(subject.code)"
      >
        {{ subject.label }}
      </button>
      <span v-if="keyword" class="ml-1 rounded-full bg-[#fff5e6] px-2.5 py-1 text-xs text-[var(--k-warn)]">
        搜索「{{ keyword }}」的结果
      </span>
    </div>

    <p v-if="loading" class="text-sm text-[var(--k-text-faint)]">加载中…</p>
    <p
      v-else-if="error"
      class="rounded-lg border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-sm text-[var(--k-warn)]"
    >
      {{ error }}
    </p>

    <div v-else class="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="point in points"
        :key="point.pointId"
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3.5 transition hover:-translate-y-0.5 hover:shadow-[0_10px_24px_rgba(52,87,150,0.08)]"
      >
        <p class="text-sm font-semibold text-[var(--k-text)]">{{ point.title }}</p>
        <p class="mt-1 text-xs text-[var(--k-text-faint)]">
          {{ point.pointId }} · {{ point.blockCount }} 个知识块
        </p>
      </div>
    </div>
  </div>
</template>