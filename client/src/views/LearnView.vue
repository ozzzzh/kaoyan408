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

    <div class="grid gap-5 lg:grid-cols-[260px_minmax(0,1fr)]">
      <!-- 左侧：知识点清单 -->
      <nav
        class="max-h-[70vh] overflow-y-auto rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] p-2 lg:sticky lg:top-4"
      >
        <p v-if="!points.length" class="px-3 py-4 text-xs text-[var(--k-text-faint)]">
          该科目暂无知识点
        </p>
        <RouterLink
          v-for="point in points"
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
          <p class="mb-6 font-mono text-[11px] text-[var(--k-text-faint)]">{{ currentPointId }}</p>

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