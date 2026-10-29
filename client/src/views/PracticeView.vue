<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/api/client'
import StemMarkdown from '@/components/mock/StemMarkdown.vue'
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
/** 游客尝试提交时为 true，用于在提示条里给出「去登录」按钮 */
const needLogin = ref(false)

/* ---------- 视频指引 ---------- */
const videoOpen = ref(false)
const videoLoading = ref(false)
const videoGuide = ref<{
  examRef: string
  subjectName: string
  chapterName: string | null
  points: { title: string; subpointTitle: string | null }[]
  sources: { teacher: string; keyword: string; url: string }[]
} | null>(null)

/**
 * 打开视频弹窗。
 * 用当前题的 examRef 请求「该看哪节课」，服务端按知识点生成 B 站搜索词。
 * 每次打开都重新请求而不是缓存：换题后知识点不同，缓存容易张冠李戴。
 */
async function openVideo() {
  const q = current.value
  if (!q) return
  videoOpen.value = true
  videoGuide.value = null
  videoLoading.value = true
  try {
    // 队列项里题目标识是 examRef（如 exam-2026-1），不是 id
    videoGuide.value = await api.videoGuide(q.examRef)
  } catch (e) {
    message.value = e instanceof Error ? e.message : '视频指引加载失败'
    videoOpen.value = false
  } finally {
    videoLoading.value = false
  }
}
/** 连续答对次数，答错清零；用于顶部正反馈 */
const streak = ref(0)
/** 导航里折叠起来的年份；默认全部展开由 loadQueue 决定 */
const collapsedYears = ref<Set<number>>(new Set())
/** 导航的跳转输入框 */
const jumpInput = ref('')

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
  if (scope.value === 'wrong' || scope.value === 'wrong-pending') return '错题本 · 待订正'
  if (scope.value === 'wrong-mastered') return '错题本 · 已订正'
  if (scope.value === 'untouched') return '未做的题'
  if (blockIdParam.value || pointIdParam.value) return '知识点练习'
  if (yearParam.value) return `${yearParam.value} 年真题`
  if (subjectParam.value) return subjectName(subjectParam.value)
  return '全部题目'
})

/** 当前是否处于错题范围，用于切换「含已订正」 */
const isWrongScope = computed(() =>
  ['wrong', 'wrong-pending', 'wrong-mastered'].includes(scope.value),
)

const progressText = computed(() =>
  queue.value ? `${index.value + 1} / ${queue.value.total}` : '—',
)

/** 顶部统计跟随当前范围，避免切到错题本仍显示「共 846 题」。 */
const statsText = computed(() => {
  if (!queue.value) return '正在加载…'
  const { total, answered, correct } = queue.value
  const accuracy = answered > 0 ? Math.round((correct / answered) * 100) : null
  return (
    `${scopeLabel.value} · 共 ${total} 题 · 已答 ${answered}` +
    (accuracy === null ? '' : ` · 正确率 ${accuracy}%`)
  )
})

/** 顶部进度条百分比（已答 / 总数）。 */
const progressPercent = computed(() => {
  if (!queue.value || queue.value.total === 0) return 0
  return Math.round((queue.value.answered / queue.value.total) * 100)
})

/**
 * 导航按年份分组。
 *
 * 原来把 846 个按钮平铺成一面墙：数字连续排列但实际跨越 18 份试卷，
 * 卷与卷之间没有任何分隔，同一数字还会重复出现（每年都从 1 开始），
 * 用户根本找不到自己在哪。这里改为按年份分组、可折叠。
 */
const navGroups = computed(() => {
  const items = queue.value?.items ?? []
  const groups: Array<{
    year: number
    items: Array<{ item: (typeof items)[number]; index: number }>
    answered: number
    correct: number
  }> = []
  const byYear = new Map<number, (typeof groups)[number]>()
  items.forEach((item, i) => {
    let group = byYear.get(item.year)
    if (!group) {
      group = { year: item.year, items: [], answered: 0, correct: 0 }
      byYear.set(item.year, group)
      groups.push(group)
    }
    group.items.push({ item, index: i })
    if (item.answered) group.answered += 1
    if (item.correct) group.correct += 1
  })
  return groups
})

/** 当前题所在的年份，用于自动展开。 */
const currentYear = computed(() => current.value?.year ?? null)

function toggleYear(year: number) {
  const next = new Set(collapsedYears.value)
  if (next.has(year)) next.delete(year)
  else next.add(year)
  collapsedYears.value = next
}

function isCollapsed(year: number) {
  return collapsedYears.value.has(year)
}

/**
 * 题干拆行。
 *
 * 组合选择题的题干形如：
 *   「……下列哪些操作会必然导致产生移动次数（）。
 *    I. 表头插入一个元素
 *    II. 表头删除一个元素」
 * 其中 I.~IV. 属于题干内容，但按整段正文渲染时行距过松、视觉上像四个独立段落。
 * 这里按行切开，把罗马数字开头的行单独标记，由模板做紧凑排版。
 */
type StemLine = { kind: 'text' | 'option'; text: string; mark?: string }

const ROMAN = /^\s*(I{1,3}|IV|V|VI{1,3}|IX|X)\s*[.、．]\s*(.*)$/

const stemLines = computed<StemLine[]>(() => {
  const stem = question.value?.stem
  if (!stem) return []
  return stem.split('\n').map((raw) => {
    const matched = raw.match(ROMAN)
    if (matched) {
      return { kind: 'option' as const, mark: `${matched[1]}.`, text: matched[2] }
    }
    return { kind: 'text' as const, text: raw }
  })
})

/** 跳转输入框：接受「2026 3」「2026年第3题」「47」等写法 */
function jumpToInput() {
  const raw = jumpInput.value.trim()
  if (!raw) return
  const items = queue.value?.items ?? []
  // 形如 2026 3 / 2026年第3题 / 2026-3：按年份 + 卷内题号定位
  const matched = raw.match(/^(\d{4})\D*(\d{1,2})$/)
  if (matched) {
    const year = Number(matched[1])
    const number = Number(matched[2])
    const at = items.findIndex((i) => i.year === year && i.number === number)
    if (at >= 0) {
      jumpTo(at)
      jumpInput.value = ''
      return
    }
  }
  // 否则当作队列序号（第 N 题）
  const ordinal = Number(raw)
  if (Number.isFinite(ordinal) && ordinal >= 1 && ordinal <= items.length) {
    jumpTo(ordinal - 1)
    jumpInput.value = ''
  }
}

function subjectName(code: string): string {
  return (
    { ds: '数据结构', os: '操作系统', co: '计算机组成原理', cn: '计算机网络' }[code] ?? code
  )
}

/** 载入队列：按 URL 参数筛选；带 examRef 时只放这一道题。 */
async function loadQueue() {
  loadingQueue.value = true
  message.value = null
  needLogin.value = false
  // 换范围等于换了一套题，连续答对计数应重新开始
  streak.value = 0
  // 折叠状态也是按队列来的，重新载入时恢复全部展开
  collapsedYears.value = new Set()
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
      scope: scope.value as 'all' | 'wrong' | 'untouched' | 'wrong-pending' | 'wrong-mastered',
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
    // 背题模式要直接看答案；做题模式保持「作答后才给答案」
    question.value = await api.question(item.examRef, 0, mode.value === 'memorize')
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
  needLogin.value = false
  try {
    const answer = await api.submitAnswer(item.examRef, selected.value || '已完成')
    // 游客也能判分与看解析，只是结果不落库。这里如实告知，避免用户以为记录被保存了。
    if (answer.guest) {
      message.value = '游客模式：本题已判分，但答题记录不会保存。登录后可累积错题本与掌握度。'
    }
    result.value = {
      correct: answer.correct,
      standardAnswer: answer.standardAnswer,
      explanation: answer.explanation,
    }
    // 连续答对计数：答错即清零（用于顶部的正反馈）
    streak.value = answer.correct ? streak.value + 1 : 0
    // 同步队列内的作答状态，让右侧导航与进度即时更新
    const target = queue.value?.items[index.value]
    if (queue.value && target) {
      if (!target.answered) queue.value.answered += 1
      target.answered = true
      const wasCorrect = target.correct
      target.correct = answer.correct
      // 错题闭环状态本地推演，避免整队列重取
      if (!answer.correct) {
        target.wrongCount += 1
        target.mastered = false
      } else if (target.wrongCount > 0 && !wasCorrect) {
        target.retryCorrectCount += 1
        target.mastered = true
      }
      queue.value.correct = queue.value.items.filter((i) => i.correct).length
    }
    // 在错题范围内，订正成功后该题应移出列表——重新拉取以反映新成员
    if (isWrongScope.value && answer.correct) {
      const currentRef = item.examRef
      const fresh = await api.practiceQueue({
        subject: subjectParam.value,
        scope: scope.value as 'wrong' | 'wrong-pending' | 'wrong-mastered',
      })
      queue.value = fresh
      // 尽量停在同一题；若已移出列表则停在原位置或最后一题
      const stillIndex = fresh.items.findIndex((i) => i.examRef === currentRef)
      index.value =
        stillIndex >= 0 ? stillIndex : Math.min(index.value, Math.max(0, fresh.items.length - 1))
      if (fresh.items.length) {
        await loadQuestion()
      } else {
        question.value = null
      }
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
  // 跳到折叠年份里的题时自动展开，否则导航上看不到当前题
  const year = queue.value?.items[target]?.year
  if (year !== undefined && collapsedYears.value.has(year)) {
    const next = new Set(collapsedYears.value)
    next.delete(year)
    collapsedYears.value = next
  }
  await loadQuestion()
}

onMounted(loadQueue)

/* ---------- 背题模式 ---------- */

/** practice：要提交判分；memorize：直接展开答案，逐题过卷 */
const mode = ref<'practice' | 'memorize'>('practice')

/**
 * 背题模式下用空格/方向键翻页。
 *
 * 只在背题模式监听：做题模式下空格用来选中选项更自然，
 * 这里抢占会让两套交互打架。
 */
function onKeydown(e: KeyboardEvent) {
  if (mode.value !== 'memorize') return
  const target = e.target as HTMLElement | null
  // 输入框里不拦截，避免影响跳题等输入
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) return

  if (e.key === ' ' || e.key === 'ArrowRight' || e.key === 'PageDown') {
    e.preventDefault()
    if (queue.value && index.value < queue.value.items.length - 1) void go(1)
  } else if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
    e.preventDefault()
    if (index.value > 0) void go(-1)
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

/**
 * 切换模式要重新拉一次题目。
 *
 * 背题模式带 memorize 才有答案，做题模式故意不带；不重拉的话
 * 切到背题会看到「本题未提供答案」，切回做题又会残留答案。
 */
watch(mode, () => {
  void loadQuestion()
})

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
    <header class="space-y-3">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="space-y-1">
          <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">刷题</h1>
          <p class="text-sm text-[var(--k-text-muted)]">{{ statsText }}</p>
        </div>
        <div class="flex items-center gap-3 text-sm">
          <!-- 模式切换：做题要提交判分，背题直接看答案过卷 -->
          <div class="flex rounded-lg bg-[var(--k-chip)] p-0.5 text-xs">
            <button
              class="rounded-md px-3 py-1.5 transition"
              :class="
                mode === 'practice'
                  ? 'bg-[var(--k-surface)] font-semibold text-[var(--k-accent-text)] shadow-sm'
                  : 'text-[var(--k-text-muted)]'
              "
              @click="mode = 'practice'"
            >
              做题
            </button>
            <button
              class="rounded-md px-3 py-1.5 transition"
              :class="
                mode === 'memorize'
                  ? 'bg-[var(--k-surface)] font-semibold text-[var(--k-accent-text)] shadow-sm'
                  : 'text-[var(--k-text-muted)]'
              "
              @click="mode = 'memorize'"
            >
              背题
            </button>
          </div>

          <!-- 连续答对：给一点正反馈，答错即清零 -->
          <span
            v-if="streak >= 2 && mode === 'practice'"
            class="inline-flex items-center gap-1 rounded-lg bg-[#0f7a4d]/12 px-2.5 py-1 text-xs font-semibold text-[#0f7a4d] dark:bg-[#4ade80]/12 dark:text-[#4ade80]"
          >
            <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M13 2 3 14h7l-1 8 10-12h-7l1-8Z" />
            </svg>
            连对 {{ streak }}
          </span>
          <span class="tabular-nums text-[var(--k-text-faint)]">{{ progressText }}</span>
          <RouterLink
            to="/dashboard"
            class="rounded-lg border border-[var(--k-border)] px-3 py-1.5 text-xs text-[var(--k-text-muted)] no-underline transition hover:bg-[var(--k-surface-soft)]"
          >
            返回工作台
          </RouterLink>
        </div>
      </div>

      <!-- 进度条：原来只有一个「1 / 846」的数字，看不出整体推进了多少 -->
      <div v-if="queue" class="flex items-center gap-3">
        <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--k-track)]">
          <div
            class="h-full rounded-full bg-[var(--k-accent)] transition-[width] duration-300"
            :style="{ width: progressPercent + '%' }"
          />
        </div>
        <span class="shrink-0 text-[11px] tabular-nums text-[var(--k-text-faint)]">
          已答 {{ progressPercent }}%
        </span>
      </div>
    </header>

    <!-- 提示条 -->
    <div
      v-if="message"
      class="flex flex-wrap items-center justify-between gap-3 rounded-lg border px-4 py-2.5 text-sm"
      :class="
        needLogin
          ? 'border-[var(--k-accent)]/40 bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
          : 'border-[#8a6d1f]/40 bg-[var(--k-warn-soft)] text-[var(--k-warn)]'
      "
    >
      <span>{{ message }}</span>
      <RouterLink
        v-if="needLogin"
        to="/me"
        class="shrink-0 rounded-lg bg-[var(--k-accent)] px-3 py-1 text-xs font-bold text-[var(--k-on-accent)] no-underline"
      >
        去登录
      </RouterLink>
    </div>

    <!-- 错题本子视图切换 -->
    <div v-if="isWrongScope" class="flex flex-wrap items-center gap-2">
      <RouterLink
        :to="{ path: '/practice', query: { scope: 'wrong-pending' } }"
        class="rounded-lg border px-3 py-1.5 text-xs no-underline transition"
        :class="
          scope !== 'wrong-mastered'
            ? 'border-[#b23c3c]/40 bg-[#b23c3c]/10 font-semibold text-[#b23c3c]'
            : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
        "
      >
        待订正
      </RouterLink>
      <RouterLink
        :to="{ path: '/practice', query: { scope: 'wrong-mastered' } }"
        class="rounded-lg border px-3 py-1.5 text-xs no-underline transition"
        :class="
          scope === 'wrong-mastered'
            ? 'border-[#0f7a4d]/40 bg-[#0f7a4d]/10 font-semibold text-[#0f7a4d]'
            : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
        "
      >
        已订正
      </RouterLink>
      <RouterLink
        :to="{ path: '/practice', query: { scope: 'wrong' } }"
        class="rounded-lg border px-3 py-1.5 text-xs no-underline transition"
        :class="
          scope === 'wrong'
            ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
            : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
        "
      >
        全部错题
      </RouterLink>
      <span class="text-[11px] text-[var(--k-text-faint)]">
        重做答对即移入「已订正」；再次答错会退回「待订正」
      </span>
    </div>

    <p v-if="loadingQueue" class="py-10 text-center text-sm text-[var(--k-text-faint)]">加载中…</p>

    <div
      v-else-if="!queue || queue.total === 0"
      class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-12 text-center"
    >
      <p class="text-sm text-[var(--k-text-muted)]">
        {{
          scope === 'wrong-mastered'
            ? '还没有已订正的错题 —— 把待订正的错题重做答对后会出现在这里。'
            : scope === 'untouched'
              ? '所有题目都做过了。'
              : scope === 'wrong-pending'
                ? '待订正的错题已清空。已订正的错题可在「已订正」中回顾。'
                : isWrongScope
                  ? '还没有答错过的题。'
                  : '该范围内暂无题目。'
        }}
      </p>
      <RouterLink
        v-if="scope === 'wrong-pending'"
        :to="{ path: '/practice', query: { scope: 'wrong-mastered' } }"
        class="mt-3 inline-block rounded-lg border border-[var(--k-border)] px-4 py-2 text-sm font-semibold text-[var(--k-accent-text)] no-underline transition hover:bg-[var(--k-surface-soft)]"
      >
        查看已订正的错题
      </RouterLink>
      <RouterLink
        to="/dashboard"
        class="mt-4 inline-block rounded-lg bg-[var(--k-accent)] px-4 py-2 text-sm font-semibold text-[var(--k-on-accent)] no-underline hover:bg-[var(--k-accent-hover)]"
      >
        回到工作台
      </RouterLink>
    </div>

    <!-- 版面：题目限制行宽（中文阅读舒适区约 30–40 字），导航栏加宽以容纳分组 -->
    <div v-else class="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_240px]">
      <div class="min-w-0 space-y-4">
        <article
          v-if="question && mode === 'practice'"
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
            <span
              v-if="current && current.wrongCount > 0"
              class="rounded bg-[#b23c3c]/12 px-2 py-0.5 font-semibold text-[#b23c3c]"
            >
              错过 {{ current.wrongCount }} 次
            </span>
            <span
              v-if="current && current.mastered"
              class="rounded bg-[#0f7a4d]/12 px-2 py-0.5 font-semibold text-[#0f7a4d]"
            >
              已订正
            </span>
          </div>

          <!--
            题干。
            组合选择题的选项（I. / II. / III. / IV.）是题干的一部分，原先按正文
            1.9 倍行距渲染，四个短选项之间被拉开近 200px。这里把整体行距收到 1.75，
            并把单独的 I.~IV. 行改成紧凑列表（见下方 stemLines 渲染）。
          -->
          <div class="max-w-[68ch] space-y-3">
            <template v-for="(line, i) in stemLines" :key="i">
              <!-- 罗马数字选项行：紧凑、左侧带缩进，与题干正文区分 -->
              <p
                v-if="line.kind === 'option'"
                class="flex gap-2 text-[15px] leading-relaxed text-[var(--k-text)]"
              >
                <span class="shrink-0 font-semibold text-[var(--k-text-muted)]">{{ line.mark }}</span>
                <span>{{ line.text }}</span>
              </p>
              <pre
                v-else
                class="whitespace-pre-wrap font-sans text-[15px] leading-[1.75] text-[var(--k-text)]"
                >{{ line.text }}</pre
              >
            </template>
          </div>

          <img
            v-for="path in question.imagePaths"
            :key="path"
            :src="path"
            :alt="`题目配图 ${question.number}`"
            class="mt-4 max-w-full rounded-lg border border-[var(--k-border)]"
          />

          <!--
            选项：整行可点（label 包裹 radio），加 hover 位移与更明显的选中态。
            限制行宽与题干一致，避免长选项在一行里拉出很长的视线距离。
          -->
          <div v-if="question.options.length" class="mt-5 max-w-[68ch] space-y-2">
            <label
              v-for="option in question.options"
              :key="option.key"
              class="flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-3 text-[15px] transition"
              :class="
                selected === option.key
                  ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)]'
                  : 'border-[var(--k-border)] hover:border-[var(--k-accent)]/60 hover:bg-[var(--k-surface-soft)]'
              "
            >
              <input v-model="selected" type="radio" :value="option.key" class="mt-1.5 shrink-0" />
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
              class="rounded-lg bg-[var(--k-accent)] px-5 py-2.5 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-60"
              :disabled="submitting"
              @click="submit"
            >
              {{ submitting ? '提交中…' : result ? '再次提交' : '提交答案' }}
            </button>

            <!-- 按本题关联的知识点跳 B 站搜索。不托管课程内容，只做「该看哪一节」的指引。 -->
            <button
              class="inline-flex items-center gap-1.5 rounded-lg border border-[var(--k-border)] px-4 py-2.5 text-sm text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)] disabled:opacity-50"
              :disabled="videoLoading"
              @click="openVideo"
            >
              <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                <rect x="2" y="6" width="20" height="13" rx="3" />
                <path d="m10 10.5 4 2.5-4 2.5z" fill="currentColor" stroke="none" />
              </svg>
              {{ videoLoading ? '查找中…' : '看视频讲解' }}
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

          <!-- 关联知识点：显示可读名称并可跳转，不再暴露 kb-xxx 这样的内部 ID -->
          <div v-if="question.knowledgeBlocks?.length" class="mt-4 border-t border-[var(--k-border)] pt-3">
            <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">相关知识点</p>
            <div class="flex flex-wrap gap-2">
              <RouterLink
                v-for="block in question.knowledgeBlocks"
                :key="block.blockId"
                :to="{
                  path: '/learn',
                  query: block.pointId ? { subject: question.subject } : undefined,
                  hash: block.pointId ? `#/learn/${block.pointId}` : undefined,
                }"
                class="inline-flex items-center gap-1.5 rounded-lg border border-[var(--k-border)] px-2.5 py-1 text-xs text-[var(--k-text-muted)] no-underline transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
              >
                <svg
                  class="h-3 w-3 shrink-0"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                >
                  <path d="M4 19.5V5a2 2 0 0 1 2-2h11l3 3v13.5" />
                  <path d="M8 7h6M8 11h6" />
                </svg>
                {{ block.label }}
              </RouterLink>
            </div>
          </div>
        </article>

        <p v-else-if="loadingQuestion" class="py-10 text-center text-sm text-[var(--k-text-faint)]">
          加载题目…
        </p>

        <!--
          背题视图：与做题视图共用同一份题目数据与翻页状态，只是把
          「提交后才能看答案」换成「直接展示答案与解析」。
          不另建页面，是因为背题与做题在「翻到哪一题」上应当保持联动。
        -->
        <article
          v-if="question && mode === 'memorize'"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-6"
        >
          <div class="mb-4 flex flex-wrap items-center gap-2 text-xs text-[var(--k-text-faint)]">
            <span class="rounded bg-[var(--k-chip)] px-2 py-0.5 font-medium text-[var(--k-text-muted)]">
              {{ question.id }}
            </span>
            <span>{{ question.number }}</span>
            <span>{{ subjectName(question.subject) }}</span>
            <span v-if="question.chapterName">· {{ question.chapterName }}</span>
            <span class="ml-auto rounded bg-[var(--k-accent-soft)] px-2 py-0.5 font-semibold text-[var(--k-accent-text)]">
              {{ question.score }} 分
            </span>
          </div>

          <StemMarkdown :source="question.stem" />

          <!-- 选择题：把选项也展示出来，并标出正确答案 -->
          <div v-if="question.options.length" class="mt-4 space-y-1.5">
            <div
              v-for="opt in question.options"
              :key="opt.key"
              class="flex items-start gap-2.5 rounded-lg border px-3 py-2 text-sm"
              :class="
                opt.key === question.answer
                  ? 'border-[#0f7a4d]/40 bg-[#0f7a4d]/8'
                  : 'border-[var(--k-border)] text-[var(--k-text-muted)]'
              "
            >
              <span
                class="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded text-[11px] font-semibold"
                :class="
                  opt.key === question.answer
                    ? 'bg-[#0f7a4d] text-white'
                    : 'bg-[var(--k-chip)] text-[var(--k-text-muted)]'
                "
              >
                {{ opt.key }}
              </span>
              <StemMarkdown class="min-w-0 flex-1" :source="opt.text" />
            </div>
          </div>

          <!-- 综合题的子问 -->
          <div v-if="question.subQuestions.length" class="mt-3 space-y-1.5">
            <p
              v-for="sub in question.subQuestions"
              :key="sub.subNumber"
              class="rounded-lg bg-[var(--k-surface-soft)] px-3 py-1.5 text-sm text-[var(--k-text-muted)]"
            >
              <span class="font-semibold text-[var(--k-text)]">({{ sub.subNumber }})</span>
              <span class="ml-1.5">{{ sub.description }}</span>
              <span v-if="sub.score != null" class="ml-2 text-[11px] text-[var(--k-text-faint)]">
                {{ sub.score }} 分
              </span>
            </p>
          </div>

          <!-- 答案与解析：背题模式直接展开 -->
          <div class="mt-4 space-y-3 rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-4">
            <div>
              <p class="text-[11px] font-semibold text-[#0f7a4d] dark:text-[#4ade80]">正确答案</p>
              <p class="mt-1 text-sm font-semibold text-[var(--k-text)]">
                {{ question.answer || '（本题未提供答案）' }}
              </p>
            </div>
            <div v-if="question.explanation">
              <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">解析</p>
              <StemMarkdown class="mt-1" :source="question.explanation" />
            </div>
            <div v-else>
              <p class="text-[11px] text-[var(--k-text-faint)]">本题暂无解析</p>
            </div>
          </div>

          <!-- 翻页 -->
          <div class="mt-5 flex flex-wrap items-center justify-between gap-3">
            <button
              class="rounded-lg border border-[var(--k-border)] px-4 py-2 text-sm text-[var(--k-text-muted)] transition hover:bg-[var(--k-surface-soft)] disabled:opacity-40"
              :disabled="index === 0"
              @click="go(-1)"
            >
              ← 上一题
            </button>

            <div class="flex items-center gap-2 text-xs text-[var(--k-text-faint)]">
              <span class="tabular-nums">{{ index + 1 }} / {{ queue?.items.length ?? 0 }}</span>
              <span class="hidden sm:inline">· 空格翻页</span>
            </div>

            <button
              class="rounded-lg bg-[var(--k-accent)] px-5 py-2 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)] disabled:opacity-40"
              :disabled="!queue || index >= queue.items.length - 1"
              @click="go(1)"
            >
              下一题 →
            </button>
          </div>
        </article>
      </div>

      <aside class="space-y-3">
        <div
          class="flex max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] lg:sticky lg:top-4"
        >
          <div class="shrink-0 border-b border-[var(--k-border)] p-3 pb-2.5">
            <div class="flex items-baseline justify-between gap-2">
              <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">题目导航</p>
              <span v-if="currentYear" class="text-[10px] tabular-nums text-[var(--k-text-faint)]">
                {{ currentYear }} 年
              </span>
            </div>

            <!-- 跳转：846 道题里靠点找太慢，允许直接输入题号 -->
            <div class="mt-2 flex items-center gap-1.5">
              <input
                v-model="jumpInput"
                class="w-full min-w-0 rounded-lg bg-[var(--k-surface-soft)] px-2.5 py-1.5 text-[11px] text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
                placeholder="跳转：2026 3 或 47"
                @keyup.enter="jumpToInput"
              />
              <button
                class="shrink-0 rounded-lg border border-[var(--k-border)] px-2 py-1.5 text-[11px] text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)]"
                @click="jumpToInput"
              >
                跳
              </button>
            </div>
          </div>

          <!--
            按年份分组，可折叠。
            原先把全部题目平铺成按钮墙：数字连续排列却跨越 18 份试卷，
            卷与卷之间没有分隔、同一数字还会重复出现，用户无法定位。
          -->
          <div class="min-h-0 flex-1 overflow-y-auto p-2">
            <div v-for="group in navGroups" :key="group.year" class="mb-1.5">
              <button
                class="flex w-full items-center gap-1.5 rounded-lg px-2 py-1.5 text-left transition hover:bg-[var(--k-surface-soft)]"
                :class="group.year === currentYear ? 'text-[var(--k-accent-text)]' : 'text-[var(--k-text-muted)]'"
                @click="toggleYear(group.year)"
              >
                <svg
                  class="h-3 w-3 shrink-0 transition-transform"
                  :class="isCollapsed(group.year) ? '-rotate-90' : ''"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                >
                  <path d="m6 9 6 6 6-6" />
                </svg>
                <span class="text-[11px] font-bold tabular-nums">{{ group.year }}</span>
                <span class="text-[10px] text-[var(--k-text-faint)]">{{ group.items.length }} 题</span>
                <span class="ml-auto text-[10px] tabular-nums text-[var(--k-text-faint)]">
                  {{ group.answered }}/{{ group.items.length }}
                </span>
              </button>

              <div
                v-if="!isCollapsed(group.year)"
                class="mt-1 grid grid-cols-8 gap-1 pl-1 lg:grid-cols-7"
              >
                <button
                  v-for="cell in group.items"
                  :key="cell.item.examRef"
                  class="grid h-6 place-items-center rounded text-[10px] font-semibold tabular-nums transition"
                  :class="[
                    cell.index === index
                      ? 'ring-2 ring-[var(--k-accent)] ring-offset-1 ring-offset-[var(--k-surface)]'
                      : '',
                    cell.item.correct
                      ? 'bg-[#0f7a4d]/15 text-[#0f7a4d]'
                      : cell.item.answered
                        ? 'bg-[#b23c3c]/15 text-[#b23c3c]'
                        : 'bg-[var(--k-chip)] text-[var(--k-text-faint)] hover:bg-[var(--k-accent-soft)]',
                  ]"
                  :title="
                    `${cell.item.year} 年第 ${cell.item.number} 题` +
                    (cell.item.wrongCount > 0 ? ` · 错过 ${cell.item.wrongCount} 次` : '') +
                    (cell.item.mastered ? ' · 已订正' : '')
                  "
                  @click="jumpTo(cell.index)"
                >
                  {{ cell.item.number }}
                </button>
              </div>
            </div>
          </div>

          <div
            class="flex shrink-0 flex-wrap gap-x-3 gap-y-1 border-t border-[var(--k-border)] p-3 pt-2.5 text-[10px] text-[var(--k-text-faint)]"
          >
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

        <!--
          原先这里是「当前范围」卡片，内容与顶部标题行重复，
          且把 kb-xxx / pointId 这类内部 ID 直接显示给用户。
          改为快捷操作，才是侧栏真正该放的东西。
        -->
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-3">
          <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">快捷操作</p>
          <div class="space-y-1.5">
            <button
              class="w-full rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-left text-[11px] text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
              @click="jumpTo(0)"
            >
              回到本范围第 1 题
            </button>
            <RouterLink
              :to="{ path: '/practice', query: { scope: 'wrong-pending' } }"
              class="block rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-[11px] text-[var(--k-text-muted)] no-underline transition hover:border-[#b23c3c] hover:text-[#b23c3c]"
            >
              去做错题
            </RouterLink>
            <RouterLink
              :to="{ path: '/practice', query: { scope: 'untouched' } }"
              class="block rounded-lg border border-[var(--k-border)] px-2.5 py-1.5 text-[11px] text-[var(--k-text-muted)] no-underline transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
            >
              去做没做过的题
            </RouterLink>
          </div>
        </div>
      </aside>
    </div>

    <!-- 视频指引弹窗：按知识点跳 B 站搜索，不托管课程内容 -->
    <div
      v-if="videoOpen"
      class="fixed inset-0 z-40 grid place-items-center bg-black/50 p-4"
      @click.self="videoOpen = false"
    >
      <div class="w-full max-w-lg space-y-4 rounded-2xl bg-[var(--k-surface)] p-6 shadow-xl">
        <div class="flex items-start justify-between gap-3">
          <div>
            <h3 class="text-base font-bold text-[var(--k-text)]">视频讲解</h3>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
              按本题知识点到 B 站搜索，点击后在新标签页打开
            </p>
          </div>
          <button
            class="shrink-0 rounded-lg px-2 py-1 text-[var(--k-text-faint)] transition hover:bg-[var(--k-surface-soft)]"
            @click="videoOpen = false"
          >
            ✕
          </button>
        </div>

        <div v-if="videoGuide">
          <p class="text-[11px] font-semibold text-[var(--k-text-faint)]">本题知识点</p>
          <div class="mt-1.5 flex flex-wrap gap-1.5">
            <span
              v-for="p in videoGuide.points"
              :key="p.title"
              class="rounded-md bg-[var(--k-accent-soft)] px-2 py-1 text-[11px] text-[var(--k-accent-text)]"
            >
              {{ p.title }}
            </span>
            <span v-if="!videoGuide.points.length" class="text-[11px] text-[var(--k-text-faint)]">
              {{ videoGuide.subjectName }} · {{ videoGuide.chapterName ?? '暂无' }}
            </span>
          </div>

          <p class="mt-4 text-[11px] font-semibold text-[var(--k-text-faint)]">选择课程</p>
          <div class="mt-1.5 space-y-1.5">
            <a
              v-for="s in videoGuide.sources"
              :key="s.teacher"
              :href="s.url"
              target="_blank"
              rel="noopener noreferrer"
              class="flex items-center gap-3 rounded-lg border border-[var(--k-border)] px-3 py-2.5 no-underline transition hover:border-[var(--k-accent)] hover:bg-[var(--k-accent-soft)]"
            >
              <span
                class="shrink-0 rounded px-2 py-0.5 text-[11px] font-semibold"
                :class="
                  s.teacher === '综合'
                    ? 'bg-[var(--k-chip)] text-[var(--k-text-muted)]'
                    : 'bg-[var(--k-accent)] text-[var(--k-on-accent)]'
                "
              >
                {{ s.teacher }}
              </span>
              <span class="min-w-0 flex-1 truncate text-xs text-[var(--k-text-muted)]">
                {{ s.keyword }}
              </span>
              <span class="shrink-0 text-[11px] text-[var(--k-accent-text)]">去 B 站 →</span>
            </a>
          </div>

          <p class="mt-3 text-[11px] leading-relaxed text-[var(--k-text-faint)]">
            课程内容与版权归各出处所有，本站只提供搜索指引，不托管视频。
          </p>
        </div>

        <p v-else class="text-sm text-[var(--k-text-muted)]">正在查找相关课程…</p>
      </div>
    </div>
  </div>
</template>