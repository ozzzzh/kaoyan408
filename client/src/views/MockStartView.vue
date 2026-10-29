<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { ExamSummary, MockHistoryItem } from '@/api/types'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const exams = ref<ExamSummary[]>([])
const history = ref<MockHistoryItem[]>([])
const loading = ref(true)
const starting = ref(false)
const error = ref<string | null>(null)

const year = ref<number>(0)
const minutes = ref(180)

const durationOptions = [
  { label: '180 分钟', value: 180, hint: '真实考试时长' },
  { label: '120 分钟', value: 120, hint: '加快节奏' },
  { label: '60 分钟', value: 60, hint: '只练选择题' },
  { label: '不限时', value: 0, hint: '练习模式' },
]

/** 从 URL 带入的年份 */
const initialYear = Number(route.query.year)

const needLogin = computed(() => !userStore.user)

const historyByYear = computed(() => {
  const map: Record<number, MockHistoryItem[]> = {}
  for (const h of history.value) {
    ;(map[h.examYear] ??= []).push(h)
  }
  return map
})

async function load() {
  loading.value = true
  try {
    exams.value = await api.exams()
    if (!year.value) {
      year.value = initialYear || exams.value[exams.value.length - 1]?.year || 2009
    }
    if (userStore.user) {
      try {
        const h = await api.mockHistory(50)
        history.value = h.items
      } catch {
        history.value = []
      }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function start() {
  // 游客也能开考：会话走服务端内存，交卷后返回成绩但不落库。
  // 不再强制跳登录，否则游客点进来什么也做不了。
  if (!year.value) return
  starting.value = true
  error.value = null
  try {
    const state = await api.mockStart(year.value, minutes.value)
    if (state.status === 'submitted') {
      await router.push(`/mock/${state.sessionId}/result`)
    } else {
      await router.push(`/mock/${state.sessionId}`)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法开始考试'
  } finally {
    starting.value = false
  }
}

async function resume(sessionId: number) {
  await router.push(`/mock/${sessionId}`)
}

onMounted(async () => {
  await userStore.loadMe?.()
  await load()
})
</script>

<template>
  <div class="mx-auto max-w-2xl space-y-5">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">模拟考试</h1>
      <p class="text-sm text-[var(--k-text-muted)]">
        整卷计时作答，交卷后自动判选择题；综合题对照参考答案自评。
      </p>
    </header>

    <div v-if="loading" class="grid min-h-[40vh] place-items-center">
      <p class="text-sm text-[var(--k-text-muted)]">载入中…</p>
    </div>

    <template v-else>
      <p
        v-if="needLogin"
        class="rounded-lg border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-xs text-[var(--k-text-muted)]"
      >
        游客也可直接开考，交卷后照常判分与查看解析；成绩不会保存，登录后才会记录历史。
        <RouterLink to="/me" class="text-[var(--k-accent-text)] hover:underline">去登录 →</RouterLink>
      </p>

      <p
        v-if="error"
        class="rounded-lg border border-[var(--k-danger)]/30 bg-[var(--k-warn-soft)] px-4 py-3 text-xs text-[var(--k-danger)]"
      >
        {{ error }}
      </p>

      <!-- 选年份 -->
      <section class="space-y-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
        <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">选择试卷</p>
        <div class="grid grid-cols-3 gap-2 sm:grid-cols-5">
          <button
            v-for="e in exams"
            :key="e.year"
            class="relative rounded-lg border px-2 py-2 text-center transition"
            :class="
              year === e.year
                ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)]'
                : 'border-[var(--k-border)] hover:border-[var(--k-border-strong)]'
            "
            @click="year = e.year"
          >
            <span
              class="block text-sm font-semibold"
              :class="year === e.year ? 'text-[var(--k-accent-text)]' : 'text-[var(--k-text)]'"
            >
              {{ e.year }}
            </span>
            <span class="mt-0.5 block text-[10px] text-[var(--k-text-faint)]">
              {{ historyByYear[e.year]?.length ? `考过 ${historyByYear[e.year].length} 次` : `${e.questionCount} 题` }}
            </span>
          </button>
        </div>
      </section>

      <!-- 选时长 -->
      <section class="space-y-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
        <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">限时</p>
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <button
            v-for="opt in durationOptions"
            :key="opt.value"
            class="rounded-lg border px-3 py-2 text-center transition"
            :class="
              minutes === opt.value
                ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)]'
                : 'border-[var(--k-border)] hover:border-[var(--k-border-strong)]'
            "
            @click="minutes = opt.value"
          >
            <span
              class="block text-xs font-semibold"
              :class="minutes === opt.value ? 'text-[var(--k-accent-text)]' : 'text-[var(--k-text)]'"
            >
              {{ opt.label }}
            </span>
            <span class="mt-0.5 block text-[10px] text-[var(--k-text-faint)]">{{ opt.hint }}</span>
          </button>
        </div>
      </section>

      <button
        class="w-full rounded-xl bg-[var(--k-accent)] px-6 py-3 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
        :disabled="starting || !year"
        @click="start"
      >
        {{ starting ? '正在准备试卷…' : `开始 ${year} 年模拟考试` }}
      </button>

      <!-- 历史成绩 -->
      <section v-if="history.length" class="space-y-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">历史成绩</h2>
        <div class="space-y-2">
          <div
            v-for="h in history.slice(0, 10)"
            :key="h.sessionId"
            class="flex flex-wrap items-center gap-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3"
          >
            <span class="text-sm font-semibold text-[var(--k-text)]">{{ h.examYear }}</span>
            <span class="text-lg font-bold tabular-nums text-[var(--k-text)]">
              {{ h.autoScore + h.selfScore }}
              <span class="text-[11px] font-normal text-[var(--k-text-faint)]">/150</span>
            </span>
            <span class="text-[11px] text-[var(--k-text-faint)]">
              选择 {{ h.autoScore }}/80 · 综合 {{ h.selfScore }}/70
            </span>
            <span v-if="h.timedOut === 1" class="text-[11px] text-[var(--k-danger)]">超时</span>
            <button
              class="ml-auto text-[11px] text-[var(--k-accent-text)] hover:underline"
              @click="resume(h.sessionId)"
            >
              查看成绩单 →
            </button>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>