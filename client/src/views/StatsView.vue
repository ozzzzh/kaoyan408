<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'
import { api } from '@/api/client'
import type { PointMastery, PointMasteryItem } from '@/api/types'
import { subjectMeta } from '@/constants/subjects'

const userStore = useUserStore()
const { dashboard } = storeToRefs(userStore)

/** 真题卷进度：练习（question_attempt）与模考（mock_session）分开统计 */
type ExamRow = {
  year: number
  practice?: { total: number; done: number; correct: number }
  mock?: { attempts: number; bestScore: number; lastScore: number; lastAt: string | null; durationMinutes: number }
}
const examRows = ref<ExamRow[]>([])

const examSummary = computed(() => {
  let finished = 0
  let done = 0
  let total = 0
  let mockCount = 0
  let bestMock = 0
  for (const row of examRows.value) {
    const p = row.practice
    const q = p?.total ?? 0
    total += q
    done += p?.done ?? 0
    if (p && q > 0 && (p.done ?? 0) >= q) finished += 1
    if (row.mock) {
      mockCount += row.mock.attempts
      bestMock = Math.max(bestMock, row.mock.bestScore)
    }
  }
  return { finished, total: examRows.value.length, done, questionTotal: total, mockCount, bestMock }
})

async function loadExamProgress() {
  try {
    examRows.value = await api.examProgress()
  } catch {
    /* 看板其余部分不受影响 */
  }
}

/** 方格底色：按练习完成度分档（0 / <34% / <67% / <100% / 100%） */
function cellClass(row: ExamRow): string {
  const p = row.practice
  if (!p || !p.total || !p.done) return 'bg-[var(--k-surface-soft)]'
  const ratio = p.done / p.total
  if (ratio >= 1) return 'bg-[var(--k-accent)]/90'
  if (ratio >= 0.67) return 'bg-[var(--k-accent)]/60'
  if (ratio >= 0.34) return 'bg-[var(--k-accent)]/40'
  return 'bg-[var(--k-accent)]/20'
}

/** 方格 hover 提示 */
function cellTitle(row: ExamRow): string {
  const p = row.practice
  const parts = [`${row.year} 年`]
  parts.push(p && p.total ? `练习 ${p.done}/${p.total}` : '练习未开始')
  if (row.mock) parts.push(`模考最好 ${row.mock.bestScore}/150（${row.mock.attempts} 次）`)
  return parts.join(' · ')
}

const points = ref<PointMastery | null>(null)
const loadingPoints = ref(true)
/** 科目筛选（'' = 全部） */
const subjectFilter = ref('')

const subjects = [
  { code: '', label: '全部' },
  { code: 'ds', label: '数据结构' },
  { code: 'os', label: '操作系统' },
  { code: 'co', label: '组成原理' },
  { code: 'cn', label: '计算机网络' },
]

/** 正确率对应的颜色：<50% 红、<75% 黄、其余绿 */
function accuracyClass(accuracy: number | null): string {
  if (accuracy === null) return 'text-[var(--k-text-faint)]'
  if (accuracy < 50) return 'text-[#b23c3c]'
  if (accuracy < 75) return 'text-[var(--k-warn)]'
  return 'text-[#0f7a4d]'
}

function accuracyBarClass(accuracy: number | null): string {
  if (accuracy === null) return 'bg-[var(--k-chip)]'
  if (accuracy < 50) return 'bg-[#b23c3c]'
  if (accuracy < 75) return 'bg-[#f0a020]'
  return 'bg-[#0f7a4d]'
}

/** 已开始的知识点（有做题记录），按完成度排序用于「正在推进」区块 */
const inProgress = computed(() => {
  const started = (points.value?.points ?? []).filter((p) => p.done > 0)
  return [...started].sort((a, b) => b.progress - a.progress).slice(0, 12)
})

const untouchedCount = computed(() => points.value?.untouchedPoints ?? 0)

async function loadPoints() {
  loadingPoints.value = true
  try {
    points.value = await api.statsPoints(subjectFilter.value || undefined)
  } catch {
    points.value = null
  } finally {
    loadingPoints.value = false
  }
}

function practicePoint(point: PointMasteryItem) {
  return { path: '/practice', query: { pointId: point.pointId } }
}

/**
 * 刚起步时（已开始的知识点很少），单看「薄弱」没有意义——
 * 此时更该提示「还有哪些没开始」。这里给出题量最多的未开始知识点。
 */
/** 分组展示：每组按「欠账」排序，默认只看前 3 个 */
const expandedSubjects = ref<Set<string>>(new Set())

type WeakGroup = { subject: string; items: PointMasteryItem[] }

const weakGroups = computed<WeakGroup[]>(() => {
  const groups = new Map<string, PointMasteryItem[]>()
  for (const point of points.value?.weakest ?? []) {
    const key = point.subject || 'other'
    const list = groups.get(key) ?? []
    list.push(point)
    groups.set(key, list)
  }
  // 组内按欠账（总题数 - 已答对）从多到少
  return [...groups.entries()]
    .map(([subject, items]) => ({
      subject,
      items: [...items].sort(
        (a, b) => b.total - b.correct - (a.total - a.correct) || (a.accuracy ?? 100) - (b.accuracy ?? 100),
      ),
    }))
    .sort((a, b) => b.items.length - a.items.length)
})

function toggleSubject(subject: string) {
  const next = new Set(expandedSubjects.value)
  if (next.has(subject)) next.delete(subject)
  else next.add(subject)
  expandedSubjects.value = next
}

/** 每组默认 3 条，展开后显示全部 */
function visiblePoints(group: WeakGroup): PointMasteryItem[] {
  return expandedSubjects.value.has(group.subject) ? group.items : group.items.slice(0, 3)
}

const untouchedTop = computed(() => {
  const untouched = (points.value?.points ?? []).filter((p) => p.done === 0)
  return [...untouched].sort((a, b) => b.total - a.total).slice(0, 6)
})

const showUntouchedSuggestion = computed(
  () => (points.value?.startedPoints ?? 0) < 5 && untouchedTop.value.length > 0,
)

onMounted(() => {
  void loadPoints()
  void loadExamProgress()
})

watch(subjectFilter, loadPoints)
</script>

<template>
  <div class="space-y-5">
    <header class="space-y-1">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">学习看板</h1>
      <p class="text-sm text-[var(--k-text-muted)]">
        按科目与知识点查看掌握度，找出该补的章节。
      </p>
    </header>

    <div
      v-if="!dashboard"
      class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] p-6 text-sm text-[var(--k-text-faint)]"
    >
      暂无数据。请先启动后端并导入内容，然后刷新页面。
    </div>

    <template v-else>
      <!-- 总览：一行紧凑指标（原四张大卡里「题库总量」是常数，信息密度太低） -->
      <section
        class="flex flex-wrap items-center gap-x-6 gap-y-2 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-5 py-3"
      >
        <p class="text-xs text-[var(--k-text-faint)]">
          已作答
          <span class="ml-1.5 text-xl font-bold tabular-nums text-[var(--k-text)]">{{ dashboard.doneQuestions }}</span>
        </p>
        <p class="text-xs text-[var(--k-text-faint)]">
          正确率
          <span class="ml-1.5 text-xl font-bold tabular-nums text-[var(--k-text)]">{{ dashboard.accuracy }}%</span>
        </p>
        <p class="text-xs text-[var(--k-text-faint)]">
          连续打卡
          <span class="ml-1.5 text-xl font-bold tabular-nums text-[var(--k-text)]">{{ dashboard.streakDays }}</span>
          <span class="text-xs">天</span>
        </p>
        <p class="text-xs text-[var(--k-text-faint)]">
          题库覆盖
          <span class="ml-1.5 text-xl font-bold tabular-nums text-[var(--k-text)]">
            {{ Math.round((dashboard.doneQuestions / Math.max(dashboard.totalQuestions, 1)) * 100) }}%
          </span>
        </p>
        <RouterLink
          to="/exams"
          class="ml-auto text-[11px] font-semibold text-[var(--k-accent-text)] no-underline hover:underline"
        >
          真题卷 →
        </RouterLink>
      </section>

      <!-- 真题卷覆盖：18 卷方格热力图（一眼看完，替代竖排 18 行） -->
      <section v-if="examRows.length" class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <div>
            <h2 class="text-sm font-bold text-[var(--k-heading)]">真题卷覆盖</h2>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
              底色深浅 = 练习完成度；右上角数字 = 模考最好分。点一格看整卷。
            </p>
          </div>
          <p class="text-[11px] tabular-nums text-[var(--k-text-faint)]">
            已完成 {{ examSummary.finished }}/{{ examSummary.total }} 卷 · 累计
            {{ examSummary.done }}/{{ examSummary.questionTotal }} 题 · 模考 {{ examSummary.mockCount }} 次
            <span v-if="examSummary.bestMock" class="text-[var(--k-accent-text)]">最好 {{ examSummary.bestMock }}</span>
          </p>
        </div>

        <div class="mt-3 grid grid-cols-6 gap-1.5">
          <RouterLink
            v-for="row in examRows"
            :key="row.year"
            :to="`/exams/${row.year}`"
            class="relative flex flex-col justify-between rounded-lg border border-[var(--k-border)] p-1.5 no-underline transition hover:border-[var(--k-accent)]"
            :class="cellClass(row)"
            :title="cellTitle(row)"
          >
            <span class="text-[10px] font-semibold tabular-nums text-[var(--k-text-muted)]">{{ row.year }}</span>
            <span class="mt-1 text-[10px] tabular-nums text-[var(--k-text-faint)]">
              {{ row.practice?.done ?? 0 }}/{{ row.practice?.total ?? 47 }}
            </span>
            <span
              v-if="row.mock"
              class="absolute right-1 top-1 text-[9px] font-bold tabular-nums text-[var(--k-accent-text)]"
            >
              {{ row.mock.bestScore }}
            </span>
          </RouterLink>
        </div>
      </section>

      <!-- 薄弱知识点 -->
      <section class="space-y-3">
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <div>
            <h2 class="text-sm font-bold text-[var(--k-heading)]">该补的知识点</h2>
            <p class="mt-0.5 text-[11px] text-[var(--k-text-faint)]">
              按正确率由低到高排列；正确率越低越该优先复习
            </p>
          </div>
          <p v-if="points" class="text-xs text-[var(--k-text-faint)]">
            共 {{ points.totalPoints }} 个可练知识点 · 已开始 {{ points.startedPoints }} ·
            未开始 {{ untouchedCount }}
          </p>
        </div>

        <p v-if="loadingPoints" class="py-6 text-center text-sm text-[var(--k-text-faint)]">
          加载中…
        </p>

        <div
          v-else-if="points && points.weakest.length === 0"
          class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-10 text-center text-sm text-[var(--k-text-muted)]"
        >
          还没有做过题 —— 先刷几道，这里会按正确率排出最需要补的知识点。
        </div>

        <div v-else-if="points" class="space-y-3">
          <!-- 按科目分组，每组先给最需要补的 3 个；其余折叠 -->
          <section
            v-for="group in weakGroups"
            :key="group.subject"
            class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]"
          >
            <header class="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--k-border)] px-4 py-2.5">
              <p class="flex items-center gap-2 text-sm font-semibold text-[var(--k-text)]">
                <span class="h-2 w-2 shrink-0 rounded-full" :class="subjectMeta(group.subject).bar ?? 'bg-[var(--k-text-faint)]'" />
                {{ subjectMeta(group.subject).name ?? group.subject }}
                <span class="text-[11px] font-normal text-[var(--k-text-faint)]">
                  待补 {{ group.items.length }} 个
                </span>
              </p>
              <button
                v-if="group.items.length > 3"
                type="button"
                class="text-[11px] text-[var(--k-text-faint)] transition hover:text-[var(--k-accent-text)]"
                @click="toggleSubject(group.subject)"
              >
                {{ expandedSubjects.has(group.subject) ? '收起' : `展开全部 ${group.items.length} 个` }}
              </button>
            </header>

            <ul class="divide-y divide-[var(--k-border)]">
              <li
                v-for="point in visiblePoints(group)"
                :key="point.pointId"
                class="flex flex-wrap items-center gap-x-4 gap-y-1.5 px-4 py-2.5"
              >
                <RouterLink
                  :to="`/learn/${point.pointId}`"
                  class="min-w-0 flex-1 truncate text-sm font-semibold text-[var(--k-text)] no-underline hover:text-[var(--k-accent-text)]"
                >
                  {{ point.title }}
                </RouterLink>

                <span class="w-16 shrink-0 text-right text-[11px] tabular-nums text-[var(--k-text-faint)]">
                  {{ point.done }}/{{ point.total }} 题
                </span>
                <span class="w-12 shrink-0 text-right text-xs font-bold tabular-nums" :class="accuracyClass(point.accuracy)">
                  {{ point.accuracy === null ? '—' : point.accuracy + '%' }}
                </span>
                <!-- 拆成两项：错题数 + 还没做的题数，避免「正确率 100% 却欠 29 题」的误读 -->
                <span class="w-24 shrink-0 text-right text-[11px] tabular-nums">
                  <span :class="point.done - point.correct > 0 ? 'text-[var(--k-danger)]' : 'text-[var(--k-text-faint)]'">
                    错 {{ Math.max(point.done - point.correct, 0) }}
                  </span>
                  <span class="text-[var(--k-text-faint)]">· 未 {{ Math.max(point.total - point.done, 0) }}</span>
                </span>
                <RouterLink
                  :to="practicePoint(point)"
                  class="shrink-0 rounded-lg border border-[var(--k-border)] px-2.5 py-1 text-[11px] no-underline text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
                >
                  去练
                </RouterLink>

                <span class="h-1 w-full overflow-hidden rounded-full bg-[var(--k-track)]">
                  <span
                    class="block h-full rounded-full transition-all duration-500"
                    :class="accuracyBarClass(point.accuracy)"
                    :style="{ width: Math.max(point.accuracy ?? 0, 2) + '%' }"
                  />
                </span>
              </li>
            </ul>
          </section>
        </div>

        <!-- 刚起步时补充「还没开始」的提示 -->
        <div
          v-if="showUntouchedSuggestion"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-4 py-3"
        >
          <p class="text-xs font-semibold text-[var(--k-text-muted)]">
            还有 {{ untouchedCount }} 个知识点没开始。题量最多的几个：
          </p>
          <div class="mt-2 flex flex-wrap gap-1.5">
            <RouterLink
              v-for="point in untouchedTop"
              :key="point.pointId"
              :to="practicePoint(point)"
              class="rounded-lg border border-[var(--k-border)] bg-[var(--k-surface)] px-2.5 py-1 text-[11px] text-[var(--k-text-muted)] no-underline transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
            >
              {{ point.title }} · {{ point.total }} 题
            </RouterLink>
          </div>
        </div>
      </section>

      <!-- 分科目掌握度 -->
      <section class="space-y-3">
        <h2 class="text-sm font-bold text-[var(--k-heading)]">分科目掌握度</h2>
        <div class="overflow-hidden rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)]">
          <table class="w-full text-sm">
            <thead class="bg-[var(--k-surface-soft)] text-xs text-[var(--k-text-faint)]">
              <tr>
                <th class="px-5 py-3 text-left font-medium">科目</th>
                <th class="px-5 py-3 text-right font-medium">已做 / 总量</th>
                <th class="px-5 py-3 text-right font-medium">正确率</th>
                <th class="px-5 py-3 text-right font-medium">掌握率</th>
                <th class="px-5 py-3 text-right font-medium">完成度</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in dashboard.subjects"
                :key="item.subject"
                class="border-t border-[var(--k-border)]"
              >
                <td class="px-5 py-3">
                  <span class="inline-flex items-center gap-2">
                    <span
                      class="h-2 w-2 rounded-full"
                      :class="subjectMeta(item.subject).bar ?? 'bg-[var(--k-text-faint)]'"
                    />
                    <span class="font-medium text-[var(--k-text)]">
                      {{ subjectMeta(item.subject).name ?? item.subject }}
                    </span>
                  </span>
                </td>
                <td class="px-5 py-3 text-right tabular-nums text-[var(--k-text-muted)]">
                  {{ item.done }} / {{ item.total }}
                </td>
                <td class="px-5 py-3 text-right tabular-nums text-[var(--k-text-muted)]">
                  {{ item.accuracy }}%
                </td>
                <td class="px-5 py-3 text-right tabular-nums text-[var(--k-text-muted)]">
                  {{ item.masteryRate }}%
                </td>
                <td class="px-5 py-3 text-right tabular-nums text-[var(--k-text-muted)]">
                  {{ item.progress }}%
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 知识点明细（按科目筛选） -->
      <section class="space-y-3">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-sm font-bold text-[var(--k-heading)]">知识点明细</h2>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="item in subjects"
              :key="item.code"
              class="rounded-lg border px-3 py-1 text-xs transition"
              :class="
                subjectFilter === item.code
                  ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
                  : 'border-[var(--k-border)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
              "
              @click="subjectFilter = item.code"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <p
          v-if="!loadingPoints && inProgress.length === 0"
          class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-8 text-center text-sm text-[var(--k-text-muted)]"
        >
          该范围内还没有做题记录。
        </p>

        <div v-else class="grid gap-2 sm:grid-cols-2">
          <RouterLink
            v-for="point in inProgress"
            :key="point.pointId"
            :to="practicePoint(point)"
            class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
          >
            <div class="flex items-center justify-between gap-3">
              <span class="truncate text-[13px] font-semibold text-[var(--k-text)]">
                {{ point.title }}
              </span>
              <span class="shrink-0 text-xs font-bold tabular-nums" :class="accuracyClass(point.accuracy)">
                {{ point.accuracy === null ? '—' : point.accuracy + '%' }}
              </span>
            </div>
            <div class="mt-2 flex items-center gap-2">
              <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--k-track)]">
                <div
                  class="h-full rounded-full"
                  :class="subjectMeta(point.subject).bar ?? 'bg-[var(--k-accent)]'"
                  :style="{ width: Math.max(point.progress, 2) + '%' }"
                />
              </div>
              <span class="shrink-0 text-[10px] tabular-nums text-[var(--k-text-faint)]">
                {{ point.done }}/{{ point.total }}
              </span>
            </div>
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>