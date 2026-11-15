<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { ExamDetail, QuestionView } from '@/api/types'
import { useUserStore } from '@/store/user'
import { subjectMeta } from '@/constants/subjects'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const exam = ref<ExamDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
/** 已展开解析的题号集合 */
const revealed = ref<Set<number>>(new Set())
/** 左侧目录的当前高亮 */
const activeNumber = ref(1)

const year = computed(() => Number(route.params.year))

const choiceQuestions = computed(
  () => exam.value?.questions.filter((q) => q.questionType === 'choice') ?? [],
)
const comprehensiveQuestions = computed(
  () => exam.value?.questions.filter((q) => q.questionType !== 'choice') ?? [],
)

const choiceTotal = computed(() => choiceQuestions.value.reduce((s, q) => s + q.score, 0))
const comprehensiveTotal = computed(() =>
  comprehensiveQuestions.value.reduce((s, q) => s + q.score, 0),
)
const grandTotal = computed(() => choiceTotal.value + comprehensiveTotal.value)

/**
 * 408 规范分值为选择 80 + 综合 70 = 150。
 * 个别年份的源数据综合题分值合计不等于 70（已知缺陷，按用户要求保留源值），
 * 这里如实提示而不是悄悄改数。
 */
const scoreAnomaly = computed(() => {
  if (!exam.value) return null
  const parts: string[] = []
  if (comprehensiveTotal.value !== 70) {
    parts.push(`综合题合计 ${comprehensiveTotal.value} 分（规范应为 70 分）`)
  }
  if (grandTotal.value !== 150) {
    parts.push(`全卷合计 ${grandTotal.value} 分（规范应为 150 分）`)
  }
  return parts.length ? parts.join('；') : null
})

function subjectOf(code: string) {
  return subjectMeta[code] ?? { name: code, bar: 'bg-[var(--k-text-faint)]', soft: 'bg-[var(--k-chip)]' }
}

/**
 * 答案与解析的加载。
 *
 * 后端刻意不在整卷接口里下发答案（见 ExamController 注释），
 * 且单题接口只在「该用户已作答」后才会给出解析——这是防作弊规则，不应绕过。
 * 因此这里按题按需拉取：已作答过的题可以直接看到解析，没做过的会提示先去作答。
 */
const answerCache = ref<Record<string, { answer: string | null; explanation: string | null }>>({})
const answerState = ref<Record<string, 'loading' | 'ready' | 'locked'>>({})

async function toggleReveal(question: QuestionView) {
  const number = question.number
  if (revealed.value.has(number)) {
    const next = new Set(revealed.value)
    next.delete(number)
    revealed.value = next
    return
  }

  const next = new Set(revealed.value)
  next.add(number)
  revealed.value = next

  if (answerCache.value[question.id] || answerState.value[question.id] === 'loading') return

  answerState.value = { ...answerState.value, [question.id]: 'loading' }
  try {
    const detail = await api.question(question.id, 1)
    if (detail.answer || detail.explanation) {
      answerCache.value = {
        ...answerCache.value,
        [question.id]: { answer: detail.answer ?? null, explanation: detail.explanation ?? null },
      }
      answerState.value = { ...answerState.value, [question.id]: 'ready' }
    } else {
      // 未作答：后端不下发解析
      answerState.value = { ...answerState.value, [question.id]: 'locked' }
    }
  } catch {
    answerState.value = { ...answerState.value, [question.id]: 'locked' }
  }
}

function scrollTo(number: number) {
  activeNumber.value = number
  document.getElementById(`q-${number}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function load() {
  loading.value = true
  error.value = null
  revealed.value = new Set()
  try {
    exam.value = await api.exam(year.value)
  } catch (e) {
    exam.value = null
    error.value = e instanceof Error ? e.message : '该年份试卷不存在'
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(
  () => route.params.year,
  (next, prev) => {
    if (next !== prev) {
      void load()
      void router.replace({ path: `/exams/${next}` })
    }
  },
)
</script>

<template>
  <div class="space-y-5">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <div class="flex items-baseline gap-3">
          <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">
            {{ year }} 年真题
          </h1>
          <RouterLink
            to="/exams"
            class="text-xs text-[var(--k-accent)] no-underline hover:underline"
          >
            全部年份 →
          </RouterLink>
        </div>
        <p v-if="exam" class="text-sm text-[var(--k-text-muted)]">
          {{ exam.questionCount }} 题 · 选择 {{ choiceQuestions.length }} 题 /
          {{ choiceTotal }} 分 · 综合 {{ comprehensiveQuestions.length }} 题 /
          {{ comprehensiveTotal }} 分 · 合计 {{ grandTotal }} 分
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-[11px] text-[var(--k-text-faint)]">答案需先作答后可见</span>
        <button
          class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)]"
          @click="revealed = new Set()"
        >
          收起全部
        </button>
      </div>
    </header>

    <!-- 数据缺陷如实提示 -->
    <p
      v-if="scoreAnomaly"
      class="rounded-xl border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-xs leading-relaxed text-[var(--k-warn)]"
    >
      分值提示：{{ scoreAnomaly }}。本站数据与 408os 保持一致，未做人工修正。
    </p>

    <p v-if="loading" class="py-10 text-center text-sm text-[var(--k-text-faint)]">加载中…</p>
    <p
      v-else-if="error"
      class="rounded-xl border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-sm text-[var(--k-warn)]"
    >
      {{ error }}
    </p>

    <div v-else-if="exam" class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_200px]">
      <!-- 试卷正文 -->
      <div class="min-w-0 space-y-8">
        <!-- 选择题 -->
        <section class="space-y-4">
          <h2 class="flex items-center gap-2 text-sm font-bold text-[var(--k-heading)]">
            一、单项选择题
            <span class="text-xs font-normal text-[var(--k-text-faint)]">
              第 1–{{ choiceQuestions.length }} 题，每题 2 分，共 {{ choiceTotal }} 分
            </span>
          </h2>

          <article
            v-for="question in choiceQuestions"
            :id="`q-${question.number}`"
            :key="question.id"
            class="scroll-mt-20 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-5 py-4"
          >
            <div class="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
              <span class="font-bold tabular-nums text-[var(--k-text)]">
                {{ question.number }}.
              </span>
              <span
                class="rounded px-1.5 py-0.5 font-medium"
                :class="[subjectOf(question.subject).soft, 'text-[var(--k-text-muted)]']"
              >
                {{ subjectOf(question.subject).name }}
              </span>
              <span v-if="question.chapterName" class="text-[var(--k-text-faint)]">
                {{ question.chapterName }}
              </span>
              <span class="text-[var(--k-text-faint)]">{{ question.score }} 分</span>
            </div>

            <pre
              class="whitespace-pre-wrap font-sans text-[15px] leading-[1.9] text-[var(--k-text)]"
              >{{ question.stem }}</pre
            >

            <img
              v-for="path in question.imagePaths"
              :key="path"
              :src="path"
              :alt="`第 ${question.number} 题配图`"
              class="mt-3 max-w-full rounded-lg border border-[var(--k-border)]"
              loading="lazy"
            />

            <div class="mt-3 grid gap-1.5 sm:grid-cols-2">
              <p
                v-for="option in question.options"
                :key="option.key"
                class="text-[14px] leading-relaxed text-[var(--k-text-muted)]"
              >
                <strong class="mr-1 text-[var(--k-text)]">{{ option.key }}.</strong>{{ option.text }}
              </p>
            </div>

            <!-- 解析（默认隐藏） -->
            <div class="mt-3 flex flex-wrap items-center gap-3">
              <button
                class="text-xs font-semibold text-[var(--k-accent-text)] transition hover:underline"
                @click="toggleReveal(question)"
              >
                {{ revealed.has(question.number) ? '收起答案与解析' : '查看答案与解析' }}
              </button>
              <RouterLink
                v-if="question.knowledgeBlockIds.length"
                :to="{ path: '/practice', query: { examRef: question.id } }"
                class="text-xs text-[var(--k-text-faint)] no-underline transition hover:text-[var(--k-accent-text)]"
              >
                去刷这道题 →
              </RouterLink>
            </div>

            <div
              v-if="revealed.has(question.number)"
              class="mt-3 rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-4"
            >
              <p
                v-if="answerState[question.id] === 'loading'"
                class="text-xs text-[var(--k-text-faint)]"
              >
                读取中…
              </p>

              <template v-else-if="answerState[question.id] === 'locked'">
                <p class="text-xs leading-relaxed text-[var(--k-text-muted)]">
                  这道题还没有作答记录，答案与解析暂不显示。
                </p>
                <RouterLink
                  :to="{ path: '/practice', query: { examRef: question.id } }"
                  class="mt-2 inline-block rounded-lg bg-[var(--k-accent)] px-3 py-1.5 text-xs font-semibold text-[var(--k-on-accent)] no-underline hover:bg-[var(--k-accent-hover)]"
                >
                  现在去作答
                </RouterLink>
              </template>

              <template v-else-if="answerCache[question.id]">
                <p class="text-sm font-semibold text-[var(--k-text)]">
                  答案：{{ answerCache[question.id].answer ?? '—' }}
                </p>
                <pre
                  v-if="answerCache[question.id].explanation"
                  class="mt-2 whitespace-pre-wrap font-sans text-[13px] leading-relaxed text-[var(--k-text-muted)]"
                  >{{ answerCache[question.id].explanation }}</pre
                >
                <p v-else class="mt-2 text-xs text-[var(--k-text-faint)]">该题暂无解析文本。</p>
              </template>
            </div>
          </article>
        </section>

        <!-- 综合题 -->
        <section v-if="comprehensiveQuestions.length" class="space-y-4">
          <h2 class="flex items-center gap-2 text-sm font-bold text-[var(--k-heading)]">
            二、综合应用题
            <span class="text-xs font-normal text-[var(--k-text-faint)]">
              第 {{ comprehensiveQuestions[0].number }}–{{
                comprehensiveQuestions[comprehensiveQuestions.length - 1].number
              }}
              题，共 {{ comprehensiveTotal }} 分
            </span>
          </h2>

          <article
            v-for="question in comprehensiveQuestions"
            :id="`q-${question.number}`"
            :key="question.id"
            class="scroll-mt-20 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-5 py-4"
          >
            <div class="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
              <span class="font-bold tabular-nums text-[var(--k-text)]">
                {{ question.number }}.
              </span>
              <span
                class="rounded px-1.5 py-0.5 font-medium"
                :class="[subjectOf(question.subject).soft, 'text-[var(--k-text-muted)]']"
              >
                {{ subjectOf(question.subject).name }}
              </span>
              <span v-if="question.chapterName" class="text-[var(--k-text-faint)]">
                {{ question.chapterName }}
              </span>
              <span class="font-semibold text-[var(--k-accent-text)]">{{ question.score }} 分</span>
            </div>

            <pre
              class="whitespace-pre-wrap font-sans text-[15px] leading-[1.9] text-[var(--k-text)]"
              >{{ question.stem }}</pre
            >

            <img
              v-for="path in question.imagePaths"
              :key="path"
              :src="path"
              :alt="`第 ${question.number} 题配图`"
              class="mt-3 max-w-full rounded-lg border border-[var(--k-border)]"
              loading="lazy"
            />

            <div class="mt-3 flex flex-wrap items-center gap-3">
              <button
                class="text-xs font-semibold text-[var(--k-accent-text)] transition hover:underline"
                @click="toggleReveal(question)"
              >
                {{ revealed.has(question.number) ? '收起参考答案' : '查看参考答案' }}
              </button>
              <RouterLink
                :to="{ path: '/practice', query: { examRef: question.id } }"
                class="text-xs text-[var(--k-text-faint)] no-underline transition hover:text-[var(--k-accent-text)]"
              >
                去作答/标记 →
              </RouterLink>
            </div>

            <div
              v-if="revealed.has(question.number)"
              class="mt-3 rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-4"
            >
              <p
                v-if="answerState[question.id] === 'loading'"
                class="text-xs text-[var(--k-text-faint)]"
              >
                读取中…
              </p>
              <p
                v-else-if="answerState[question.id] === 'locked'"
                class="text-xs leading-relaxed text-[var(--k-text-muted)]"
              >
                综合题需先在刷题页标记完成，之后才会显示参考答案。
              </p>
              <template v-else-if="answerCache[question.id]">
                <p
                  v-if="answerCache[question.id].answer"
                  class="text-sm leading-relaxed whitespace-pre-wrap text-[var(--k-text)]"
                >
                  {{ answerCache[question.id].answer }}
                </p>
                <pre
                  v-if="answerCache[question.id].explanation"
                  class="mt-2 whitespace-pre-wrap font-sans text-[13px] leading-relaxed text-[var(--k-text-muted)]"
                  >{{ answerCache[question.id].explanation }}</pre
                >
              </template>
            </div>
          </article>
        </section>
      </div>

      <!-- 题目导航 -->
      <aside class="lg:sticky lg:top-4 lg:self-start">
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-3">
          <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">题目导航</p>
          <div class="grid grid-cols-8 gap-1 lg:grid-cols-5">
            <button
              v-for="question in exam.questions"
              :key="question.id"
              class="grid h-7 place-items-center rounded text-[10px] font-semibold tabular-nums transition"
              :class="
                activeNumber === question.number
                  ? 'ring-2 ring-[var(--k-accent)] bg-[var(--k-chip)] text-[var(--k-text)]'
                  : question.questionType === 'choice'
                    ? 'bg-[var(--k-chip)] text-[var(--k-text-muted)] hover:bg-[var(--k-accent-soft)]'
                    : 'bg-[#f0a020]/15 text-[var(--k-warn)] hover:bg-[#f0a020]/25'
              "
              :title="`第 ${question.number} 题 · ${question.score} 分`"
              @click="scrollTo(question.number)"
            >
              {{ question.number }}
            </button>
          </div>
          <div class="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-[10px] text-[var(--k-text-faint)]">
            <span class="inline-flex items-center gap-1">
              <i class="h-2.5 w-2.5 rounded-sm bg-[var(--k-chip)]" />选择题
            </span>
            <span class="inline-flex items-center gap-1">
              <i class="h-2.5 w-2.5 rounded-sm bg-[#f0a020]/40" />综合题
            </span>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>