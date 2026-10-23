<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import type { PracticeQueue, QuestionView } from '@/api/types'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

const queue = ref<PracticeQueue | null>(null)
const question = ref<QuestionView | null>(null)
const index = ref(0)
const selected = ref('')
const result = ref<{
  correct: boolean
  standardAnswer: string | null
  explanation: string | null
} | null>(null)
const loadingQueue = ref(true)
const loadingQuestion = ref(false)
const submitting = ref(false)
const message = ref<string | null>(null)

const current = computed(() => queue.value?.items[index.value] ?? null)
const scope = computed(() => (typeof route.query.scope === 'string' ? route.query.scope : 'all'))
const yearParam = computed(() =>
  typeof route.query.year === 'string' ? Number(route.query.year) : undefined,
)
const subjectParam = computed(() =>
  typeof route.query.subject === 'string' ? route.query.subject : undefined,
)
const blockIdParam = computed(() =>
  typeof route.query.blockId === 'string' ? route.query.blockId : undefined,
)
const pointIdParam = computed(() =>
  typeof route.query.pointId === 'string' ? route.query.pointId : undefined,
)
/** 从搜索结果直接跳到某一道题：?examRef=exam-2026-3 */
const examRefParam = computed(() =>
  typeof route.query.examRef === 'string' ? route.query.examRef : undefined,
)

const scopeLabel = computed(() => {
  if (examRefParam.value) return '搜索结果'
  if (scope.value === 'wrong') return '错题本'
  if (scope.value === 'untouched') return '未做的题'
  if (blockIdParam.value || pointIdParam.value) return '知识点练习'
  if (yearParam.value) return `${yearParam.value} 年真题`
  if (subjectParam.value) return subjectName(subjectParam.value)
  return '全部题目'
})

const progressText = computed(() =>
  queue.value ? `${index.value + 1} / ${queue.value.total}` : '—',
)

function subjectName(code: string): string {
  return (
    { ds: '数据结构', os: '操作系统', co: '计算机组成原理', cn: '计算机网络' }[code] ?? code
  )
}

/** 载入队列：按 URL 参数筛选；带 examRef 时只放这一道题。 */
async function loadQueue() {
  loadingQueue.value = true
  message.value = null
  try {
    if (examRefParam.value) {
      // 搜索直达单题：取所在年份的队列后定位到该题，
      // 这样仍可用上一题/下一题在同年份里继续刷。
      const detail = await api.question(examRefParam.value)
      const yearQueue = await api.practiceQueue({ year: detail.year ?? undefined })
      const found = yearQueue.items.findIndex((i) => i.examRef === examRefParam.value)
      queue.value = yearQueue
      index.value = found >= 0 ? found : 0
      await loadQuestion()
      return
    }

    queue.value = await api.practiceQueue({
      year: yearParam.value,
      subject: subjectParam.value,
      blockId: blockIdParam.value,
      pointId: pointIdParam.value,
      scope: scope.value as 'all' | 'wrong' | 'untouched',
    })
    index.value = 0
    await loadQuestion()
  } catch (e) {
    queue.value = null
    message.value = e instanceof Error ? e.message : '加载队列失败'
  } finally {
    loadingQueue.value = false
  }
}

/** 载入当前题的完整内容（未作答时后端不下发解析）。 */
async function loadQuestion() {
  const item = current.value
  if (!item) {
    question.value = null
    return
  }
  loadingQuestion.value = true
  selected.value = ''
  result.value = null
  try {
    question.value = await api.question(item.examRef)
  } catch (e) {
    question.value = null
    message.value = e instanceof Error ? e.message : '加载题目失败'
  } finally {
    loadingQuestion.value = false
  }
}

async function submit() {
  const item = current.value
  if (!item || submitting.value) return
  if (question.value?.options.length && !selected.value) {
    message.value = '请先选择一个选项。'
    return
  }
  submitting.value = true
  message.value = null
  try {
    const answer = await api.submitAnswer(item.examRef, selected.value || '已完成')
    result.value = {
      correct: answer.correct,
      standardAnswer: answer.standardAnswer,
      explanation: answer.explanation,
    }
    // 同步队列内的作答状态，让右侧导航与进度即时更新
    const target = queue.value?.items[index.value]
    if (queue.value && target) {
      if (!target.answered) queue.value.answered += 1
      target.answered = true
      target.correct = answer.correct
      queue.value.correct = queue.value.items.filter((i) => i.correct).length
    }
    await userStore.loadDashboard()
  } catch (e) {
    message.value = e instanceof Error ? e.message : '提交失败'
  } finally {
    submitting.value = false
  }
}

async function go(delta: number) {
  if (!queue.value) return
  const next = index.value + delta
  if (next < 0 || next >= queue.value.items.length) return
  index.value = next
  await loadQuestion()
}

async function jumpTo(target: number) {
  index.value = target
  await loadQuestion()
}

onMounted(loadQueue)

watch(
  () => [
    route.query.scope,
    route.query.year,
    route.query.subject,
    route.query.blockId,
    route.query.pointId,
    route.query.examRef,
  ],
  () => {
    void loadQueue()
  },
)
</script>

<template>
  <div class="space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">刷题</h1>
        <p class="text-sm text-[var(--k-text-muted)]">
          {{ scopeLabel }}
          <span v-if="queue" class="text-[var(--k-text-faint)]">
            · 共 {{ queue.total }} 题 · 已答 {{ queue.answered }} · 正确 {{ queue.correct }}
          </span>
        </p>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span class="tabular-nums text-[var(--k-text-faint)]">{{ progressText }}</span>
        <RouterLink
          to="/dashboard"
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] no-underline transition hover:bg-[var(--k-surface-soft)]"
        >
          返回工作台
        </RouterLink>
      </div>
    </header>

    <p v-if="loadingQueue" class="py-10 text-center text-sm text-[var(--k-text-faint)]">加载中…</p>

    <div
      v-else-if="!queue || queue.total === 0"
      class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-12 text-center"
    >
      <p class="text-sm text-[var(--k-text-muted)]">
        {{ scope === 'wrong' ? '错题本是空的 —— 还没有答错的题。' : '该范围内暂无题目。' }}
      </p>
      <RouterLink
        to="/dashboard"
        class="mt-4 inline-block rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-white no-underline hover:bg-[var(--k-accent-hover)]"
      >
        回到工作台
      </RouterLink>
    </div>

    <div v-else class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_220px]">
      <div class="min-w-0 space-y-4">
        <p
          v-if="message"
          class="rounded-lg border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-sm text-[var(--k-warn)]"
        >
          {{ message }}
        </p>

        <article
          v-if="question"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-6"
        >
          <div class="mb-4 flex flex-wrap items-center gap-2 text-xs text-[var(--k-text-faint)]">
            <span
              class="rounded bg-[var(--k-chip)] px-2 py-0.5 font-medium text-[var(--k-text-muted)]"
            >
              {{ question.id }}
            </span>
            <span>{{ current?.year }} 年第 {{ question.number }} 题</span>
            <span>{{ subjectName(question.subject) }}</span>
            <span v-if="question.chapterName">{{ question.chapterName }}</span>
            <span class="font-semibold text-[var(--k-accent-text)]">{{ question.score }} 分</span>
            <span
              v-if="current?.answered"
              class="rounded px-2 py-0.5 font-semibold"
              :class="current.correct ? 'bg-[#0f7a4d]/12 text-[#0f7a4d]' : 'bg-[#b23c3c]/12 text-[#b23c3c]'"
            >
              {{ current.correct ? '已答对' : '曾答错' }}
            </span>
          </div>

          <pre
            class="whitespace-pre-wrap font-sans text-[15px] leading-[1.9] text-[var(--k-text)]"
            >{{ question.stem }}</pre
          >

          <img
            v-for="path in question.imagePaths"
            :key="path"
            :src="path"
            :alt="`题目配图 ${question.number}`"
            class="mt-4 max-w-full rounded-lg border border-[var(--k-border)]"
          />

          <div v-if="question.options.length" class="mt-5 space-y-2">
            <label
              v-for="option in question.options"
              :key="option.key"
              class="flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-2.5 text-[15px] transition"
              :class="
                selected === option.key
                  ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)]'
                  : 'border-[var(--k-border)] hover:border-[var(--k-accent)]'
              "
            >
              <input v-model="selected" type="radio" :value="option.key" class="mt-1.5" />
              <span class="text-[var(--k-text)]">
                <strong class="mr-1">{{ option.key }}.</strong>{{ option.text }}
              </span>
            </label>
          </div>

          <div v-else class="mt-5 space-y-2">
            <p class="text-xs text-[var(--k-text-faint)]">
              综合题：当前记为「已完成」，自动评分在后续里程碑实现。
            </p>
            <div
              v-for="sub in question.subQuestions"
              :key="sub.number"
              class="rounded-lg bg-[var(--k-surface-soft)] px-4 py-2.5 text-sm text-[var(--k-text)]"
            >
              <span class="font-semibold">({{ sub.number }})</span> {{ sub.description }}
              <span v-if="sub.score" class="text-xs text-[var(--k-text-faint)]">
                （{{ sub.score }} 分）
              </span>
            </div>
          </div>

          <div class="mt-6 flex flex-wrap items-center gap-3">
            <button
              class="rounded-lg bg-[var(--k-accent)] px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
              :disabled="submitting"
              @click="submit"
            >
              {{ submitting ? '提交中…' : result ? '再次提交' : '提交答案' }}
            </button>

            <span
              v-if="result"
              class="text-sm font-semibold"
              :class="result.correct ? 'text-[#0f7a4d]' : 'text-[#b23c3c]'"
            >
              {{ result.correct ? '回答正确' : '回答错误' }}
              <span v-if="result.standardAnswer" class="font-normal text-[var(--k-text-faint)]">
                （正确答案 {{ result.standardAnswer }}）
              </span>
            </span>

            <div class="ml-auto flex items-center gap-2">
              <button
                class="rounded-lg border border-[var(--k-border)] px-4 py-2 text-sm text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-40"
                :disabled="index === 0"
                @click="go(-1)"
              >
                ← 上一题
              </button>
              <button
                class="rounded-lg border border-[var(--k-border)] px-4 py-2 text-sm text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-40"
                :disabled="!queue || index >= queue.items.length - 1"
                @click="go(1)"
              >
                下一题 →
              </button>
            </div>
          </div>

          <div
            v-if="result?.explanation"
            class="mt-5 rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-4"
          >
            <p class="mb-1 text-xs font-semibold text-[var(--k-text-faint)]">解析</p>
            <pre
              class="prose-code whitespace-pre-wrap font-sans text-sm leading-relaxed text-[var(--k-text-muted)]"
              >{{ result.explanation }}</pre
            >
          </div>

          <div
            v-if="question.knowledgeBlockIds.length"
            class="mt-4 text-xs text-[var(--k-text-faint)]"
          >
            关联知识块：{{ question.knowledgeBlockIds.join('、') }}
          </div>
        </article>

        <p v-else-if="loadingQuestion" class="py-10 text-center text-sm text-[var(--k-text-faint)]">
          加载题目…
        </p>
      </div>

      <aside class="space-y-3">
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-3">
          <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">题目导航</p>
          <div class="grid grid-cols-6 gap-1.5 lg:grid-cols-5">
            <button
              v-for="(item, i) in queue.items"
              :key="item.examRef"
              class="grid h-8 place-items-center rounded text-[11px] font-semibold tabular-nums transition"
              :class="[
                i === index
                  ? 'ring-2 ring-[var(--k-accent)] ring-offset-1 ring-offset-[var(--k-surface)]'
                  : '',
                item.correct
                  ? 'bg-[#0f7a4d]/15 text-[#0f7a4d]'
                  : item.answered
                    ? 'bg-[#b23c3c]/15 text-[#b23c3c]'
                    : 'bg-[var(--k-chip)] text-[var(--k-text-faint)]',
              ]"
              :title="`第 ${item.number} 题`"
              @click="jumpTo(i)"
            >
              {{ item.number }}
            </button>
          </div>
          <div class="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-[10px] text-[var(--k-text-faint)]">
            <span class="inline-flex items-center gap-1">
              <i class="h-2.5 w-2.5 rounded-sm bg-[#0f7a4d]/30" />已答对
            </span>
            <span class="inline-flex items-center gap-1">
              <i class="h-2.5 w-2.5 rounded-sm bg-[#b23c3c]/30" />曾答错
            </span>
            <span class="inline-flex items-center gap-1">
              <i class="h-2.5 w-2.5 rounded-sm bg-[var(--k-chip)]" />未做
            </span>
          </div>
        </div>

        <div
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-3 text-[11px] text-[var(--k-text-faint)]"
        >
          <p class="mb-1.5 font-semibold">当前范围</p>
          <p class="leading-relaxed">
            {{ scopeLabel }}
            <template v-if="blockIdParam || pointIdParam">
              <br />{{ blockIdParam || pointIdParam }}
            </template>
          </p>
        </div>
      </aside>
    </div>
  </div>
</template>