<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import StemMarkdown from '@/components/mock/StemMarkdown.vue'
import type { MockQuestion, MockState } from '@/api/types'

const route = useRoute()
const router = useRouter()

const sessionId = Number(route.params.sessionId)
const state = ref<MockState | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
/** 展开解析的题 */
const expanded = ref<Set<string>>(new Set())
/** 自评保存中 */
const grading = ref<Record<string, boolean>>({})

const subjectName = (code: string) =>
  ({ ds: '数据结构', os: '操作系统', co: '计算机组成原理', cn: '计算机网络' })[code] ?? code

const items = computed(() => state.value?.items ?? [])
const choiceItems = computed(() => items.value.filter((i) => i.questionType === 'choice'))
const comprehensiveItems = computed(() =>
  items.value.filter((i) => i.questionType === 'comprehensive'),
)

/** 选择题得分与满分的对比 */
const choiceAnswered = computed(
  () => choiceItems.value.filter((i) => i.correct != null).length,
)
const choiceCorrect = computed(
  () => choiceItems.value.filter((i) => i.correct === 1).length,
)
const choiceWrong = computed(
  () => choiceItems.value.filter((i) => i.correct === 0).length,
)

const autoScore = computed(() => state.value?.autoScore ?? 0)
const selfScore = computed(() => state.value?.selfScore ?? 0)
const totalScore = computed(() => autoScore.value + selfScore.value)
const fullScore = 150

/** 用时 */
const usedMinutes = computed(() => {
  if (!state.value?.submittedAt) return 0
  const start = new Date(state.value.startedAt).getTime()
  const end = new Date(state.value.submittedAt).getTime()
  return Math.max(0, Math.round((end - start) / 60000))
})

/** 白卷警告：一道选择题都没答 */
const isBlank = computed(() => choiceAnswered.value === 0)

function toggle(id: string) {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
}

/** 综合题自评：选一个分数档 */
async function grade(q: MockQuestion, score: number) {
  grading.value = { ...grading.value, [q.id]: true }
  try {
    const res = await api.mockGrade(sessionId, q.id, score)
    if (state.value) {
      state.value.selfScore = res.sessionSelfScore
      const target = state.value.items.find((i) => i.id === q.id)
      if (target) target.selfScore = res.selfScore
      // 触发响应式更新
      state.value = { ...state.value }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '自评保存失败'
  } finally {
    grading.value = { ...grading.value, [q.id]: false }
  }
}

async function load() {
  loading.value = true
  try {
    state.value = await api.mockState(sessionId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <div v-if="loading" class="grid min-h-[60vh] place-items-center">
      <p class="text-sm text-[var(--k-text-muted)]">正在载入成绩…</p>
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
      <!-- 成绩总览 -->
      <section
        class="rounded-2xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] p-6 text-center"
      >
        <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">
          {{ state.examYear }} 年 408 模拟考试
          <span v-if="state.timedOut" class="ml-1 text-[var(--k-danger)]">（超时自动交卷）</span>
        </p>

        <div class="mt-3 flex items-end justify-center gap-1">
          <span class="text-5xl font-bold tabular-nums text-[var(--k-text)]">{{ totalScore }}</span>
          <span class="mb-2 text-lg text-[var(--k-text-faint)]">/ {{ fullScore }}</span>
        </div>

        <p class="mt-1 text-xs text-[var(--k-text-muted)]">
          用时 {{ usedMinutes }} 分钟
          <span v-if="state.durationMinutes > 0"> · 限时 {{ state.durationMinutes }} 分钟</span>
        </p>

        <p
          v-if="isBlank"
          class="mx-auto mt-3 max-w-md rounded-lg bg-[var(--k-warn-soft)] px-3 py-2 text-xs text-[var(--k-danger)]"
        >
          本场没有作答任何选择题，得分仅反映未作答状态。
        </p>

        <!-- 分项得分 -->
        <div class="mx-auto mt-5 grid max-w-md grid-cols-2 gap-3">
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-3">
            <p class="text-lg font-bold tabular-nums text-[var(--k-text)]">
              {{ autoScore }}<span class="text-xs font-normal text-[var(--k-text-faint)]">/80</span>
            </p>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">选择题（自动判分）</p>
          </div>
          <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-3">
            <p class="text-lg font-bold tabular-nums text-[var(--k-text)]">
              {{ selfScore }}<span class="text-xs font-normal text-[var(--k-text-faint)]">/70</span>
            </p>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">综合题（自评）</p>
          </div>
        </div>

        <div class="mt-4 flex flex-wrap justify-center gap-2 text-[11px]">
          <span class="rounded-full bg-[#0f7a4d]/10 px-2.5 py-1 text-[#0f7a4d] dark:text-[#4ade80]">
            答对 {{ choiceCorrect }}
          </span>
          <span class="rounded-full bg-[#b23c3c]/10 px-2.5 py-1 text-[#b23c3c] dark:text-[#f87171]">
            答错 {{ choiceWrong }}
          </span>
          <span class="rounded-full bg-[var(--k-chip)] px-2.5 py-1 text-[var(--k-text-muted)]">
            未答 {{ choiceItems.length - choiceAnswered }}
          </span>
        </div>
      </section>

      <!-- 综合题自评 -->
      <section class="space-y-3">
        <div>
          <h2 class="text-sm font-bold text-[var(--k-heading)]">综合题自评</h2>
          <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
            对照参考答案给自己打分，计入总分（满分 70）。
          </p>
        </div>

        <article
          v-for="q in comprehensiveItems"
          :key="q.id"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5"
        >
          <div class="mb-2 flex flex-wrap items-center gap-2 text-[11px] text-[var(--k-text-faint)]">
            <span class="font-semibold text-[var(--k-text)]">第 {{ q.number }} 题</span>
            <span>{{ subjectName(q.subject) }}</span>
            <span class="ml-auto">{{ q.score }} 分</span>
          </div>

          <StemMarkdown :source="q.stem" />

          <div v-if="q.subQuestions.length" class="mt-2 space-y-1">
            <p
              v-for="sub in q.subQuestions"
              :key="sub.subNumber"
              class="rounded-lg bg-[var(--k-surface-soft)] px-3 py-1.5 text-xs text-[var(--k-text-muted)]"
            >
              <span class="font-semibold text-[var(--k-text)]">({{ sub.subNumber }})</span>
              <span class="ml-1.5">{{ sub.description }}</span>
            </p>
          </div>

          <button
            class="mt-3 text-[11px] text-[var(--k-accent-text)] hover:underline"
            @click="toggle(q.id)"
          >
            {{ expanded.has(q.id) ? '收起参考答案' : '查看参考答案与解析' }}
          </button>

          <div
            v-if="expanded.has(q.id)"
            class="mt-2 space-y-2 rounded-lg bg-[var(--k-surface-soft)] p-3 text-xs leading-relaxed"
          >
            <div>
              <p class="font-semibold text-[var(--k-text)]">参考答案</p>
              <p class="mt-1 whitespace-pre-wrap text-[var(--k-text-muted)]">
                {{ q.standardAnswer || '（本题未提供参考答案）' }}
              </p>
            </div>
            <div v-if="q.explanation">
              <p class="font-semibold text-[var(--k-text)]">解析</p>
              <p class="mt-1 whitespace-pre-wrap text-[var(--k-text-muted)]">{{ q.explanation }}</p>
            </div>
          </div>

          <!-- 自评打分 -->
          <div class="mt-3 flex flex-wrap items-center gap-2">
            <span class="text-[11px] text-[var(--k-text-faint)]">自评</span>
            <button
              v-for="opt in [
                { label: '0', value: 0 },
                { label: '一半', value: Math.round(q.score / 2) },
                { label: '满分', value: q.score },
              ]"
              :key="opt.label"
              class="rounded-lg border px-3 py-1 text-[11px] transition disabled:opacity-50"
              :class="
                q.selfScore === opt.value
                  ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] text-[var(--k-accent-text)] font-semibold'
                  : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:border-[var(--k-accent)]'
              "
              :disabled="grading[q.id]"
              @click="grade(q, opt.value)"
            >
              {{ opt.label }}（{{ opt.value }} 分）
            </button>
            <span v-if="q.selfScore != null" class="text-[11px] text-[var(--k-text-faint)]">
              已给 {{ q.selfScore }} 分
            </span>
            <span v-if="grading[q.id]" class="text-[11px] text-[var(--k-text-faint)]">保存中…</span>
          </div>
        </article>
      </section>

      <!-- 选择题复盘 -->
      <section class="space-y-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">选择题复盘</h2>

        <article
          v-for="q in choiceItems"
          :key="q.id"
          class="rounded-xl border bg-[var(--k-surface)] p-4"
          :class="
            q.correct === 1
              ? 'border-[#0f7a4d]/30'
              : q.correct === 0
                ? 'border-[#b23c3c]/30'
                : 'border-[var(--k-border)]'
          "
        >
          <div class="flex flex-wrap items-center gap-2 text-[11px]">
            <span
              class="grid h-5 w-5 place-items-center rounded text-[10px] font-bold text-white"
              :class="
                q.correct === 1
                  ? 'bg-[#0f7a4d]'
                  : q.correct === 0
                    ? 'bg-[#b23c3c]'
                    : 'bg-[var(--k-border-strong)]'
              "
            >
              {{ q.correct === 1 ? '✓' : q.correct === 0 ? '✕' : '—' }}
            </span>
            <span class="font-semibold text-[var(--k-text)]">第 {{ q.number }} 题</span>
            <span class="text-[var(--k-text-faint)]">{{ subjectName(q.subject) }}</span>
            <span class="ml-auto text-[var(--k-text-faint)]">
              标准答案 <strong class="text-[var(--k-text)]">{{ q.standardAnswer }}</strong>
              <span v-if="q.myAnswer" class="ml-2">
                · 我的 <strong class="text-[var(--k-text)]">{{ q.myAnswer }}</strong>
              </span>
              <span v-else class="ml-2 text-[var(--k-text-faint)]">· 未作答</span>
            </span>
          </div>

          <StemMarkdown class="mt-2" :source="q.stem" />

          <button
            class="mt-2 text-[11px] text-[var(--k-accent-text)] hover:underline"
            @click="toggle(q.id)"
          >
            {{ expanded.has(q.id) ? '收起选项与解析' : '展开选项与解析' }}
          </button>

          <div v-if="expanded.has(q.id)" class="mt-2 space-y-2">
            <div class="space-y-1">
              <p
                v-for="opt in q.options"
                :key="opt.key"
                class="rounded px-2 py-1 text-xs"
                :class="
                  opt.key === q.standardAnswer
                    ? 'bg-[#0f7a4d]/10 text-[var(--k-text)]'
                    : opt.key === q.myAnswer
                      ? 'bg-[#b23c3c]/10 text-[var(--k-text)]'
                      : 'text-[var(--k-text-muted)]'
                "
              >
                <span class="font-semibold">{{ opt.key }}.</span> <StemMarkdown class="inline-block align-top" :source="opt.text" />
              </p>
            </div>
            <p
              v-if="q.explanation"
              class="rounded bg-[var(--k-surface-soft)] p-2 text-xs leading-relaxed whitespace-pre-wrap text-[var(--k-text-muted)]"
            >
              {{ q.explanation }}
            </p>
          </div>
        </article>
      </section>

      <!-- 底部操作 -->
      <div class="flex flex-wrap justify-center gap-2 pb-8">
        <RouterLink
          to="/exams"
          class="rounded-lg border border-[var(--k-border)] px-5 py-2.5 text-sm text-[var(--k-text-muted)] no-underline transition hover:bg-[var(--k-surface-soft)]"
        >
          返回真题卷
        </RouterLink>
        <RouterLink
          :to="`/mock/start?year=${state.examYear}`"
          class="rounded-lg bg-[var(--k-accent)] px-5 py-2.5 text-sm font-semibold text-white no-underline transition hover:bg-[var(--k-accent-hover)]"
        >
          再考一次
        </RouterLink>
      </div>
    </template>
  </div>
</template>