<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type { KnowledgePointView, SearchResult } from '@/api/types'
import { clearRecentPoints, recentPoints } from '@/utils/recentPoints'
import type { RecentPoint } from '@/utils/recentPoints'

const route = useRoute()
const router = useRouter()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const result = ref<SearchResult | null>(null)
const loading = ref(false)
const searched = ref(false)
/** 当前选中的分组，用于 tab 切换；'' 表示全部 */
const tab = ref<'' | 'point' | 'question' | 'block'>('')

const tabs = computed(() => [
  { key: '' as const, label: '全部', count: result.value?.total ?? 0 },
  { key: 'point' as const, label: '知识点', count: result.value?.points.length ?? 0 },
  { key: 'question' as const, label: '题目', count: result.value?.questions.length ?? 0 },
  { key: 'block' as const, label: '正文片段', count: result.value?.blocks.length ?? 0 },
])

const showPoints = computed(() => tab.value === '' || tab.value === 'point')
const showQuestions = computed(() => tab.value === '' || tab.value === 'question')
const showBlocks = computed(() => tab.value === '' || tab.value === 'block')

const subjectName = (code: string) =>
  ({ ds: '数据结构', os: '操作系统', co: '计算机组成原理', cn: '计算机网络' })[code] ?? code

/** 把关键词在文本中高亮：先转义 HTML，再包 mark，避免 XSS。 */
function highlight(text: string | null | undefined): string {
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
  const k = keyword.value.trim()
  if (!k) return escaped
  const pattern = new RegExp(`(${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return escaped.replace(pattern, '<mark class="k-hit">$1</mark>')
}

/**
 * 已经执行过搜索的关键词。
 *
 * 不能用 `q !== keyword.value` 做去重判断：输入框是 v-model 双向绑定，
 * 用户点「搜索」时 keyword 已经等于要搜的词，那个判断恒为 false，
 * 于是 runSearch 根本不会被调用——URL 变了、结果却没出来，只能刷新页面
 * （刷新走 onMounted 那条路径）才看到结果。这里单独记录「上次实际搜过什么」。
 */
const lastQuery = ref('')

async function runSearch(q: string) {
  const trimmed = q.trim()
  if (!trimmed) {
    result.value = null
    searched.value = false
    lastQuery.value = ''
    return
  }
  // 同一个词已经搜过就不重复请求（例如输入框失焦、路由回退等触发的重复调用）
  if (trimmed === lastQuery.value && result.value) {
    return
  }
  loading.value = true
  searched.value = true
  try {
    result.value = await api.search(trimmed)
    lastQuery.value = trimmed
  } catch {
    result.value = null
    lastQuery.value = ''
  } finally {
    loading.value = false
  }
}

/** 提交：立刻发起搜索，不等 watch 回调（避免依赖路由变化的时序）。 */
function submit() {
  const q = keyword.value.trim()
  if (!q) return
  // 关键词没变时也要重新搜——用户可能就是想「再搜一次」
  if (q === lastQuery.value) {
    void runSearch(q)
    return
  }
  router.replace({ path: '/search', query: { q } })
  void runSearch(q)
}

/**
 * 示例词：空状态时给用户一个起点。
 * 分成「考点」「题号」两组——题号定位是隐藏能力，混在一堆普通词里没人会注意到。
 */
const exampleGroups = [
  { label: '考点', words: ['死锁', '银行家算法', 'KMP', '子网掩码'] },
  { label: '题号', words: ['2026 3'] },
]

/** 点示例词：填入并直接搜索，不经过路由（少一次时序依赖）。 */
function pickExample(word: string) {
  keyword.value = word
  submit()
}

/**
 * 「怎么搜」三张卡：把占位符里挤成一行的能力拆开讲。
 * 题号定位（「2026 3」= 2026 年第 3 题）和正文命中都是没人会自己发现的功能。
 */
const searchTips = [
  {
    title: '搜知识点',
    desc: '按标题找章节，命中后直接进正文。',
    example: '死锁',
  },
  {
    title: '搜题目（支持题号）',
    desc: '输入「2026 3」按年份+题号直接定位。',
    example: '2026 3',
  },
  {
    title: '搜正文片段',
    desc: '关键词出现在段落里也能找到，附上下文。',
    example: '缓冲区',
  },
]

/* ---------------- 空状态：用真实内容填满，而不是留一大片背景色 ---------------- */

const searchInput = ref<HTMLInputElement | null>(null)

/** 四科元信息。科目顺序与「知识点」页保持一致，避免两页认知不一致。 */
const subjectCards = [
  { code: 'ds', label: '数据结构' },
  { code: 'os', label: '操作系统' },
  { code: 'co', label: '组成原理' },
  { code: 'cn', label: '计算机网络' },
]

/**
 * 全量知识点目录（147 条，响应很小）。
 * 只在空状态需要展示时拉一次，搜索路径不受影响。
 */
const allPoints = ref<KnowledgePointView[]>([])
const catalogLoading = ref(false)

const catalog = computed(() =>
  subjectCards.map((subject) => {
    const points = allPoints.value.filter((point) => point.subject === subject.code)
    return { ...subject, total: points.length, preview: points.slice(0, 3) }
  }),
)

async function loadCatalog() {
  if (catalogLoading.value || allPoints.value.length) return
  catalogLoading.value = true
  try {
    allPoints.value = await api.knowledgePoints()
  } catch {
    allPoints.value = []
  } finally {
    catalogLoading.value = false
  }
}

/** 最近浏览（来自 LearnView 写入的 localStorage）。 */
const recent = ref<RecentPoint[]>([])

function refreshRecent() {
  recent.value = recentPoints(4)
}

function forgetRecent() {
  clearRecentPoints()
  recent.value = []
}

/** 相对时间：这里只需要「是不是刚看过」的粗粒度感知，不引日期库。 */
function timeAgo(at: number): string {
  const minutes = Math.floor((Date.now() - at) / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

/** Ctrl/Cmd + K 聚焦搜索框：全站最常用的入口，省一次鼠标点击。 */
function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    searchInput.value?.focus()
    searchInput.value?.select()
  }
}

onMounted(() => {
  runSearch(keyword.value)
  if (!keyword.value.trim()) {
    refreshRecent()
    void loadCatalog()
  }
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})

/** 清空搜索后要回到空状态，此时最近浏览和目录可能还是空的，补一次。 */
watch(searched, (value) => {
  if (!value) {
    refreshRecent()
    void loadCatalog()
  }
})

watch(
  () => route.query.q,
  (next) => {
    const q = typeof next === 'string' ? next : ''
    // 只同步输入框；搜索由 submit() 或 onMounted 负责。
    // 这里再调一次 runSearch 会在「点搜索」时产生重复请求。
    keyword.value = q
    if (!q.trim()) {
      // 关键词被清掉（点侧栏「搜索」、地址栏退回去）：重置成空状态，
      // 否则上一次的结果会一直挂在那里，空状态永远出不来。
      void runSearch('')
      return
    }
    if (q !== lastQuery.value) {
      void runSearch(q)
    }
  },
)
</script>

<template>
  <div class="space-y-5">
    <header class="space-y-3">
      <h1 class="text-2xl font-bold tracking-tight text-[var(--k-text)]">搜索</h1>

      <div
        class="flex items-center gap-2 rounded-xl border border-[var(--k-border-strong)] bg-[var(--k-surface)] px-4 py-2.5"
      >
        <svg
          class="h-4 w-4 shrink-0 text-[var(--k-text-faint)]"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
        <input
          ref="searchInput"
          v-model="keyword"
          class="w-full border-0 bg-transparent text-[15px] text-[var(--k-text)] outline-0 placeholder:text-[var(--k-text-faint)]"
          placeholder="搜知识点、题目、正文；也可直接输「2026 3」定位题号"
          @keydown.enter="submit"
        />
        <kbd
          class="hidden shrink-0 rounded border border-[var(--k-border)] px-1.5 py-0.5 font-sans text-[10px] text-[var(--k-text-faint)] sm:block"
        >
          Ctrl K
        </kbd>
        <button
          class="shrink-0 rounded-lg bg-[var(--k-accent)] px-4 py-1.5 text-sm font-semibold text-[var(--k-on-accent)] transition hover:bg-[var(--k-accent-hover)]"
          @click="submit"
        >
          搜索
        </button>
      </div>

      <!-- 示例：按「考点 / 题号」分组，让隐藏的题号定位能力露出来 -->
      <div v-if="!searched" class="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs">
        <div
          v-for="group in exampleGroups"
          :key="group.label"
          class="flex flex-wrap items-center gap-2"
        >
          <span class="text-[var(--k-text-faint)]">{{ group.label }}</span>
          <button
            v-for="word in group.words"
            :key="word"
            class="rounded-full border border-[var(--k-border)] px-3 py-1 text-[var(--k-text-muted)] transition hover:border-[var(--k-accent)] hover:text-[var(--k-accent-text)]"
            @click="pickExample(word)"
          >
            {{ word }}
          </button>
        </div>
      </div>
    </header>

    <!--
      空状态：没搜过、也没有关键词时显示。
      原来这里只有一条分隔线加 footer，整页 800px 是纯背景色，看起来像没加载出来。
      现在用三块真实内容填满：最近浏览（本机记忆）、按科目浏览（147 个知识点）、怎么搜。
    -->
    <div v-if="!searched && !loading && !result" class="space-y-4">
      <!-- 继续学习 -->
      <section
        v-if="recent.length"
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-4"
      >
        <div class="mb-3 flex items-baseline justify-between gap-3">
          <h2 class="text-sm font-bold text-[var(--k-text)]">继续学习</h2>
          <button
            class="text-[11px] text-[var(--k-text-faint)] transition hover:text-[var(--k-text)]"
            @click="forgetRecent"
          >
            清除记录
          </button>
        </div>
        <div class="grid gap-2 sm:grid-cols-2">
          <RouterLink
            v-for="item in recent"
            :key="item.pointId"
            :to="{
              path: `/learn/${item.pointId}`,
              query: item.subject ? { subject: item.subject } : {},
            }"
            class="group rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-3 py-2.5 no-underline transition hover:border-[var(--k-accent)]"
          >
            <div class="flex items-baseline justify-between gap-2">
              <span class="truncate text-[13px] font-semibold text-[var(--k-text)]">
                {{ item.title }}
              </span>
              <span class="shrink-0 text-[10px] text-[var(--k-text-faint)]">
                {{ timeAgo(item.at) }}
              </span>
            </div>
            <span class="mt-0.5 block text-[11px] text-[var(--k-text-faint)]">
              {{ subjectName(item.subject) }}
            </span>
          </RouterLink>
        </div>
      </section>

      <!-- 按科目浏览 -->
      <section
        v-if="catalog.some((item) => item.total)"
        class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-4"
      >
        <div class="mb-3 flex items-baseline justify-between gap-3">
          <h2 class="text-sm font-bold text-[var(--k-text)]">按科目浏览</h2>
          <span class="text-[11px] text-[var(--k-text-faint)]">
            共 {{ allPoints.length }} 个知识点
          </span>
        </div>
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div
            v-for="subject in catalog"
            :key="subject.code"
            class="rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] p-3"
          >
            <RouterLink
              :to="{ path: '/learn', query: { subject: subject.code } }"
              class="flex items-baseline justify-between gap-2 no-underline"
            >
              <span class="text-[13px] font-semibold text-[var(--k-text)]">
                {{ subject.label }}
              </span>
              <span class="text-[11px] tabular-nums text-[var(--k-text-faint)]">
                {{ subject.total }}
              </span>
            </RouterLink>
            <ul class="mt-1.5 space-y-0.5">
              <li v-for="point in subject.preview" :key="point.pointId">
                <RouterLink
                  :to="{ path: `/learn/${point.pointId}`, query: { subject: subject.code } }"
                  class="block truncate text-[11px] text-[var(--k-text-muted)] no-underline transition hover:text-[var(--k-accent-text)]"
                >
                  {{ point.title }}
                </RouterLink>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 怎么搜 -->
      <section class="rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-4">
        <h2 class="mb-3 text-sm font-bold text-[var(--k-text)]">怎么搜</h2>
        <div class="grid gap-3 md:grid-cols-3">
          <button
            v-for="tip in searchTips"
            :key="tip.title"
            class="rounded-lg border border-[var(--k-border)] bg-[var(--k-surface-soft)] px-3 py-2.5 text-left transition hover:border-[var(--k-accent)]"
            @click="pickExample(tip.example)"
          >
            <span class="block text-[12px] font-semibold text-[var(--k-text)]">
              {{ tip.title }}
            </span>
            <span class="mt-1 block text-[11px] leading-relaxed text-[var(--k-text-faint)]">
              {{ tip.desc }}
            </span>
            <span class="mt-1.5 inline-block text-[11px] text-[var(--k-accent-text)]">
              试试「{{ tip.example }}」
            </span>
          </button>
        </div>
      </section>
    </div>

    <p v-if="loading" class="py-10 text-center text-sm text-[var(--k-text-faint)]">搜索中…</p>

    <template v-else-if="result">
      <!-- 结果统计 + 分组切换 -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-[var(--k-text-muted)]">
          「<span class="font-semibold text-[var(--k-text)]">{{ result.query }}</span
          >」共 {{ result.total }} 条结果
        </p>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="item in tabs"
            :key="item.key"
            class="rounded-lg px-3 py-1 text-xs transition"
            :class="
              tab === item.key
                ? 'bg-[var(--k-accent-soft)] font-semibold text-[var(--k-accent-text)]'
                : 'text-[var(--k-text-faint)] hover:bg-[var(--k-surface-soft)]'
            "
            @click="tab = item.key"
          >
            {{ item.label }} {{ item.count }}
          </button>
        </div>
      </div>

      <p
        v-if="result.total === 0"
        class="rounded-xl border border-dashed border-[var(--k-border-strong)] bg-[var(--k-surface)] px-6 py-12 text-center text-sm text-[var(--k-text-muted)]"
      >
        没有找到匹配的内容。试试更短的关键词，或用「2026 3」这类题号查询。
      </p>

      <div v-else class="space-y-6">
        <!-- 知识点 -->
        <section v-if="showPoints && result.points.length">
          <h2 class="mb-2 text-xs font-bold text-[var(--k-text-faint)]">
            知识点 · {{ result.points.length }}
          </h2>
          <div class="space-y-2">
            <RouterLink
              v-for="hit in result.points"
              :key="hit.pointId"
              :to="`/learn/${hit.pointId}`"
              class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
            >
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  class="text-sm font-semibold text-[var(--k-text)]"
                  v-html="highlight(hit.title)"
                ></span>
                <span class="text-[11px] text-[var(--k-text-faint)]">
                  {{ subjectName(hit.subject) }} · {{ hit.blockCount }} 块
                </span>
                <span
                  v-if="hit.bodyHits > 0"
                  class="rounded bg-[var(--k-chip)] px-1.5 py-0.5 text-[10px] text-[var(--k-text-faint)]"
                >
                  正文命中 {{ hit.bodyHits }} 处
                </span>
              </div>
            </RouterLink>
          </div>
        </section>

        <!-- 题目 -->
        <section v-if="showQuestions && result.questions.length">
          <h2 class="mb-2 text-xs font-bold text-[var(--k-text-faint)]">
            题目 · {{ result.questions.length }}
          </h2>
          <div class="space-y-2">
            <RouterLink
              v-for="hit in result.questions"
              :key="hit.examRef"
              :to="{ path: '/practice', query: { examRef: hit.examRef } }"
              class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
            >
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  class="text-sm font-semibold text-[var(--k-text)]"
                  v-html="highlight(hit.title)"
                ></span>
                <span class="text-[11px] text-[var(--k-text-faint)]">
                  {{ hit.hint }} · {{ hit.score }} 分
                </span>
              </div>
              <p
                v-if="hit.snippet"
                class="mt-1 text-xs leading-relaxed text-[var(--k-text-muted)]"
                v-html="highlight(hit.snippet)"
              ></p>
            </RouterLink>
          </div>
        </section>

        <!-- 正文片段 -->
        <section v-if="showBlocks && result.blocks.length">
          <h2 class="mb-2 text-xs font-bold text-[var(--k-text-faint)]">
            正文片段 · {{ result.blocks.length }}
          </h2>
          <div class="space-y-2">
            <RouterLink
              v-for="hit in result.blocks"
              :key="hit.blockId"
              :to="`/learn/${hit.pointId}`"
              class="block rounded-xl border border-[var(--k-border)] bg-[var(--k-surface)] px-4 py-3 no-underline transition hover:border-[var(--k-accent)]"
            >
              <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span
                  class="text-[13px] font-semibold text-[var(--k-text)]"
                  v-html="highlight(hit.pointTitle)"
                ></span>
                <span v-if="hit.subpointTitle" class="text-[11px] text-[var(--k-text-faint)]">
                  {{ hit.subpointTitle }}
                </span>
              </div>
              <p
                class="mt-1 text-xs leading-relaxed text-[var(--k-text-muted)]"
                v-html="highlight(hit.snippet)"
              ></p>
            </RouterLink>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
:deep(.k-hit) {
  border-radius: 2px;
  background: color-mix(in srgb, var(--k-accent) 22%, transparent);
  color: var(--k-text);
  padding: 0 2px;
  font-weight: 600;
}
</style>