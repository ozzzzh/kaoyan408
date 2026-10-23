<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'
import { api } from '@/api/client'
import type { PointMastery, PointMasteryItem } from '@/api/types'

const userStore = useUserStore()
const { dashboard } = storeToRefs(userStore)

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

const subjectMeta: Record<string, { name: string; bar: string }> = {
  ds: { name: '数据结构', bar: 'bg-[#2d68ef]' },
  os: { name: '操作系统', bar: 'bg-[#7c5cf0]' },
  co: { name: '计算机组成原理', bar: 'bg-[#f0a020]' },
  cn: { name: '计算机网络', bar: 'bg-[#12a594]' },
}

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
const untouchedTop = computed(() => {
  const untouched = (points.value?.points ?? []).filter((p) => p.done === 0)
  return [...untouched].sort((a, b) => b.total - a.total).slice(0, 6)
})

const showUntouchedSuggestion = computed(
  () => (points.value?.startedPoints ?? 0) < 5 && untouchedTop.value.length > 0,
)

onMounted(loadPoints)

watch(subjectFilter, loadPoints)
</script>

<template>
  <div class="space-y-8">
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
      <!-- 总览 -->
      <section class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <p class="text-xs text-[var(--k-text-faint)]">题库总量</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[var(--k-text)]">
            {{ dashboard.totalQuestions }}
          </p>
        </div>
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <p class="text-xs text-[var(--k-text-faint)]">已作答</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[var(--k-text)]">
            {{ dashboard.doneQuestions }}
          </p>
        </div>
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <p class="text-xs text-[var(--k-text-faint)]">正确率</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[var(--k-text)]">
            {{ dashboard.accuracy }}%
          </p>
        </div>
        <div class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-5">
          <p class="text-xs text-[var(--k-text-faint)]">连续打卡</p>
          <p class="mt-2 text-2xl font-bold tabular-nums text-[var(--k-text)]">
            {{ dashboard.streakDays }}
            <span class="text-sm font-normal text-[var(--k-text-faint)]">天</span>
          </p>
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

        <div v-else-if="points" class="space-y-2">
          <article
            v-for="point in points.weakest"
            :key="point.pointId"
            class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3"
          >
            <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
              <div class="flex min-w-0 items-center gap-2">
                <span
                  class="h-2 w-2 shrink-0 rounded-full"
                  :class="subjectMeta[point.subject]?.bar ?? 'bg-[var(--k-text-faint)]'"
                />
                <RouterLink
                  :to="`/learn/${point.pointId}`"
                  class="truncate text-sm font-semibold text-[var(--k-text)] no-underline hover:text-[var(--k-accent-text)]"
                >
                  {{ point.title }}
                </RouterLink>
                <span class="shrink-0 text-[11px] text-[var(--k-text-faint)]">
                  {{ subjectMeta[point.subject]?.name ?? point.subject }}
                </span>
              </div>
              <div class="flex shrink-0 items-center gap-4 text-xs tabular-nums">
                <span class="text-[var(--k-text-faint)]">
                  {{ point.done }} / {{ point.total }} 题
                </span>
                <span class="font-bold" :class="accuracyClass(point.accuracy)">
                  {{ point.accuracy === null ? '—' : point.accuracy + '%' }}
                </span>
                <RouterLink
                  :to="practicePoint(point)"
                  class="rounded-lg border border-[var(--k-border)] px-2.5 py-1 no-underline text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
                >
                  去练
                </RouterLink>
              </div>
            </div>
            <div class="mt-2.5 h-1.5 overflow-hidden rounded-full bg-[var(--k-track)]">
              <div
                class="h-full rounded-full transition-all duration-500"
                :class="accuracyBarClass(point.accuracy)"
                :style="{ width: Math.max(point.accuracy ?? 0, 2) + '%' }"
              />
            </div>
          </article>
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
                      :class="subjectMeta[item.subject]?.bar ?? 'bg-[var(--k-text-faint)]'"
                    />
                    <span class="font-medium text-[var(--k-text)]">
                      {{ subjectMeta[item.subject]?.name ?? item.subject }}
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
                  :class="subjectMeta[point.subject]?.bar ?? 'bg-[var(--k-accent)]'"
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