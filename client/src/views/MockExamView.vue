<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import { useUserStore } from '@/store/user'
import StemMarkdown from '@/components/mock/StemMarkdown.vue'
import type { MockQuestion, MockState } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sessionId = Number(route.params.sessionId)
const state = ref<MockState | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const submitting = ref(false)

/** 本地剩余秒数，由服务端下发值起始，之后每秒递减 */
const remaining = ref(0)
let ticker: number | null = null
/** 截止时间戳（毫秒），用于校正本地递减的漂移 */
let deadline = 0

const answers = ref<Record<string, string>>({})
/** 正在保存的题，避免重复请求 */
const saving = ref<Record<string, boolean>>({})

const subjectName = (code: string) =>
  ({ ds: '数据结构', os: '操作系统', co: '计算机组成原理', cn: '计算机网络' })[code] ?? code

const isSubmitted = computed(() => state.value?.status === 'submitted')
const items = computed(() => state.value?.items ?? [])
const choiceItems = computed(() => items.value.filter((i) => i.questionType === 'choice'))
const comprehensiveItems = computed(() =>
  items.value.filter((i) => i.questionType === 'comprehensive'),
)

const answeredCount = computed(
  () => choiceItems.value.filter((i) => answers.value[i.id]).length,
)
const progressPercent = computed(() =>
  choiceItems.value.length === 0
    ? 0
    : Math.round((answeredCount.value / choiceItems.value.length) * 100),
)

/** 剩余时间格式化 */
const clock = computed(() => {
  if (remaining.value < 0) return '不限时'
  const total = Math.max(0, remaining.value)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const isUrgent = computed(() => remaining.value >= 0 && remaining.value <= 300)

function startTicker() {
  if (ticker != null) return
  // 不限时（remainingSeconds = -1）不需要计时器。
  // 之前这里没有区分：deadline 被算成「现在 + 0」，第一秒就把 remaining 归零，
  // 触发自动交卷，导致「不限时」的场次开考 2 秒就被判超时交卷。
  if (remaining.value < 0) return
  ticker = window.setInterval(() => {
    if (deadline <= 0) return
    remaining.value = Math.max(0, Math.round((deadline - Date.now()) / 1000))
    if (remaining.value === 0 && !isSubmitted.value) {
      // 时间到自动交卷
      void doSubmit(true)
    }
  }, 1000)
}

function stopTicker() {
  if (ticker != null) {
    window.clearInterval(ticker)
    ticker = null
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const data = await api.mockState(sessionId)
    apply(data)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function apply(data: MockState) {
  state.value = data
  const map: Record<string, string> = {}
  for (const item of data.items) {
    if (item.myAnswer) map[item.id] = item.myAnswer
  }
  answers.value = map
  if (data.status !== 'submitted') {
    remaining.value = data.remainingSeconds
    if (data.remainingSeconds < 0) {
      // 不限时：不设截止时间，也不启动计时器
      deadline = 0
      stopTicker()
    } else {
      deadline = Date.now() + data.remainingSeconds * 1000
      startTicker()
    }
  } else {
    remaining.value = 0
    deadline = 0
    stopTicker()
  }
}

/** 选答案：立即写入本地，并异步保存到服务端 */
async function pick(question: MockQuestion, key: string) {
  if (isSubmitted.value) return
  answers.value = { ...answers.value, [question.id]: key }
  saving.value = { ...saving.value, [question.id]: true }
  try {
    await api.mockAnswer(sessionId, question.id, key)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = { ...saving.value, [question.id]: false }
  }
}

const confirmSubmit = ref(false)

async function doSubmit(timedOut = false) {
  if (submitting.value || isSubmitted.value) return
  submitting.value = true
  confirmSubmit.value = false
  try {
    const data = await api.mockSubmit(sessionId, timedOut)
    stopTicker()
    state.value = data
    await router.push(`/mock/${sessionId}/result`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '交卷失败'
  } finally {
    submitting.value = false
  }
}

/** 跳转到某题 */
function jumpTo(examRef: string) {
  const el = document.getElementById('q-' + examRef)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

onMounted(async () => {
  await userStore.loadMe?.()
  await load()
})

onBeforeUnmount(stopTicker)
</script>

<template>
  <div class="space-y-5">
    <!-- 加载 / 错误 -->
    <div v-if="loading" class="grid min-h-[60vh] place-items-center">
      <p class="text-sm text-[var(--k-text-muted)]">正在载入试卷…</p>
    </div>

    <div v-else-if="error && !state" class="grid min-h-[60vh] place-items-center">
      <div class="space-y-3 text-center">
        <p class="text-sm text-[var(--k-danger)]">{{ error }}</p>
        <RouterLink to="/exams" class="text-xs text-[var(--k-accent-text)] hover:underline">
          返回真题卷
        </RouterLink>
      </div>
    </div>

    <template v-else-if="state">
      <!-- 顶部：年份 + 计时 + 交卷 -->
      <header
        class="sticky top-0 z-20 flex flex-wrap items-center gap-4 rounded-xl border border-[var(--k-border-strong)] bg-[var(--k-surface)]/95 px-5 py-3 backdrop-blur"
      >
        <div class="min-w-0">
          <h1 class="text-base font-bold text-[var(--k-text)]">
            {{ state.examYear }} 年 408 模拟考试
          </h1>
          <p class="text-[11px] text-[var(--k-text-faint)]">
            已答 {{ answeredCount }} / {{ choiceItems.length }} 选择题
          </p>
        </div>

        <!-- 进度条 -->
        <div class="hidden min-w-[120px] flex-1 sm:block">
          <div class="h-1.5 overflow-hidden rounded-full bg-[var(--k-track)]">
            <div
              class="h-full rounded-full bg-[var(--k-accent)] transition-[width] duration-300"
              :style="{ width: progressPercent + '%' }"
            />
          </div>
        </div>

        <!-- 计时器 -->
        <div
          class="ml-auto flex items-center gap-2 rounded-lg px-3 py-1.5 tabular-nums"
          :class="
            isUrgent
              ? 'bg-[var(--k-warn-soft)] text-[var(--k-danger)]'
              : 'bg-[var(--k-chip)] text-[var(--k-text)]'
          "
        >
          <svg
            class="h-4 w-4 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
          >
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 2" />
          </svg>
          <span class="text-sm font-semibold">{{ clock }}</span>
        </div>

        <button
          v-if="!isSubmitted"
          class="rounded-lg bg-[var(--k-accent)] px-4 py-1.5 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
          :disabled="submitting"
          @click="confirmSubmit = true"
        >
          交卷
        </button>
      </header>

      <p
        v-if="error"
        class="rounded-lg border border-[var(--k-danger)]/30 bg-[var(--k-warn-soft)] px-4 py-2 text-xs text-[var(--k-danger)]"
      >
        {{ error }}
      </p>

      <!-- 题号导航 -->
      <nav class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-4">
        <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">答题卡</p>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="q in choiceItems"
            :key="q.id"
            class="grid h-7 w-7 place-items-center rounded-md text-[11px] font-medium transition"
            :class="
              answers[q.id]
                ? 'bg-[var(--k-accent)] text-[var(--k-on-accent)]'
                : 'border border-[var(--k-border)] text-[var(--k-text-muted)] hover:border-[var(--k-accent)]'
            "
            :title="`第 ${q.number} 题${answers[q.id] ? '：' + answers[q.id] : '：未作答'}`"
            @click="jumpTo(q.id)"
          >
            {{ q.number }}
          </button>
        </div>
      </nav>

      <!-- 选择题 -->
      <section class="space-y-4">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">一、单项选择题（共 40 小题，每小题 2 分）</h2>

        <article
          v-for="q in choiceItems"
          :id="'q-' + q.id"
          :key="q.id"
          class="scroll-mt-24 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5"
        >
          <div class="mb-2 flex flex-wrap items-center gap-2 text-[11px] text-[var(--k-text-faint)]">
            <span class="font-semibold text-[var(--k-text)]">第 {{ q.number }} 题</span>
            <span>{{ subjectName(q.subject) }}</span>
            <span v-if="q.chapterName">· {{ q.chapterName }}</span>
            <span class="ml-auto">{{ q.score }} 分</span>
            <span v-if="saving[q.id]" class="text-[var(--k-text-faint)]">保存中…</span>
          </div>

          <StemMarkdown class="mt-1" :source="q.stem" />

          <div class="mt-3 space-y-1.5">
            <button
              v-for="opt in q.options"
              :key="opt.key"
              class="flex w-full items-start gap-2.5 rounded-lg border px-3 py-2 text-left text-sm transition"
              :class="
                answers[q.id] === opt.key
                  ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] text-[var(--k-text)]'
                  : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:border-[var(--k-border-strong)]'
              "
              :disabled="isSubmitted"
              @click="pick(q, opt.key)"
            >
              <span
                class="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded text-[11px] font-semibold"
                :class="
                  answers[q.id] === opt.key
                    ? 'bg-[var(--k-accent)] text-[var(--k-on-accent)]'
                    : 'bg-[var(--k-chip)] text-[var(--k-text-muted)]'
                "
              >
                {{ opt.key }}
              </span>
              <StemMarkdown class="min-w-0 flex-1" :source="opt.text" />
            </button>
          </div>
        </article>
      </section>

      <!-- 综合题 -->
      <section class="space-y-4">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">二、综合应用题（共 7 小题，共 70 分）</h2>

        <article
          v-for="q in comprehensiveItems"
          :id="'q-' + q.id"
          :key="q.id"
          class="scroll-mt-24 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5"
        >
          <div class="mb-2 flex flex-wrap items-center gap-2 text-[11px] text-[var(--k-text-faint)]">
            <span class="font-semibold text-[var(--k-text)]">第 {{ q.number }} 题</span>
            <span>{{ subjectName(q.subject) }}</span>
            <span class="ml-auto">{{ q.score }} 分</span>
          </div>

          <StemMarkdown class="mt-1" :source="q.stem" />

          <div v-if="q.subQuestions.length" class="mt-3 space-y-2">
            <div
              v-for="sub in q.subQuestions"
              :key="sub.subNumber"
              class="rounded-lg bg-[var(--k-surface-soft)] px-3 py-2 text-sm text-[var(--k-text-muted)]"
            >
              <span class="font-semibold text-[var(--k-text)]">({{ sub.subNumber }})</span>
              <span class="ml-1.5">{{ sub.description }}</span>
              <span v-if="sub.score != null" class="ml-2 text-[11px] text-[var(--k-text-faint)]">
                {{ sub.score }} 分
              </span>
            </div>
          </div>

          <p class="mt-3 text-[11px] text-[var(--k-text-faint)]">
            综合题为主观题，交卷后对照参考答案自评得分。
          </p>
        </article>
      </section>

      <!-- 底部交卷 -->
      <div v-if="!isSubmitted" class="flex justify-center pb-8">
        <button
          class="rounded-xl bg-[var(--k-accent)] px-8 py-3 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
          :disabled="submitting"
          @click="confirmSubmit = true"
        >
          交卷并查看成绩
        </button>
      </div>

      <!-- 交卷确认 -->
      <div
        v-if="confirmSubmit"
        class="fixed inset-0 z-40 grid place-items-center bg-black/50 p-4"
        @click.self="confirmSubmit = false"
      >
        <div class="w-full max-w-sm space-y-4 rounded-2xl bg-[var(--k-surface)] p-6 shadow-xl">
          <h3 class="text-base font-bold text-[var(--k-text)]">确认交卷？</h3>
          <p class="text-sm leading-relaxed text-[var(--k-text-muted)]">
            你已作答
            <strong class="text-[var(--k-text)]">{{ answeredCount }}</strong>
            /
            {{ choiceItems.length }}
            道选择题。
            <span v-if="answeredCount < choiceItems.length" class="text-[var(--k-danger)]">
              还有 {{ choiceItems.length - answeredCount }} 道未作答。
            </span>
          </p>
          <p class="text-xs text-[var(--k-text-faint)]">交卷后选择题不能再修改。</p>
          <div class="flex gap-2">
            <button
              class="flex-1 rounded-lg border border-[var(--k-border)] px-4 py-2 text-sm text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)]"
              @click="confirmSubmit = false"
            >
              继续答题
            </button>
            <button
              class="flex-1 rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
              :disabled="submitting"
              @click="doSubmit(false)"
            >
              {{ submitting ? '交卷中…' : '确认交卷' }}
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>