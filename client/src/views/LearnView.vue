<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { KnowledgePointView } from '@/api/types'
import KnowledgeBlocks from '@/components/knowledge/KnowledgeBlocks.vue'
import { toArticle } from '@/components/knowledge/types'

const route = useRoute()
const router = useRouter()

const points = ref<KnowledgePointView[]>([])
const article = ref<ReturnType<typeof toArticle> | null>(null)
const pointTitle = ref<string>('')
const questionCount = ref(0)
const loading = ref(true)
const error = ref<string | null>(null)

const subjects = [
  { code: '', label: '全部' },
  { code: 'ds', label: '数据结构' },
  { code: 'os', label: '操作系统' },
  { code: 'co', label: '组成原理' },
  { code: 'cn', label: '计算机网络' },
]

const currentPointId = computed(() => String(route.params.pointId ?? ''))

/**
 * 侧栏就地筛选。单个科目最多 40+ 个知识点，靠滚动找太慢；
 * 只匹配标题（正文检索由全局搜索承担，这里要的是快速定位）。
 */
const filter = ref('')
const filteredPoints = computed(() => {
  const keyword = filter.value.trim().toLowerCase()
  if (!keyword) return points.value
  return points.value.filter((point) => point.title.toLowerCase().includes(keyword))
})
const currentSubject = computed(() =>
  typeof route.query.subject === 'string' ? route.query.subject : '',
)

/** 当前知识点在同科目列表中的位置，用于上一节 / 下一节。 */
const currentIndex = computed(() =>
  points.value.findIndex((point) => point.pointId === currentPointId.value),
)
const prevPoint = computed(() =>
  currentIndex.value > 0 ? points.value[currentIndex.value - 1] : null,
)
const nextPoint = computed(() =>
  currentIndex.value >= 0 && currentIndex.value < points.value.length - 1
    ? points.value[currentIndex.value + 1]
    : null,
)

/** 目录：按 subpoint 分节。 */
const toc = computed(() =>
  (article.value?.subpoints ?? []).map((subpoint, index) => ({
    id: subpoint.id,
    index: index + 1,
    title: subpoint.title,
  })),
)

async function loadPoints(forSubject: string) {
  try {
    points.value = await api.knowledgePoints(forSubject || undefined)
  } catch {
    points.value = []
  }
}

async function loadArticle(pointId: string) {
  if (!pointId) {
    article.value = null
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    const response = await api.knowledgePoint(pointId)
    article.value = toArticle(response)
    pointTitle.value = response.title ?? pointId
    questionCount.value = response.questionCount ?? 0
  } catch (e) {
    article.value = null
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 无 pointId 时自动跳到第一个知识点，避免空白页。 */
async function bootstrap() {
  await loadPoints(currentSubject.value)
  if (currentPointId.value) {
    await loadArticle(currentPointId.value)
  } else if (points.value.length) {
    router.replace({ path: `/learn/${points.value[0].pointId}` })
  } else {
    loading.value = false
  }
}

function selectSubject(code: string) {
  router.replace({ path: '/learn', query: code ? { subject: code } : {} })
}

function scrollToBlock(blockId: string) {
  document.getElementById(blockId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(bootstrap)

watch(
  () => [route.params.pointId, route.query.subject],
  ([pointId, forSubject], [oldPointId, oldSubject]) => {
    if (forSubject !== oldSubject) {
      // 切换科目时清空筛选，否则会带着上一个科目的关键词、看起来像「没有知识点」
      filter.value = ''
      void loadPoints(typeof forSubject === 'string' ? forSubject : '')
    }
    if (pointId !== oldPointId && typeof pointId === 'string') {
      void loadArticle(pointId)
    }
  },
)
</script>

<template>
  <div class="space-y-5">
    <header class="space-y-3">
      <div class="flex flex-wrap items-baseline justify-between gap-3">
        <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">知识点</h1>
        <span class="text-xs text-[var(--k-text-faint)] tabular-nums">
          共 {{ points.length }} 个知识点
        </span>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <button
          v-for="item in subjects"
          :key="item.code"
          class="rounded-lg border px-3 py-1.5 text-sm transition"
          :class="
            currentSubject === item.code
              ? 'border-[var(--k-accent)] bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
              : 'border-[var(--k-border)] bg-[var(--k-surface)] text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)]'
          "
          @click="selectSubject(item.code)"
        >
          {{ item.label }}
        </button>
      </div>
    </header>

    <div class="grid items-start gap-5 lg:grid-cols-[260px_minmax(0,1fr)]">
      <!--
        左侧：知识点清单。
        高度同时锁上下限：max-h 用 calc(100vh-2rem) 而不是 70vh
        （70vh 在长屏上只占七成高度，滚动到底后左下方会露出一大块空白）；
        再加 min-h，避免筛选后条目变少、面板跟着缩下去又出现空白。
        配合 sticky，侧栏始终占满可视区。
      -->
      <nav
        class="flex max-h-[calc(100vh-2rem)] min-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] lg:sticky lg:top-4"
      >
        <!-- 科目内搜索：147 项靠滚动找太慢，加一个就地过滤 -->
        <div class="shrink-0 border-b border-[var(--k-border)] p-2">
          <div class="flex items-center gap-2 rounded-lg bg-[var(--k-surface-soft)] px-2.5 py-1.5">
            <svg
              class="h-3.5 w-3.5 shrink-0 text-[var(--k-text-faint)]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.5-3.5" />
            </svg>
            <input
              v-model="filter"
              class="w-full border-0 bg-transparent text-xs text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
              placeholder="筛选知识点"
            />
            <button
              v-if="filter"
              class="shrink-0 text-[10px] text-[var(--k-text-faint)] hover:text-[var(--k-text)]"
              @click="filter = ''"
            >
              清除
            </button>
          </div>
          <p class="mt-1.5 px-1 text-[10px] text-[var(--k-text-faint)]">
            {{ filteredPoints.length }} / {{ points.length }} 个知识点
          </p>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-2">
          <p v-if="!points.length" class="px-3 py-4 text-xs text-[var(--k-text-faint)]">
            该科目暂无知识点
          </p>
          <p
            v-else-if="!filteredPoints.length"
            class="px-3 py-4 text-xs text-[var(--k-text-faint)]"
          >
            没有匹配「{{ filter }}」的知识点
          </p>
          <RouterLink
            v-for="point in filteredPoints"
            :key="point.pointId"
            :to="`/learn/${point.pointId}`"
            class="mb-0.5 block rounded-lg px-3 py-2 no-underline transition"
            :class="
              point.pointId === currentPointId
                ? 'bg-[var(--k-accent-soft)] text-[var(--k-accent-text)]'
                : 'text-[var(--k-text-muted)] hover:bg-[var(--k-surface-soft)] hover:text-[var(--k-text)]'
            "
          >
            <span class="block text-[13px] font-semibold leading-snug">{{ point.title }}</span>
            <span class="block text-[11px] text-[var(--k-text-faint)]">{{ point.blockCount }} 块</span>
          </RouterLink>
        </div>
      </nav>

      <!-- 右侧：正文 -->
      <div class="min-w-0 space-y-4">
        <div
          v-if="article && toc.length > 1"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3"
        >
          <p class="mb-2 text-[11px] font-semibold text-[var(--k-text-faint)]">本节内容</p>
          <div class="flex flex-wrap gap-x-4 gap-y-1.5">
            <button
              v-for="section in toc"
              :key="section.id"
              class="text-[12px] text-[var(--k-text-muted)] transition hover:text-[var(--k-accent-text)]"
              @click="scrollToBlock(section.id)"
            >
              {{ String(section.index).padStart(2, '0') }} {{ section.title }}
            </button>
          </div>
        </div>

        <p v-if="loading" class="py-10 text-center text-sm text-[var(--k-text-faint)]">加载中…</p>
        <p
          v-else-if="error"
          class="rounded-xl border border-[var(--k-warn-border)] bg-[var(--k-warn-soft)] px-4 py-3 text-sm text-[var(--k-warn)]"
        >
          {{ error }}
        </p>

        <article
          v-else-if="article"
          class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-6 py-7 md:px-9 md:py-9"
        >
          <h1 class="mb-1 text-[1.7rem] font-bold leading-tight text-[var(--k-text)]">
            {{ pointTitle }}
          </h1>
          <div class="mb-6 flex flex-wrap items-center gap-3">
            <p class="m-0 font-mono text-[11px] text-[var(--k-text-faint)]">{{ currentPointId }}</p>
            <RouterLink
              v-if="questionCount > 0"
              :to="{ path: '/practice', query: { pointId: currentPointId } }"
              class="group inline-flex items-center gap-1 rounded-full border border-[var(--k-border)] px-3 py-1 text-[11px] font-semibold text-[var(--k-accent-text)] no-underline transition hover:border-[var(--k-accent)] hover:bg-[var(--k-accent-soft)]"
            >
              本节关联 {{ questionCount }} 道真题
              <span class="transition-transform group-hover:translate-x-0.5">→</span>
            </RouterLink>

            <!-- 带着当前知识点进入 AI，让助手直接围绕它答疑 -->
            <RouterLink
              :to="{ path: '/ai', query: { context: `kp:${currentPointId}` } }"
              class="group inline-flex items-center gap-1 rounded-full border border-[var(--k-border)] px-3 py-1 text-[11px] font-semibold text-[var(--k-accent-text)] no-underline transition hover:border-[var(--k-accent)] hover:bg-[var(--k-accent-soft)]"
            >
              问问 AI
              <span class="transition-transform group-hover:translate-x-0.5">→</span>
            </RouterLink>
          </div>

          <KnowledgeBlocks :article="article" />
        </article>

        <div v-if="prevPoint || nextPoint" class="flex items-stretch gap-3">
          <RouterLink
            v-if="prevPoint"
            :to="`/learn/${prevPoint.pointId}`"
            class="flex flex-1 items-center gap-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
          >
            <span class="text-[var(--k-text-faint)]">←</span>
            <span class="min-w-0">
              <span class="block text-[11px] text-[var(--k-text-faint)]">上一节</span>
              <span class="block truncate text-[13px] font-semibold text-[var(--k-text)]">
                {{ prevPoint.title }}
              </span>
            </span>
          </RouterLink>
          <RouterLink
            v-if="nextPoint"
            :to="`/learn/${nextPoint.pointId}`"
            class="flex flex-1 items-center justify-end gap-3 rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 text-right no-underline transition hover:border-[var(--k-accent)]"
          >
            <span class="min-w-0">
              <span class="block text-[11px] text-[var(--k-text-faint)]">下一节</span>
              <span class="block truncate text-[13px] font-semibold text-[var(--k-text)]">
                {{ nextPoint.title }}
              </span>
            </span>
            <span class="text-[var(--k-text-faint)]">→</span>
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>